package org.example.festivalservice.domain.hostapplication;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.example.festivalservice.common.UserLookupClient;
import org.example.festivalservice.common.UserLookupClient.UserSummary;
import org.example.festivalservice.common.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
@RequiredArgsConstructor
public class HostApplicationService {

    private static final String HOST_ROLE = "HOST";
    private static final String ADMIN_ROLE = "ADMIN";
    private static final Logger log = LoggerFactory.getLogger(HostApplicationService.class);

    private final HostApplicationRepository hostApplicationRepository;
    private final RestClient authServiceRestClient;
    private final UserLookupClient userLookupClient;

    @Value("${internal.auth-service.token:CHANGE_ME_IN_ENV}")
    private String internalAuthToken;

    //페스티벌 주최자가 되기 위한 신청을 제출할 때
    @Transactional
    public HostApplicationResponseDto submit(Long userId, String role, HostApplicationSubmitRequestDto request) {
        if (ADMIN_ROLE.equals(role)) {
            throw new ApiException(HostApplicationErrorCode.FORBIDDEN_ROLE);
        }
        if (HOST_ROLE.equals(role)) {
            throw new ApiException(HostApplicationErrorCode.ALREADY_HOST);
        }
        if (hostApplicationRepository.existsByUserIdAndStatus(userId, HostApplicationStatus.PENDING)) {
            throw new ApiException(HostApplicationErrorCode.DUPLICATE_APPLICATION);
        }

        HostApplication application =
                new HostApplication(userId, request.introduction(), request.contact());
        hostApplicationRepository.save(application);
        return HostApplicationResponseDto.from(application);
    }

    //주최자의 주최 신청 상태·반려사유를 조회할 때
    public HostApplicationResponseDto getMy(Long userId) {
        HostApplication application = hostApplicationRepository
                .findFirstByUserIdOrderByCreatedAtDesc(userId)
                .orElseThrow(() -> new ApiException(HostApplicationErrorCode.APPLICATION_NOT_FOUND));
        return HostApplicationResponseDto.from(application);
    }

    //본인의 주최 신청 이력 전체(반려→재신청 등)를 최신순으로 조회할 때
    public List<HostApplicationResponseDto> getMyHistory(Long userId) {
        return hostApplicationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(HostApplicationResponseDto::from)
                .toList();
    }

    //운영자가 주최 신청 목록을 조회 — 심사 대기뿐 아니라 승인·반려된 것도 이력으로 함께 내려준다.
    //신청자 이름·이메일은 auth-service에서 한 번에 조회해 붙인다(실패하면 null로 내려가고 목록은 유지).
    public List<HostApplicationResponseDto> getListHostApplications(String role) {
        if (!ADMIN_ROLE.equals(role)) {
            throw new ApiException(HostApplicationErrorCode.FORBIDDEN_ADMIN_ROLE);
        }
        List<HostApplication> applications = hostApplicationRepository.findAllByOrderByCreatedAtDesc();
        Map<Long, UserSummary> applicants = userLookupClient.findByIds(
                applications.stream().map(HostApplication::getUserId).toList());
        return applications.stream()
                .map(application -> HostApplicationResponseDto.from(application, applicants.get(application.getUserId())))
                .toList();
    }

    //운영자가 주최 신청을 승인·반려하고, 승인 시 주최자 권한을 부여
    //@Transactional을 의도적으로 걸지 않는다 — 승인 처리는 "PENDING → APPROVAL_PENDING 확정 저장 → auth-service 호출 → 성공 시 APPROVED 확정 저장" 순서라,
    //auth-service 호출이 실패해도 APPROVAL_PENDING 전이만은 그대로 커밋되어 있어야 나중에 같은 요청으로 안전하게 재시도할 수 있다.
    public HostApplicationResponseDto review(Long applicationId, String role, HostApplicationSetHostRequestDto request) {
        if (!ADMIN_ROLE.equals(role)) {
            throw new ApiException(HostApplicationErrorCode.FORBIDDEN_ADMIN_ROLE);
        }
        HostApplication application = hostApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ApiException(HostApplicationErrorCode.APPLICATION_NOT_FOUND));

