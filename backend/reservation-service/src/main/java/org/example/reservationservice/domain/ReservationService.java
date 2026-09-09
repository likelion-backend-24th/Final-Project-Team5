package org.example.reservationservice.domain;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.reservationservice.common.exception.ApiException;
import org.example.reservationservice.domain.refund.RefundPolicy;
import org.example.reservationservice.domain.refund.RefundQuote;
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
    private static final String HELPER_ROLE = "HELPER";
    private static final Duration RESERVATION_HOLD_DURATION = Duration.ofMinutes(10);
    private static final int MAX_CHECK_IN_CODE_ATTEMPTS = 5;
    //취소되지 않은 것으로 보고 구매 제한에 합산할 상태들 (만료·취소 건은 다시 살 수 있어야 하므로 제외)
    private static final List<ReservationStatus> HELD_STATUSES = List.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED);

    //입장 검증·현황 집계에 포함할 상태들. 부분 환불된 예매도 남은 장수만큼은 입장할 수 있어야 한다.
    private static final List<ReservationStatus> ADMITTABLE_STATUSES =
            List.of(ReservationStatus.CONFIRMED, ReservationStatus.PARTIALLY_REFUNDED);

    private final ReservationRepository reservationRepository;
    private final FestivalServiceClient festivalServiceClient;
    private final CheckInCodeGenerator checkInCodeGenerator;
    private final RefundPolicy refundPolicy;

    //사이트 전체 기본 1인당 구매 제한(계정 기준, 티켓 종류당). 주최자가 티켓 종류별로 더 낮게 설정하는 기능은 아직 없다(festival-service TicketType에 필드 추가 필요 — 후속 작업).
    @Value("${reservation.max-quantity-per-ticket-type:4}")
    private int maxQuantityPerTicketType;

    //QR 이미지를 그려주는 goqr.me(api.qrserver.com) create-qr-code API 베이스 URL.
    //디코딩(read-qr-code)은 쓰지 않는다 — 스캔 결과 문자열은 프론트에서 카메라로 직접 디코딩해 전달받는다.
    @Value("${qr.image-base-url:https://api.qrserver.com/v1/create-qr-code/}")
    private String qrImageBaseUrl;

    //페스티벌 시각 비교 기준 타임존. 서버(JVM) 기본 타임존에 기대지 않는다 — 아래 checkIn() 주석 참고.
    @Value("${app.timezone:Asia/Seoul}")
    private String appTimezone;

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
                .festivalId(festival.id())
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

    //참가자 본인의 확정된 예매에 대해 입장용 QR을 발급(조회)한다
    public ReservationQrResponseDto getQrForReservation(Long id, Long userId) {
        Reservation reservation = getOwnedReservation(id, userId);
        if (reservation.getReservationStatus() != ReservationStatus.CONFIRMED) {
            throw new ApiException(ReservationErrorCode.RESERVATION_NOT_CONFIRMED);
        }
        String qrImageUrl = qrImageBaseUrl + "?size=200x200&data="
                + URLEncoder.encode(reservation.getQrToken(), StandardCharsets.UTF_8);
        return new ReservationQrResponseDto(reservation.getId(), reservation.getQrToken(), qrImageUrl,
                reservation.getCheckInCode(), reservation.getCheckedInAt());
    }

    //주최자·도우미가 현장에서 스캔한 QR을 검증하고 입장 처리한다
    @Transactional
    public ReservationVerifyResponseDto verifyAndCheckIn(
            Long scannerUserId, String scannerRole, Long scannerFestivalId, ReservationVerifyRequestDto request) {
        Reservation reservation = reservationRepository.findByQrToken(request.qrToken())
                .orElseThrow(() -> new ApiException(ReservationErrorCode.INVALID_QR_TOKEN));

        return checkIn(reservation, scannerUserId, scannerRole, scannerFestivalId);
    }

    //QR 스캔이 안 될 때 도우미가 손으로 입력한 입장 코드로 검증하고 입장 처리한다
    @Transactional
    public ReservationVerifyResponseDto verifyAndCheckInByCode(
            Long scannerUserId, String scannerRole, Long scannerFestivalId, ReservationVerifyByCodeRequestDto request) {
        //현장에서 소문자로 입력하거나 앞뒤 공백이 섞여 들어오는 경우가 많아 정규화해서 조회한다.
        String checkInCode = request.checkInCode().trim().toUpperCase();
        Reservation reservation = reservationRepository.findByCheckInCode(checkInCode)
                .orElseThrow(() -> new ApiException(ReservationErrorCode.INVALID_CHECK_IN_CODE));

        return checkIn(reservation, scannerUserId, scannerRole, scannerFestivalId);
    }

    //총 티켓 수 대비 현재 입장 인원. 도우미 현장 화면과 주최자 화면이 같이 쓴다.
    public CheckInStatsResponseDto getCheckInStats(Long festivalId, Long scannerUserId, String scannerRole,
                                                   Long scannerFestivalId) {
        verifyFestivalAccess(festivalId, scannerUserId, scannerRole, scannerFestivalId);

        //환불된 장수는 현장에 오지 않으므로 총 티켓 수에서 빼야 한다 — 남은 장수(remainingQuantity)로 집계한다.
        List<Reservation> admittableReservations =
                reservationRepository.findByFestivalIdAndReservationStatusIn(festivalId, ADMITTABLE_STATUSES);

        int totalTickets = admittableReservations.stream().mapToInt(Reservation::remainingQuantity).sum();
        int checkedInTickets = admittableReservations.stream()
                .filter(reservation -> reservation.getCheckedInAt() != null)
                .mapToInt(Reservation::remainingQuantity)
                .sum();

        return new CheckInStatsResponseDto(festivalId, totalTickets, checkedInTickets);
    }

    //입장 검증 공통 로직(내부 메서드) — QR이든 입장 코드든 확인 순서와 거절 사유는 같아야 한다.
    private ReservationVerifyResponseDto checkIn(Reservation reservation, Long scannerUserId, String scannerRole,
                                                 Long scannerFestivalId) {
        //부분 환불된 예매도 남은 장수만큼은 입장시켜야 하므로 CONFIRMED만 보지 않는다.
        if (!reservation.isAdmittable()) {
            throw new ApiException(ReservationErrorCode.RESERVATION_NOT_CONFIRMED);
        }

        FestivalDetailResponseDto festival =
                verifyFestivalAccess(reservation.getFestivalId(), scannerUserId, scannerRole, scannerFestivalId);

        //공연 시작 전에 미리 입장시켜 버리는 사고를 막는다. 종료 시각은 막지 않는다 — 늦게 온 관객도 들여보내야 한다.
        //startAt은 호스트가 입력한 타임존 없는 벽시계(KST)라서, 서버 기본 타임존으로 now()를 뽑으면
        //UTC 환경에서 9시간 어긋난다. 비교 기준 타임존을 명시해 서버 설정과 무관하게 같은 결과를 낸다.
        if (festival.startAt() != null
                && LocalDateTime.now(ZoneId.of(appTimezone)).isBefore(festival.startAt())) {
            throw new ApiException(ReservationErrorCode.FESTIVAL_NOT_STARTED);
        }

        if (reservation.getCheckedInAt() != null) {
            throw new ApiException(ReservationErrorCode.ALREADY_CHECKED_IN);
        }

        reservation.checkIn();
        return ReservationVerifyResponseDto.from(reservation);
    }

    //검증자가 이 페스티벌을 다룰 권한이 있는지 확인한다(내부 메서드).
    //HOST는 본인이 주최한 페스티벌만, HELPER는 계정 발급 시 배정된 그 페스티벌만 볼 수 있다.
    private FestivalDetailResponseDto verifyFestivalAccess(Long festivalId, Long scannerUserId, String scannerRole,
                                                           Long scannerFestivalId) {
        if (HELPER_ROLE.equals(scannerRole)) {
            if (scannerFestivalId == null) {
                throw new ApiException(ReservationErrorCode.HELPER_FESTIVAL_NOT_ASSIGNED);
            }
            //도우미가 담당하지 않는 페스티벌의 티켓 — 현장에서 가장 흔한 오스캔이라 사유를 명확히 구분해준다.
            if (!scannerFestivalId.equals(festivalId)) {
                throw new ApiException(ReservationErrorCode.OTHER_FESTIVAL_TICKET);
            }
            return getFestivalOrThrow(festivalId);
        }

        FestivalDetailResponseDto festival = getFestivalOrThrow(festivalId);
        if (!scannerUserId.equals(festival.hostUserId())) {
            throw new ApiException(ReservationErrorCode.FORBIDDEN_NOT_ORGANIZER);
        }
        return festival;
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

        reservation.confirm(request.paymentId(), generateUnusedCheckInCode());
    }

    //입장 코드 발급(내부 메서드) — 32^10 조합이라 실제로는 첫 시도에서 끝나지만, 유니크 제약 위반으로
    //결제 확정 자체가 실패하는 일이 없도록 몇 번 더 뽑아본다.
    private String generateUnusedCheckInCode() {
        for (int attempt = 0; attempt < MAX_CHECK_IN_CODE_ATTEMPTS; attempt++) {
            String checkInCode = checkInCodeGenerator.generate();
            if (!reservationRepository.existsByCheckInCode(checkInCode)) {
                return checkInCode;
            }
        }
        throw new ApiException(ReservationErrorCode.CHECK_IN_CODE_GENERATION_FAILED);
    }

    //Payment-Service → Reservation-Service 내부 호출: 가상계좌 발급 시 입금 기한까지 재고 홀드 연장
    @Transactional
    public void extendReservationHold(Long id, ReservationExtendHoldRequestDto request) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ApiException(ReservationErrorCode.RESERVATION_NOT_FOUND));

        if (reservation.getReservationStatus() != ReservationStatus.PENDING) {
            //이미 확정·취소된 예매에 대한 지연 호출은 무시한다(멱등).
            return;
        }

        reservation.extendHold(request.expiresAt());
    }

    //Payment-Service → Reservation-Service 내부 호출: 환불 견적 조회.
    //공연 일정과 구매 수량을 아는 쪽이 여기라서, "얼마를 돌려줄 수 있는지"는 이 서비스가 판정한다.
    public ReservationRefundQuoteResponseDto getRefundQuote(Long id, Integer requestedQuantity) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ApiException(ReservationErrorCode.RESERVATION_NOT_FOUND));
        return quoteFor(reservation, requestedQuantity);
    }

    //참가자가 환불 버튼을 누르기 전에 "얼마를 돌려받는지"를 미리 보여주기 위한 본인 조회.
    //위약금을 모르고 환불을 확정하게 두면 안 되므로 화면에서 먼저 이 견적을 띄운다.
    public ReservationRefundQuoteResponseDto getMyRefundQuote(Long id, Long userId, Integer requestedQuantity) {
        return quoteFor(getOwnedReservation(id, userId), requestedQuantity);
    }

    private ReservationRefundQuoteResponseDto quoteFor(Reservation reservation, Integer requestedQuantity) {
        //수량을 안 보내면 남은 전량 환불로 본다(가이드 9.3 "전체 취소는 금액 생략"과 같은 맥락).
        int quantity = requestedQuantity != null ? requestedQuantity : reservation.remainingQuantity();

        RefundQuote quote = evaluateRefund(reservation, quantity);
        return ReservationRefundQuoteResponseDto.of(reservation, quote);
    }

    //Payment-Service → Reservation-Service 내부 호출: PortOne 취소 성공 후 환불 확정 + 재고 복구.
    @Transactional
    public void applyRefund(Long id, ReservationRefundRequestDto request) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ApiException(ReservationErrorCode.RESERVATION_NOT_FOUND));

        //이미 같은 수량까지 반영된 재호출은 재고를 다시 복구하지 않고 멱등하게 무시한다.
        //(PortOne 취소 웹훅과 API 응답이 같은 취소를 두 번 알려줄 수 있다 — 가이드 9.4)
        if (reservation.getRefundedQuantity() >= reservation.getQuantity()) {
            return;
        }
        if (request.quantity() > reservation.remainingQuantity()) {
            throw new ApiException(ReservationErrorCode.REFUND_QUANTITY_EXCEEDED);
        }

        reservation.refund(request.quantity());
        festivalServiceClient.restoreStock(reservation.getTicketTypeId(), request.quantity());
    }

    //환불 가능 여부 판정(내부 메서드) — 상태·입장 여부처럼 막는 조건을 먼저 보고, 마지막에 금액을 계산한다.
    private RefundQuote evaluateRefund(Reservation reservation, int quantity) {
        if (!reservation.isAdmittable()) {
            //결제가 확정되지 않았거나 이미 전액 환불·취소된 예매
            return RefundQuote.rejected(ReservationErrorCode.RESERVATION_NOT_REFUNDABLE.name(), quantity);
        }
        if (reservation.getCheckedInAt() != null) {
            return RefundQuote.rejected(ReservationErrorCode.ALREADY_CHECKED_IN_NOT_REFUNDABLE.name(), quantity);
        }
        if (quantity < 1 || quantity > reservation.remainingQuantity()) {
            return RefundQuote.rejected(ReservationErrorCode.REFUND_QUANTITY_EXCEEDED.name(), quantity);
        }

        //공연 시작 시각은 타임존 없는 벽시계라, checkIn()과 같은 기준 타임존으로 현재 시각을 뽑아 비교한다.
        LocalDateTime now = LocalDateTime.now(ZoneId.of(appTimezone));
        LocalDateTime startAt = getFestivalOrThrow(reservation.getFestivalId()).startAt();
        return refundPolicy.quote(startAt, now, quantity, reservation.getPrice());
    }

    //참가자 본인이 결제대기 중인 예매를 직접 취소한다
    @Transactional
    public void cancelMyReservation(Long id, Long userId) {
        Reservation reservation = getOwnedReservation(id, userId);
        if (reservation.getReservationStatus() != ReservationStatus.PENDING) {
            throw new ApiException(ReservationErrorCode.RESERVATION_NOT_CANCELLABLE);
        }
        reservation.cancel(CancelReason.USER_CANCELLED);
        festivalServiceClient.restoreStock(reservation.getTicketTypeId(), reservation.getQuantity());
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
