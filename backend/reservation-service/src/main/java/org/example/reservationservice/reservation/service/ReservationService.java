package org.example.reservationservice.reservation.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.example.reservationservice.common.exception.ApiException;
import org.example.reservationservice.reservation.entity.CheckInCodeGenerator;
import org.example.reservationservice.reservation.entity.refund.RefundPolicy;
import org.example.reservationservice.reservation.entity.refund.RefundQuote;
import org.example.reservationservice.reservation.entity.refund.StockReleaseQueue;
import org.example.reservationservice.reservation.entity.refund.StockReleaseQueueRepository;
import org.example.reservationservice.reservation.entity.refund.StockReleaseScheduler;
import org.example.reservationservice.reservation.entity.refund.SeatReleaseQueue;
import org.example.reservationservice.reservation.entity.refund.SeatReleaseQueueRepository;
import org.example.reservationservice.reservation.dto.*;
import org.example.reservationservice.reservation.entity.CancelReason;
import org.example.reservationservice.reservation.entity.Reservation;
import org.example.reservationservice.reservation.entity.RefundReceipt;
import org.example.reservationservice.reservation.entity.ReservationStatus;
import org.example.reservationservice.reservation.exception.ReservationErrorCode;
import org.example.reservationservice.reservation.repository.ReservationRepository;
import org.example.reservationservice.seat.entity.ReservationSeat;
import org.example.reservationservice.seat.entity.Seat;
import org.example.reservationservice.seat.entity.SeatStatus;
import org.example.reservationservice.seat.entity.TicketMode;
import org.example.reservationservice.seat.repository.ReservationSeatRepository;
import org.example.reservationservice.seat.repository.SeatRepository;
import org.example.reservationservice.seat.service.SeatBroadcastService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.example.reservationservice.reservation.infrastructure.festival.FestivalServiceClient;
import org.example.reservationservice.reservation.infrastructure.festival.dto.FestivalDetailResponseDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

@Service
@RequiredArgsConstructor
public class ReservationService {
    @PersistenceContext
    private EntityManager entityManager;

