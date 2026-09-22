package org.example.festivalservice.domain.festival;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.example.festivalservice.common.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주최자 귀책 행사 취소 흐름(요청 → 운영자 승인 → payment-service 환불 배치 → 완료)과
 * payment-service 정산 배치가 쓰는 내부 조회를 담당한다. 상태 검증은 여기서 하고 엔티티는 전이만 기록한다.
 */
@Service
@RequiredArgsConstructor
public class FestivalCancellationService {

    private static final String HOST_ROLE = "HOST";
    private static final String ADMIN_ROLE = "ADMIN";
    //정산 배치가 한 번에 훑는 후보 수. payment-service의 FestivalSettlementClient가 같은 크기를 가정한다.
    private static final int SETTLEMENT_CANDIDATE_PAGE_SIZE = 100;
    //정산 가능 시각 = 행사 종료 + 24시간. 그 사이 환불·취소가 정리될 시간을 둔다.
    private static final int SETTLEMENT_GRACE_HOURS = 24;
    private static final List<FestivalStatus> SETTLEMENT_STATUSES = List.of(
            FestivalStatus.PUBLISHED, FestivalStatus.CLOSED, FestivalStatus.CANCELLATION_PENDING,
            FestivalStatus.CANCELLED);

    private final FestivalRepository festivalRepository;

    //호스트가 입력한 종료 시각은 타임존 없는 벽시계 값이라 "지금"과 비교할 기준 타임존을 명시한다(auth-service와 같은 키).
    @Value("${app.timezone:Asia/Seoul}")
    private String appTimezone;

    //주최자가 취소를 요청한다. 이미 취소 진행 중이면 그대로 현재 상태를 돌려준다(재클릭 안전).
    @Transactional
    public FestivalStatus requestCancellation(Long id, Long hostUserId, String role, String reason) {
        if (!HOST_ROLE.equals(role)) {
            throw new ApiException(FestivalErrorCode.FORBIDDEN_HOST_ROLE);
        }
        Festival festival = getFestival(id);
        //남의 페스티벌은 존재 여부조차 알려주지 않도록 403이 아니라 404로 응답한다.
        if (!hostUserId.equals(festival.getHostUserId())) {
            throw new ApiException(FestivalErrorCode.FESTIVAL_NOT_FOUND);
        }
        FestivalStatus status = festival.getFestivalStatus();
        if (status == FestivalStatus.CANCELLATION_PENDING || status == FestivalStatus.CANCELLED) {
            return status;
        }
        if (status != FestivalStatus.PUBLISHED && status != FestivalStatus.CLOSED) {
            throw new ApiException(FestivalErrorCode.FESTIVAL_NOT_CANCELLABLE);
        }
        if (reason == null || reason.isBlank() || reason.length() > 500) {
            throw new ApiException(FestivalErrorCode.CANCEL_REASON_REQUIRED);
        }
        festival.requestCancellation(hostUserId, reason);
        return festival.getFestivalStatus();
    }

    //운영자가 취소를 승인한다. 승인 시각·담당자는 최초 1회만 기록해 재시도해도 근거가 바뀌지 않게 한다.
    @Transactional
    public FestivalStatus approveCancellation(Long id, Long adminUserId, String role) {
        if (!ADMIN_ROLE.equals(role)) {
            throw new ApiException(FestivalErrorCode.FORBIDDEN_ADMIN_ROLE);
        }
        Festival festival = getFestival(id);
        if (festival.getFestivalStatus() != FestivalStatus.CANCELLATION_PENDING) {
            throw new ApiException(FestivalErrorCode.CANCELLATION_NOT_REQUESTED);
        }
        if (festival.getCancellationApprovedAt() == null) {
            festival.approveCancellation(adminUserId);
        }
        return festival.getFestivalStatus();
    }

