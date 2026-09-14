package org.example.festivalservice.domain.festival;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.festivalservice.common.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FestivalCancellationService {

    private final FestivalRepository repository;

    @Transactional
    public FestivalStatus requestCancellation(Long id, Long hostUserId, String role, String reason) {
        requireRole(role, "HOST", FestivalErrorCode.FORBIDDEN_HOST_ROLE);
        var festival = get(id);
        // 타인의 행사 존재 여부를 취소 API로 알아낼 수 없도록 소유권 불일치는 404로 처리한다.
        if (!hostUserId.equals(festival.getHostUserId())) throw new ApiException(FestivalErrorCode.FESTIVAL_NOT_FOUND);
        if (
            festival.getFestivalStatus() == FestivalStatus.CANCELLATION_PENDING ||
            festival.getFestivalStatus() == FestivalStatus.CANCELLED
        ) return festival.getFestivalStatus();
        if (
            festival.getFestivalStatus() != FestivalStatus.PUBLISHED &&
            festival.getFestivalStatus() != FestivalStatus.CLOSED
        ) throw new ApiException(FestivalErrorCode.FESTIVAL_NOT_CANCELLABLE);
        if (reason == null || reason.isBlank() || reason.length() > 500) throw new ApiException(
            FestivalErrorCode.CANCEL_REASON_REQUIRED
        );
        festival.requestCancellation(hostUserId, reason);
        return festival.getFestivalStatus();
    }

    @Transactional
    public FestivalStatus approveCancellation(Long id, Long actorId, String role) {
        requireRole(role, "ADMIN", FestivalErrorCode.FORBIDDEN_ADMIN_ROLE);
        var festival = get(id);
        if (festival.getFestivalStatus() != FestivalStatus.CANCELLATION_PENDING) throw new ApiException(
            FestivalErrorCode.CANCELLATION_NOT_REQUESTED
        );
        // 승인 재시도는 최초 담당자와 시각을 덮어쓰지 않는다.
        if (festival.getCancellationApprovedAt() == null) festival.approveCancellation(actorId);
        return festival.getFestivalStatus();
    }

    @Transactional(readOnly = true)
    public List<FestivalCancellationRequestResponseDto> listCancellationRequests(String role) {
        requireRole(role, "ADMIN", FestivalErrorCode.FORBIDDEN_ADMIN_ROLE);
        return repository
            .findByFestivalStatus(FestivalStatus.CANCELLATION_PENDING)
            .stream()
            .map(f ->
                new FestivalCancellationRequestResponseDto(
                    f.getId(),
                    f.getName(),
                    f.getCancelReason(),
                    f.getCancellationApprovedAt() != null
                )
            )
            .toList();
    }

    @Transactional(readOnly = true)
    public List<RefundCandidateDto> refundCandidates() {
        return repository
            .findByFestivalStatusAndCancellationApprovedAtIsNotNull(FestivalStatus.CANCELLATION_PENDING)
            .stream()
            .map(f ->
                new RefundCandidateDto(f.getId(), f.getHostUserId(), f.getCancelledByUserId(), f.getCancelReason())
            )
            .toList();
    }

    @Transactional
    public void completeCancellation(Long id) {
        var festival = get(id);
        // 환불 작업자의 재시도에서도 최초 완료 시각을 보존한다.
        if (festival.getFestivalStatus() == FestivalStatus.CANCELLED) return;
        if (
            festival.getCancellationApprovedAt() == null ||
            festival.getFestivalStatus() != FestivalStatus.CANCELLATION_PENDING
        ) throw new ApiException(FestivalErrorCode.CANCELLATION_NOT_APPROVED);
        festival.completeCancellation();
    }

    private Festival get(Long id) {
        return repository.findById(id).orElseThrow(() -> new ApiException(FestivalErrorCode.FESTIVAL_NOT_FOUND));
    }

    private void requireRole(String role, String expected, FestivalErrorCode code) {
        if (!expected.equals(role)) throw new ApiException(code);
    }
}
