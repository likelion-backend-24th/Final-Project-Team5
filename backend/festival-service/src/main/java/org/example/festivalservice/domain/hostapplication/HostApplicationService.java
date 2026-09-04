package org.example.festivalservice.domain.hostapplication;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.festivalservice.common.ApiException;
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
    private static final String ALREADY_HOST = "ALREADY_HOST";
    private static final String DUPLICATE_APPLICATION = "DUPLICATE_APPLICATION";
    private static final String APPLICATION_NOT_FOUND = "APPLICATION_NOT_FOUND";
    private static final String FORBIDDEN_ROLE = "FORBIDDEN_ROLE";
    private static final String ALREADY_REVIEWED = "ALREADY_REVIEWED";
    private static final String INVALID_DECISION = "INVALID_DECISION";
    private static final String REJECT_REASON_REQUIRED = "REJECT_REASON_REQUIRED";

    private final HostApplicationRepository hostApplicationRepository;
    private final RestClient authServiceRestClient;

    @Value("${internal.auth-service.token:CHANGE_ME_IN_ENV}")
    private String internalAuthToken;

    //페스티벌 주최자가 되기 위한 신청을 제출할 때
    @Transactional
    public HostApplicationResponseDto submit(Long userId, String role, HostApplicationSubmitRequestDto request) {
        if (ADMIN_ROLE.equals(role)) {
            throw new ApiException(HttpStatus.FORBIDDEN, FORBIDDEN_ROLE, "운영자는 주최자 신청을 할 수 없습니다");
        }
        if (HOST_ROLE.equals(role)) {
            throw new ApiException(HttpStatus.CONFLICT, ALREADY_HOST, "이미 주최자 권한을 가지고 있습니다");
        }
        if (hostApplicationRepository.existsByUserIdAndStatus(userId, HostApplicationStatus.PENDING)) {
            throw new ApiException(HttpStatus.CONFLICT, DUPLICATE_APPLICATION, "이미 처리 대기 중인 신청이 있습니다");
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
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, APPLICATION_NOT_FOUND, "신청 내역이 없습니다"));
        return HostApplicationResponseDto.from(application);
    }

    //운영자가 심사 대기 중인 주최 신청 목록을 조회
    public List<HostApplicationResponseDto> getListHostApplications(String role) {
        if (!ADMIN_ROLE.equals(role)) {
            throw new ApiException(HttpStatus.FORBIDDEN, FORBIDDEN_ROLE, "운영자 권한이 없습니다");
        }
        return hostApplicationRepository.findByStatus(HostApplicationStatus.PENDING).stream()
                .map(HostApplicationResponseDto::from)
                .toList();
    }

    //운영자가 주최 신청을 승인·반려하고, 승인 시 주최자 권한을 부여
    //@Transactional을 의도적으로 걸지 않는다 — 승인 처리는 "PENDING → APPROVAL_PENDING 확정 저장 → auth-service 호출 → 성공 시 APPROVED 확정 저장" 순서라,
    //auth-service 호출이 실패해도 APPROVAL_PENDING 전이만은 그대로 커밋되어 있어야 나중에 같은 요청으로 안전하게 재시도할 수 있다.
    public HostApplicationResponseDto review(Long applicationId, String role, HostApplicationSetHostRequestDto request) {
        if (!ADMIN_ROLE.equals(role)) {
            throw new ApiException(HttpStatus.FORBIDDEN, FORBIDDEN_ROLE, "운영자 권한이 없습니다");
        }
        HostApplication application = hostApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, APPLICATION_NOT_FOUND, "존재하지 않는 신청입니다"));

        if (application.getStatus() == HostApplicationStatus.APPROVED
                || application.getStatus() == HostApplicationStatus.REJECTED) {
            throw new ApiException(HttpStatus.CONFLICT, ALREADY_REVIEWED, "이미 처리된 신청입니다");
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
                // auth-service 응답 유실·Timeout — APPROVAL_PENDING 상태로 안전하게 남겨두고, 같은 신청을 다시 승인 요청하면 재시도된다
            }
        } else if (request.getStatus() == HostApplicationStatus.REJECTED) {
            //Role 부여 절차 중(APPROVAL_PENDING)인 신청은 반려할 수 없다(중복 요청 재확인 경로와 충돌 방지)
            if (application.getStatus() == HostApplicationStatus.APPROVAL_PENDING) {
                throw new ApiException(HttpStatus.CONFLICT, ALREADY_REVIEWED, "Role 부여 처리 중인 신청은 반려할 수 없습니다");
            }
            if (request.getRejectReason() == null || request.getRejectReason().isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, REJECT_REASON_REQUIRED, "반려 사유는 필수입니다");
            }
            application.reject(request.getRejectReason());
            hostApplicationRepository.save(application);
        } else {
            throw new ApiException(HttpStatus.BAD_REQUEST, INVALID_DECISION, "승인 또는 반려만 결정할 수 있습니다");
        }
        return HostApplicationResponseDto.from(application);
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