    //운영자가 취소 요청을 반려한다. 승인 뒤에는 환불 배치가 이미 돈을 돌려주고 있으므로 되돌릴 수 없다.
    @Transactional
    public FestivalStatus rejectCancellation(Long id, String role) {
        if (!ADMIN_ROLE.equals(role)) {
            throw new ApiException(FestivalErrorCode.FORBIDDEN_ADMIN_ROLE);
        }
        Festival festival = getFestival(id);
        if (festival.getFestivalStatus() != FestivalStatus.CANCELLATION_PENDING) {
            throw new ApiException(FestivalErrorCode.CANCELLATION_NOT_REQUESTED);
        }
        if (festival.getCancellationApprovedAt() != null) {
            throw new ApiException(FestivalErrorCode.CANCELLATION_ALREADY_APPROVED);
        }
        festival.rejectCancellation();
        return festival.getFestivalStatus();
    }

    //운영자 화면용 — 취소 대기 중인 페스티벌과 승인 여부
    @Transactional(readOnly = true)
    public List<FestivalCancellationRequestResponseDto> listCancellationRequests(String role) {
        if (!ADMIN_ROLE.equals(role)) {
            throw new ApiException(FestivalErrorCode.FORBIDDEN_ADMIN_ROLE);
        }
        return festivalRepository.findByFestivalStatus(FestivalStatus.CANCELLATION_PENDING).stream()
                .map(festival -> new FestivalCancellationRequestResponseDto(
                        festival.getId(),
                        festival.getName(),
                        festival.getCancelReason(),
                        festival.getCancellationApprovedAt() != null))
                .toList();
    }

    //payment-service 환불 배치용 — 승인이 끝난 취소 요청만 전액 환불 대상이다
    @Transactional(readOnly = true)
    public List<RefundCandidateDto> refundCandidates() {
        return festivalRepository.findByFestivalStatusAndCancellationApprovedAtIsNotNull(
                        FestivalStatus.CANCELLATION_PENDING).stream()
                .map(festival -> new RefundCandidateDto(
                        festival.getId(),
                        festival.getHostUserId(),
                        festival.getCancelledByUserId(),
                        festival.getCancelReason()))
                .toList();
    }

    //환불 배치가 모든 결제의 환불과 예매 반영을 확인한 뒤 호출한다. 이미 완료됐으면 최초 완료 시각을 보존한다.
    @Transactional
    public void completeCancellation(Long id) {
        Festival festival = getFestival(id);
        if (festival.getFestivalStatus() == FestivalStatus.CANCELLED) {
            return;
        }
        if (festival.getCancellationApprovedAt() == null
                || festival.getFestivalStatus() != FestivalStatus.CANCELLATION_PENDING) {
            throw new ApiException(FestivalErrorCode.CANCELLATION_NOT_APPROVED);
        }
        festival.completeCancellation();
    }

    //payment-service 정산 배치용 — 정산 가능 시각(종료 + 24시간)이 지난 페스티벌을 페이지 단위로 넘긴다
    @Transactional(readOnly = true)
    public List<SettlementContextDto> settlementCandidates(int page) {
        if (page < 0) {
            throw new ApiException(FestivalErrorCode.INVALID_SETTLEMENT_PAGE);
        }
        ZoneId timezone = ZoneId.of(appTimezone);
        LocalDateTime cutoff = LocalDateTime.now(timezone).minusHours(SETTLEMENT_GRACE_HOURS);
        return festivalRepository.findSettlementCandidates(cutoff, SETTLEMENT_STATUSES,
                        PageRequest.of(page, SETTLEMENT_CANDIDATE_PAGE_SIZE))
                .stream()
                .map(festival -> SettlementContextDto.from(festival, timezone))
                .toList();
    }

    //payment-service가 정산을 계산하기 직전 페스티벌 하나의 최신 상태를 다시 확인할 때 쓴다
    @Transactional(readOnly = true)
    public SettlementContextDto settlementContext(Long id) {
        return SettlementContextDto.from(getFestival(id), ZoneId.of(appTimezone));
    }

    private Festival getFestival(Long id) {
        return festivalRepository.findById(id)
                .orElseThrow(() -> new ApiException(FestivalErrorCode.FESTIVAL_NOT_FOUND));
    }
}
