package org.example.reservationservice.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.reservationservice.common.exception.ApiException;
import org.example.reservationservice.infrastructure.festival.FestivalServiceClient;
import org.example.reservationservice.infrastructure.festival.dto.FestivalDetailResponseDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private static final String PUBLISHED = "PUBLISHED";
    private static final Duration RESERVATION_HOLD_DURATION = Duration.ofMinutes(10);
    //취소되지 않은 것으로 보고 구매 제한에 합산할 상태들 (만료·취소 건은 다시 살 수 있어야 하므로 제외)
    private static final List<ReservationStatus> HELD_STATUSES = List.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED);

    private final ReservationRepository reservationRepository;
    private final FestivalServiceClient festivalServiceClient;

    //사이트 전체 기본 1인당 구매 제한(계정 기준, 티켓 종류당). 주최자가 티켓 종류별로 더 낮게 설정하는 기능은 아직 없다(festival-service TicketType에 필드 추가 필요 — 후속 작업).
    @Value("${reservation.max-quantity-per-ticket-type:4}")
    private int maxQuantityPerTicketType;

    //참가자가 티켓 예매를 신청한다: 페스티벌·티켓종류 검증 → 구매 제한 검증 → 재고 차감(festival-service) → 예매 저장
    @Transactional
    public ReservationResponseDto createReservation(Long userId, ReservationCreateRequestDto request) {
        FestivalDetailResponseDto festival = getFestivalOrThrow(request.festivalId());
        if (!PUBLISHED.equals(festival.festivalStatus())) {
            throw new ApiException(ReservationErrorCode.FESTIVAL_NOT_PUBLISHED);
        }
        FestivalDetailResponseDto.TicketTypeSummary ticketType = festival.ticketTypes().stream()
                .filter(t -> t.id().equals(request.ticketTypeId()))
                .findFirst()
                .orElseThrow(() -> new ApiException(ReservationErrorCode.TICKET_TYPE_NOT_FOUND));

        checkPurchaseLimitOrThrow(userId, request.ticketTypeId(), request.quantity());
        deductStockOrThrow(request.ticketTypeId(), request.quantity());

        Reservation reservation = Reservation.builder()
                .userId(userId)
                .ticketTypeId(request.ticketTypeId())
                .quantity(request.quantity())
                .price(ticketType.price())
                .reservationStatus(ReservationStatus.PENDING)
                .expiresAt(Instant.now().plus(RESERVATION_HOLD_DURATION))
                .build();

        try {
            reservationRepository.save(reservation);
        } catch (RuntimeException e) {
            //재고는 이미 차감됐는데 예매 저장이 실패하면 재고가 영구 유실되므로 반드시 복구한다.
            festivalServiceClient.restoreStock(request.ticketTypeId(), request.quantity());
            throw e;
        }

        return ReservationResponseDto.from(reservation);
    }

    //참가자 본인의 예매 목록을 조회한다
    public List<ReservationResponseDto> listMyReservations(Long userId) {
        return reservationRepository.findByUserId(userId).stream()
                .map(ReservationResponseDto::from)
                .toList();
    }

    //참가자 본인의 예매 상세를 조회한다
    public ReservationResponseDto getMyReservationDetail(Long id, Long userId) {
        return ReservationResponseDto.from(getOwnedReservation(id, userId));
    }

    //Payment-Service → Reservation-Service 내부 호출: 결제 시작 전 예매 정보 조회
    public ReservationForPaymentResponseDto getReservationForPayment(Long id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ApiException(ReservationErrorCode.RESERVATION_NOT_FOUND));
        return ReservationForPaymentResponseDto.from(reservation);
    }

    //Payment-Service → Reservation-Service 내부 호출: 결제 성공 확정
    @Transactional
    public void confirmReservation(Long id, ReservationConfirmRequestDto request) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ApiException(ReservationErrorCode.RESERVATION_NOT_FOUND));

        if (reservation.getReservationStatus() == ReservationStatus.CONFIRMED) {
            //같은 paymentId로 재호출되면 멱등하게 성공 처리, 다른 paymentId면 충돌
            if (reservation.getPaymentId().equals(request.paymentId())) {
                return;
            }
            throw new ApiException(ReservationErrorCode.RESERVATION_ALREADY_CONFIRMED);
        }
        if (reservation.getReservationStatus() == ReservationStatus.CANCELLED) {
            if (reservation.getCancelReason() == CancelReason.EXPIRED) {
                throw new ApiException(ReservationErrorCode.RESERVATION_ALREADY_EXPIRED);
            }
            throw new ApiException(ReservationErrorCode.RESERVATION_NOT_CANCELLABLE);
        }
        if (reservation.totalAmount() != request.amount()) {
            throw new ApiException(ReservationErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        reservation.confirm(request.paymentId());
    }

    //Payment-Service → Reservation-Service 내부 호출: 결제 실패·취소·만료 시 예매 취소 + 재고 복구
    @Transactional
    public void cancelReservation(Long id, ReservationCancelRequestDto request) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ApiException(ReservationErrorCode.RESERVATION_NOT_FOUND));

        if (reservation.getReservationStatus() == ReservationStatus.CANCELLED) {
            //이미 취소 처리된 건에 대한 재호출은 재고를 다시 복구하지 않고 멱등하게 무시한다.
            return;
        }
        if (reservation.getReservationStatus() != ReservationStatus.PENDING) {
            throw new ApiException(ReservationErrorCode.RESERVATION_NOT_CANCELLABLE);
        }

        reservation.cancel(request.reasonCode());
        festivalServiceClient.restoreStock(reservation.getTicketTypeId(), reservation.getQuantity());
    }

    //Festival 불러오기(내부 메서드)
    private FestivalDetailResponseDto getFestivalOrThrow(Long festivalId) {
        try {
            return festivalServiceClient.getFestival(festivalId);
        } catch (HttpClientErrorException.NotFound e) {
            //방문자용 API는 미승인·존재하지 않음을 구분해 알려주지 않으므로 여기서도 구분하지 않는다.
            throw new ApiException(ReservationErrorCode.FESTIVAL_NOT_PUBLISHED);
        } catch (RestClientException e) {
            throw new ApiException(ReservationErrorCode.FESTIVAL_SERVICE_UNAVAILABLE);
        }
    }

    //1인당 구매 제한 검증(내부 메서드) — 계정 기준, PENDING·CONFIRMED로 이미 들고 있는 수량 + 이번 요청 수량이 한도를 넘으면 거부
    private void checkPurchaseLimitOrThrow(Long userId, Long ticketTypeId, int quantity) {
        int alreadyHeld = reservationRepository
                .findByUserIdAndTicketTypeIdAndReservationStatusIn(userId, ticketTypeId, HELD_STATUSES).stream()
                .mapToInt(Reservation::getQuantity)
                .sum();
        if (alreadyHeld + quantity > maxQuantityPerTicketType) {
            throw new ApiException(ReservationErrorCode.PURCHASE_LIMIT_EXCEEDED);
        }
    }

    //재고 차감(내부 메서드)
    private void deductStockOrThrow(Long ticketTypeId, int quantity) {
        try {
            festivalServiceClient.deductStock(ticketTypeId, quantity);
        } catch (HttpClientErrorException.Conflict e) {
            throw new ApiException(ReservationErrorCode.STOCK_EXCEEDED);
        } catch (RestClientException e) {
            throw new ApiException(ReservationErrorCode.FESTIVAL_SERVICE_UNAVAILABLE);
        }
    }

    //본인 소유 예매 불러오기(내부 메서드)
    private Reservation getOwnedReservation(Long id, Long userId) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ApiException(ReservationErrorCode.RESERVATION_NOT_FOUND));
        if (!reservation.getUserId().equals(userId)) {
            throw new ApiException(ReservationErrorCode.FORBIDDEN_NOT_OWNER);
        }
        return reservation;
    }
}