    private static final String PUBLISHED = "PUBLISHED";
    private static final String HELPER_ROLE = "HELPER";
    private static final Duration RESERVATION_HOLD_DURATION = Duration.ofMinutes(10);
    private static final int MAX_CHECK_IN_CODE_ATTEMPTS = 5;
    //취소되지 않은 것으로 보고 구매 제한에 합산할 상태들 (만료·취소 건은 다시 살 수 있어야 하므로 제외)
    private static final List<ReservationStatus> HELD_STATUSES = List.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED);
    //1인당 구매 제한에 합산할 상태 — 부분 환불된 예매도 남은 장수만큼은 들고 있는 것이다
    private static final List<ReservationStatus> PURCHASE_LIMIT_STATUSES =
            List.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED, ReservationStatus.PARTIALLY_REFUNDED);

    //입장 검증·현황 집계에 포함할 상태들. 부분 환불된 예매도 남은 장수만큼은 입장할 수 있어야 한다.
    private static final List<ReservationStatus> ADMITTABLE_STATUSES =
            List.of(ReservationStatus.CONFIRMED, ReservationStatus.PARTIALLY_REFUNDED);

    private final ReservationRepository reservationRepository;
    private final FestivalServiceClient festivalServiceClient;
    private final CheckInCodeGenerator checkInCodeGenerator;
    private final RefundPolicy refundPolicy;
    private final StockReleaseQueueRepository stockReleaseQueueRepository;
    private final StockReleaseScheduler stockReleaseScheduler;

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    private final SeatRepository seatRepository;
    private final ReservationSeatRepository reservationSeatRepository;
    private final SeatReleaseQueueRepository seatReleaseQueueRepository;
    private final SeatBroadcastService seatBroadcastService;


    //사이트 전체 기본 1인당 구매 제한(계정 기준, 페스티벌당 — 티켓 종류를 나눠 사도 합산). 티켓 종류당으로 세던 시절엔
    //같은 페스티벌에서 종류별로 4장씩 사 8장까지 가능했다(QA에서 발견). 주최자가 페스티벌별로 더 낮게 설정하는 기능은 아직 없다.
    @Value("${reservation.max-quantity-per-festival:${reservation.max-quantity-per-ticket-type:4}}")
    private int maxQuantityPerFestival;

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

        checkTicketSalePeriodOrThrow(ticketType);

        TicketMode mode = parseTicketModeOrThrow(ticketType.ticketMode());

        Reservation reservation = switch (mode) {
            case SEATED -> createSeatedReservation(userId, festival, request, ticketType);
            case STANDING -> createStandingReservation(userId, festival, request, ticketType);
        };

        log.info("예매 생성: reservation={}, user={}, festival={}, ticketType={}, qty={}, mode={}",
                reservation.getId(), userId, festival.id(), request.ticketTypeId(), reservation.getQuantity(), mode);

        return ReservationResponseDto.from(reservation);
    }

    private TicketMode parseTicketModeOrThrow(String ticketMode) {
        try {
            return TicketMode.valueOf(ticketMode);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ApiException(ReservationErrorCode.INVALID_SEAT_REQUEST);
        }
    }

    private Reservation createSeatedReservation(Long userId, FestivalDetailResponseDto festival,
                                                ReservationCreateRequestDto request,
                                                FestivalDetailResponseDto.TicketTypeSummary ticketType) {
        if (request.seatIds() == null || request.seatIds().isEmpty()) {
            throw new ApiException(ReservationErrorCode.INVALID_SEAT_REQUEST);
        }

        List<Seat> seats = seatRepository.findAllById(request.seatIds());
        if (seats.size() != request.seatIds().size()) {
            throw new ApiException(ReservationErrorCode.SEAT_NOT_FOUND);
        }
        boolean belongsToRequest = seats.stream().allMatch(seat ->
                seat.getFestivalId().equals(festival.id()) && seat.getTicketTypeId().equals(request.ticketTypeId()));
        if (!belongsToRequest) {
            throw new ApiException(ReservationErrorCode.SEAT_NOT_FOUND);
        }

        checkPurchaseLimitOrThrow(userId, festival.id(), request.seatIds().size());

        Instant heldUntil = Instant.now().plus(RESERVATION_HOLD_DURATION);
        for (Long seatId : request.seatIds()) {
            int updated = seatRepository.holdSeat(seatId, userId, heldUntil);
            if (updated == 0) {
                throw new ApiException(ReservationErrorCode.SEAT_ALREADY_TAKEN);
            }
            seatBroadcastService.broadcast(festival.id(), request.ticketTypeId(), seatId, SeatStatus.HELD);
        }

        //좌석은 holdSeat()의 원자적 UPDATE가 실제 재고 가드라 remainQuantity 차감이 판매 자체를 막는 데 쓰이진
        //않지만, festival-service가 GET /api/festivals/{id}로 노출하는 잔여 수량을 좌석 판매에도 맞춰
        //보여주기 위해 잡은 좌석 수만큼 STANDING과 같은 원자적 차감 API를 그대로 호출한다.
        deductStockOrThrow(request.ticketTypeId(), request.seatIds().size());

        Reservation reservation = Reservation.builder()
                .userId(userId)
                .festivalId(festival.id())
                .hostUserId(festival.hostUserId())
                .ticketTypeId(request.ticketTypeId())
                .quantity(request.seatIds().size())
                .price(ticketType.price())
                .reservationStatus(ReservationStatus.PENDING)
                .expiresAt(Instant.now().plus(RESERVATION_HOLD_DURATION))
                .build();

        try {
            reservationRepository.save(reservation);

            List<ReservationSeat> reservationSeats = request.seatIds().stream()
                    .map(seatId -> ReservationSeat.builder()
                            .reservation(reservation)
                            .seat(seats.stream().filter(s -> s.getId().equals(seatId)).findFirst().orElseThrow())
                            .build())
                    .toList();
            reservationSeatRepository.saveAll(reservationSeats);
        } catch (RuntimeException e) {
            log.error("좌석 예매 저장 실패 — 차감한 재고 복구 시도: ticketType={}, qty={}",
                    request.ticketTypeId(), request.seatIds().size(), e);
            festivalServiceClient.restoreStock(request.ticketTypeId(), request.seatIds().size());
            throw e;
        }

        return reservation;
    }

    private Reservation createStandingReservation(Long userId, FestivalDetailResponseDto festival,
                                                  ReservationCreateRequestDto request,
                                                  FestivalDetailResponseDto.TicketTypeSummary ticketType) {
        if (request.quantity() == null || request.quantity() < 1) {
            throw new ApiException(ReservationErrorCode.INVALID_SEAT_REQUEST);
        }

        checkPurchaseLimitOrThrow(userId, festival.id(), request.quantity());
        deductStockOrThrow(request.ticketTypeId(), request.quantity());

        Reservation reservation = Reservation.builder()
                .userId(userId)
                .festivalId(festival.id())
                .hostUserId(festival.hostUserId())
                .ticketTypeId(request.ticketTypeId())
                .quantity(request.quantity())
                .price(ticketType.price())
                .reservationStatus(ReservationStatus.PENDING)
                .expiresAt(Instant.now().plus(RESERVATION_HOLD_DURATION))
                .build();

        try {
            reservationRepository.save(reservation);
        } catch (RuntimeException e) {
            log.error("예매 저장 실패 — 차감한 재고 복구 시도: ticketType={}, qty={}", request.ticketTypeId(), request.quantity(), e);
            festivalServiceClient.restoreStock(request.ticketTypeId(), request.quantity());
            throw e;
        }

        return reservation;
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
        //부분 환불 뒤에도 남은 티켓은 입장할 수 있으므로 같은 기준으로 QR을 제공한다.
        if (!reservation.isAdmittable() || reservation.remainingQuantity() <= 0) {
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

        //조회 이후 다른 스캐너가 입장 처리했을 수 있으므로 DB에서 한 번만 갱신한다.
        Instant checkedInAt = Instant.now();
        if (reservationRepository.checkInIfNotCheckedIn(reservation.getId(), checkedInAt) == 0) {
            throw new ApiException(ReservationErrorCode.ALREADY_CHECKED_IN);
        }
        return new ReservationVerifyResponseDto(reservation.getId(), reservation.getTicketTypeId(),
                reservation.remainingQuantity(), checkedInAt);
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

    //주최자 정산용: 해당 페스티벌의 모든 예매 내역 조회
    public List<ReservationForPaymentResponseDto> settlementReservations(Long festivalId) {
        return reservationRepository.findByFestivalId(festivalId).stream()
                .map(ReservationForPaymentResponseDto::from)
                .toList();
    }

    //Payment-Service → Reservation-Service 내부 호출: 결제 성공 확정
    @Transactional
    public void confirmReservation(Long id, ReservationConfirmRequestDto request) {
        //만료 배치와 같은 잠금을 잡아 확정·취소 중 먼저 끝난 상태를 기준으로 판단한다.
        Reservation reservation = reservationRepository.findByIdForUpdate(id)
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
        markSeatsSoldIfAny(reservation);
    }

    //SEATED 예매면 연결된 좌석들을 SOLD로 확정한다. STANDING이면 좌석이 없으니 아무것도 안 한다.
    private void markSeatsSoldIfAny(Reservation reservation) {
        List<ReservationSeat> reservationSeats = reservationSeatRepository.findByReservationId(reservation.getId());
        for (ReservationSeat reservationSeat : reservationSeats) {
            Seat seat = reservationSeat.getSeat();
            int updated = seatRepository.markSold(seat.getId());
            if (updated == 0) {
                log.warn("예매 {} 확정 시 좌석 {} SOLD 전환 실패(이미 HELD가 아님)", reservation.getId(), seat.getId());
            } else {
                seatBroadcastService.broadcast(seat.getFestivalId(), seat.getTicketTypeId(), seat.getId(), SeatStatus.SOLD);
            }
        }
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

    //주최자용 환불 견적 — 위약금 없이 남은 전량 기준 금액만 보여준다(정산/환불 승인 화면용).
    public ReservationRefundQuoteResponseDto getOrganizerRefundQuote(Long id) {
        Reservation r = reservationRepository.findById(id)
                .orElseThrow(() -> new ApiException(ReservationErrorCode.RESERVATION_NOT_FOUND));
        if (!r.isAdmittable()) {
            return ReservationRefundQuoteResponseDto.of(r,
                    RefundQuote.rejected("RESERVATION_NOT_REFUNDABLE", r.remainingQuantity()));
        }
        return ReservationRefundQuoteResponseDto.of(r, RefundQuote.allowed(r.remainingQuantity(), 0,
                Math.multiplyExact((long) r.getPrice(), r.remainingQuantity())));
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

    //Payment-Service → Reservation-Service 내부 호출: PortOne 취소 성공 후 환불 확정 + 재고/좌석 복구.
    @Transactional
    public void applyRefund(Long id, ReservationRefundRequestDto request) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ApiException(ReservationErrorCode.RESERVATION_NOT_FOUND));

        //같은 예매에 환불 반영이 동시에 들어오면(웹훅 + API 응답) 수량이 이중 차감되지 않도록 행을 잠근다.
        entityManager.lock(reservation, LockModeType.PESSIMISTIC_WRITE);
        if (!Objects.equals(reservation.getPaymentId(), request.paymentId())) {
            throw new ApiException(ReservationErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }
        //PortOne 취소 ID별로 한 번만 반영한다 — 같은 취소를 웹훅과 API 응답이 각각 알려와도 수량은 한 번만 줄어야 한다.
        if (request.cancellationId() != null) {
            RefundReceipt receipt = entityManager.find(RefundReceipt.class, request.cancellationId());
            if (receipt != null) {
                if (!receipt.matches(id, request.quantity())) {
                    throw new ApiException(ReservationErrorCode.PAYMENT_AMOUNT_MISMATCH);
                }
                return;
            }
            entityManager.persist(new RefundReceipt(request.cancellationId(), id, request.quantity()));
        }

        //이미 같은 수량까지 반영된 재호출은 재고를 다시 복구하지 않고 멱등하게 무시한다.
        //(PortOne 취소 웹훅과 API 응답이 같은 취소를 두 번 알려줄 수 있다 — 가이드 9.4)
        if (reservation.getRefundedQuantity() >= reservation.getQuantity()) {
            return;
        }
        if (request.quantity() > reservation.remainingQuantity()) {
            throw new ApiException(ReservationErrorCode.REFUND_QUANTITY_EXCEEDED);
        }

        boolean isSeated = request.seatIds() != null && !request.seatIds().isEmpty();
        if (isSeated) {
            validateSeatIdsBelongToReservation(reservation, request.seatIds());
            if (request.seatIds().size() != request.quantity()) {
                throw new ApiException(ReservationErrorCode.INVALID_SEAT_REQUEST);
            }
        }

        reservation.refund(request.quantity());
        //환불 재고/좌석은 바로 풀지 않는다 — 팀 정책(리셀 방지): 모아 두었다가 매일 정해진 시각에 일괄 반환.
        Instant releaseAt = stockReleaseScheduler.nextReleaseInstant(Instant.now());

        if (isSeated) {
            for (Long seatId : request.seatIds()) {
                seatReleaseQueueRepository.save(new SeatReleaseQueue(
                        reservation.getId(), seatId, releaseAt));
            }
            log.info("환불 확정(좌석): reservation={}, seatIds={}, qty={}, 반환 예정={}",
                    reservation.getId(), request.seatIds(), request.quantity(), releaseAt);
        } else {
            stockReleaseQueueRepository.save(new StockReleaseQueue(
                    reservation.getId(), reservation.getTicketTypeId(), request.quantity(), releaseAt));
            log.info("환불 확정: reservation={}, ticketType={}, qty={}, 반환 예정={}",
                    reservation.getId(), reservation.getTicketTypeId(), request.quantity(), releaseAt);
        }
    }

    //요청받은 seatIds가 실제로 이 예매에 속한 좌석인지 확인한다(내부 메서드) — 다른 예매의 좌석 id를
    //섞어 보내는 요청을 막는다.
    private void validateSeatIdsBelongToReservation(Reservation reservation, List<Long> seatIds) {
        List<Long> ownedSeatIds = reservationSeatRepository.findByReservationId(reservation.getId()).stream()
                .map(rs -> rs.getSeat().getId())
                .toList();
        if (!ownedSeatIds.containsAll(seatIds)) {
            throw new ApiException(ReservationErrorCode.SEAT_NOT_FOUND);
        }
    }

    //환불 가능 여부 판정(내부 메서드) — 상태·입장 여부처럼 막는 조건을 먼저 보고, 마지막에 금액을 계산한다.
    private RefundQuote evaluateRefund(Reservation reservation, int quantity) {
        if (!reservation.isAdmittable()) {
            //결제가 확정되지 않았거나 이미 전액 환불·취소된 예매
            return RefundQuote.rejected(ReservationErrorCode.RESERVATION_NOT_REFUNDABLE.name(), quantity);
        }
        FestivalDetailResponseDto festival = getFestivalOrThrow(reservation.getFestivalId());
        //주최자 귀책으로 행사 취소가 진행 중이거나 끝났으면 운영자 승인 후 위약금 없이 전액 환불된다(주최자 귀책 환불).
        //화면에서 버튼만 숨기면 API 직접 호출로 위약금을 떼고 먼저 환불받을 수 있어, 본인 환불은 여기서 막는다.
        if (List.of("CANCELLATION_PENDING", "CANCELLED").contains(festival.festivalStatus())) {
            return RefundQuote.rejected(ReservationErrorCode.FESTIVAL_CANCELLATION_REFUND_PENDING.name(), quantity);
        }
        if (reservation.getCheckedInAt() != null) {
            return RefundQuote.rejected(ReservationErrorCode.ALREADY_CHECKED_IN_NOT_REFUNDABLE.name(), quantity);
        }
        if (quantity < 1 || quantity > reservation.remainingQuantity()) {
            return RefundQuote.rejected(ReservationErrorCode.REFUND_QUANTITY_EXCEEDED.name(), quantity);
        }

        //공연 시작 시각은 타임존 없는 벽시계라, checkIn()과 같은 기준 타임존으로 현재 시각을 뽑아 비교한다.
        LocalDateTime now = LocalDateTime.now(ZoneId.of(appTimezone));
        return refundPolicy.quote(festival.startAt(), now, quantity, reservation.getPrice());
    }

    //참가자 본인이 결제대기 중인 예매를 직접 취소한다
    @Transactional
    public void cancelMyReservation(Long id, Long userId) {
        Reservation reservation = getOwnedReservation(id, userId);
        if (reservation.getReservationStatus() != ReservationStatus.PENDING) {
            throw new ApiException(ReservationErrorCode.RESERVATION_NOT_CANCELLABLE);
        }
        reservation.cancel(CancelReason.USER_CANCELLED);
        releaseSeatsOrRestoreStock(reservation);
        log.info("예매 취소(본인): reservation={}, ticketType={}, qty={} 재고 복구 요청 완료",
                reservation.getId(), reservation.getTicketTypeId(), reservation.getQuantity());
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
        releaseSeatsOrRestoreStock(reservation);
        log.info("예매 취소(결제 {}): reservation={}, ticketType={}, qty={} 재고 복구 요청 완료",
                request.reasonCode(), reservation.getId(), reservation.getTicketTypeId(), reservation.getQuantity());
    }

    //예매 취소/만료 시 재고를 복구한다(내부 메서드). SEATED면 연결된 좌석을 로컬에서 AVAILABLE로 원복하고
    //실시간 브로드캐스트까지 처리한다. STANDING이면 기존처럼 festival-service 재고를 복구한다.
    private void releaseSeatsOrRestoreStock(Reservation reservation) {
        List<ReservationSeat> reservationSeats = reservationSeatRepository.findByReservationId(reservation.getId());
        if (!reservationSeats.isEmpty()) {
            int releasedCount = 0;
            for (ReservationSeat reservationSeat : reservationSeats) {
                Seat seat = reservationSeat.getSeat();
                int updated = seatRepository.releaseSeat(seat.getId());
                if (updated > 0) {
                    releasedCount++;
                    seatBroadcastService.broadcast(seat.getFestivalId(), seat.getTicketTypeId(), seat.getId(), SeatStatus.AVAILABLE);
                } else {
                    log.warn("예매 {} 취소 시 좌석 {} 원복 실패(이미 HELD가 아님)", reservation.getId(), seat.getId());
                }
            }
            //실제로 되돌린 좌석 수만큼만 festival-service 잔여 수량을 복구한다(이미 원복된 좌석은 중복으로 세지 않는다).
            if (releasedCount > 0) {
                festivalServiceClient.restoreStock(reservation.getTicketTypeId(), releasedCount);
            }
        } else {
            festivalServiceClient.restoreStock(reservation.getTicketTypeId(), reservation.getQuantity());
        }
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

    //티켓 판매 기간 검증(내부 메서드) — festival-service에 등록된 saleStartAt/saleEndAt 구간 밖이면 거부한다.
    //startAt/endAt과 같은 타임존 없는 벽시계 값이라, checkIn()·환불 판정과 같은 기준 타임존으로 now()를 뽑는다.
    private void checkTicketSalePeriodOrThrow(FestivalDetailResponseDto.TicketTypeSummary ticketType) {
        LocalDateTime now = LocalDateTime.now(ZoneId.of(appTimezone));
        if (ticketType.saleStartAt() != null && now.isBefore(ticketType.saleStartAt())) {
            throw new ApiException(ReservationErrorCode.TICKET_SALE_NOT_STARTED);
        }
        if (ticketType.saleEndAt() != null && now.isAfter(ticketType.saleEndAt())) {
            throw new ApiException(ReservationErrorCode.TICKET_SALE_ENDED);
        }
    }

    //1인당 구매 제한 검증(내부 메서드) — 계정 기준, 같은 페스티벌의 모든 티켓 종류에 대해 PENDING·CONFIRMED로 이미 들고 있는
    //수량(환불된 장수는 제외) + 이번 요청 수량이 한도를 넘으면 거부
    private void checkPurchaseLimitOrThrow(Long userId, Long festivalId, int quantity) {
        int alreadyHeld = reservationRepository
                .findByUserIdAndFestivalIdAndReservationStatusIn(userId, festivalId, PURCHASE_LIMIT_STATUSES).stream()
                .mapToInt(Reservation::remainingQuantity)
                .sum();
        if (alreadyHeld + quantity > maxQuantityPerFestival) {
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