        if (application.getStatus() == HostApplicationStatus.APPROVED
                || application.getStatus() == HostApplicationStatus.REJECTED) {
            throw new ApiException(HostApplicationErrorCode.ALREADY_REVIEWED);
        }

        if (request.getStatus() == HostApplicationStatus.APPROVED) {
            //PENDING이면 먼저 APPROVAL_PENDING으로 확정 저장한 뒤 auth-service에 Role 부여를 요청한다.
            //이미 APPROVAL_PENDING인 신청(= 이전 시도에서 Role 부여 응답을 못 받은 경우)은 같은 applicationId로 재시도만 한다.
            if (application.getStatus() == HostApplicationStatus.PENDING) {
                application.markApprovalPending();
                hostApplicationRepository.save(application);
            }
            try {
                setHostRole(application.getUserId(), application.getId());
                application.markApproved();
                hostApplicationRepository.save(application);
            } catch (RestClientException e) {
                // auth-service 응답 유실·Timeout — APPROVAL_PENDING 상태로 안전하게 남겨두고, 같은 신청을 다시 승인 요청하거나
                // retryPendingApprovals() 배치가 자동으로 재시도한다. 라이브에서 role은 부여됐는데 응답이 2초를 넘겨 여기서
                // 조용히 멈춘 사례가 있어(신청 #6) 반드시 로그를 남긴다.
                log.warn("주최자 Role 부여 응답을 받지 못해 신청 {}을 APPROVAL_PENDING으로 둔다(자동 재시도 예정)", application.getId(), e);
            }
        } else if (request.getStatus() == HostApplicationStatus.REJECTED) {
            //Role 부여 절차 중(APPROVAL_PENDING)인 신청은 반려할 수 없다(중복 요청 재확인 경로와 충돌 방지)
            if (application.getStatus() == HostApplicationStatus.APPROVAL_PENDING) {
                throw new ApiException(HostApplicationErrorCode.APPROVAL_PENDING_CANNOT_REJECT);
            }
            if (request.getRejectReason() == null || request.getRejectReason().isBlank()) {
                throw new ApiException(HostApplicationErrorCode.REJECT_REASON_REQUIRED);
            }
            application.reject(request.getRejectReason());
            hostApplicationRepository.save(application);
        } else {
            throw new ApiException(HostApplicationErrorCode.INVALID_DECISION);
        }
        return HostApplicationResponseDto.from(application);
    }

    //APPROVAL_PENDING에 머문 신청을 다시 승인 처리한다. Role 부여는 applicationId 멱등이라 이미 부여된 경우에도 안전하다.
    public int retryPendingApprovals(LocalDateTime olderThan) {
        int healed = 0;
        for (HostApplication application : hostApplicationRepository.findByStatusAndUpdatedAtBefore(
                HostApplicationStatus.APPROVAL_PENDING, olderThan)) {
            try {
                setHostRole(application.getUserId(), application.getId());
                application.markApproved();
                hostApplicationRepository.save(application);
                healed++;
                log.info("APPROVAL_PENDING 신청 {} 승인 확정(재시도 성공)", application.getId());
            } catch (RestClientException e) {
                log.warn("APPROVAL_PENDING 신청 {} 재시도 실패 — 다음 회차에 다시 시도", application.getId(), e);
            }
        }
        return healed;
    }

    //Festival-Service → Auth-Service 내부 호출: 승인된 신청자에게 HOST Role을 부여한다 (PUT /internal/v1/roles)
    //applicationId를 멱등키로 사용 — 같은 신청에 대해 재호출해도 auth-service 쪽에서 중복 부여되지 않아야 함
    private void setHostRole(Long userId, Long applicationId) {
        authServiceRestClient.put()
                .uri("/internal/v1/roles")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + internalAuthToken)
                .body(new GrantHostRoleRequest(userId, applicationId, HOST_ROLE))
                .retrieve()
                .toBodilessEntity();
    }
}
