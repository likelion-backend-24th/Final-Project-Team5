package org.example.festivalservice.domain.festival;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.festivalservice.common.exception.ApiException;
import org.example.festivalservice.domain.hostapplication.HostApplicationRepository;
import org.example.festivalservice.domain.hostapplication.HostApplicationStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 어드민 대시보드 요약 숫자. 목록을 가져오지 않고 개수만 센다(payment-service 호출 없음).
 */
@Service
@RequiredArgsConstructor
public class AdminSummaryService {

    private static final String ADMIN_ROLE = "ADMIN";

    //등록 승인 탭 "승인대기"와 같은 기준 (FestivalService.REVIEW_PENDING_STATUSES와 맞춰야 한다)
    private static final List<FestivalStatus> REVIEW_PENDING_STATUSES =
            List.of(FestivalStatus.PENDING, FestivalStatus.PUBLISH_PENDING);

    //주최자 신청 탭 "승인대기"와 같은 기준 (프론트 matchesStatus 기준 확인 후 확정)
    private static final List<HostApplicationStatus> HOST_APPLICATION_PENDING_STATUSES =
            List.of(HostApplicationStatus.PENDING);

    private final FestivalRepository festivalRepository;
    private final HostApplicationRepository hostApplicationRepository;

    @Value("${app.timezone:Asia/Seoul}")
    private String appTimezone;

    @Transactional(readOnly = true)
    public AdminSummaryResponseDto getSummary(String role) {
        if (!ADMIN_ROLE.equals(role)) {
            throw new ApiException(FestivalErrorCode.FORBIDDEN_ADMIN_ROLE);
        }

        LocalDateTime now = LocalDateTime.now(ZoneId.of(appTimezone));

        return new AdminSummaryResponseDto(
                festivalRepository.countOngoing(now),
                festivalRepository.countScheduled(now),
                hostApplicationRepository.countByStatusIn(HOST_APPLICATION_PENDING_STATUSES),
                festivalRepository.countByFestivalStatusIn(REVIEW_PENDING_STATUSES),
                festivalRepository.countByFestivalStatusAndCancellationApprovedAtIsNull(
                        FestivalStatus.CANCELLATION_PENDING),
                festivalRepository.countByFestivalStatusAndCancellationApprovedAtIsNotNull(
                        FestivalStatus.CANCELLATION_PENDING)
        );
    }
}