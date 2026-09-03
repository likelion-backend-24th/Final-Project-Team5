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
    public List<HostApplicationSubmitRequestDto> getListHostApplications(String role) {
        if (!ADMIN_ROLE.equals(role)) {
            throw new ApiException(HttpStatus.FORBIDDEN, FORBIDDEN_ROLE, "운영자 권한이 없습니다");
        }
        return hostApplicationRepository.findByStatus(HostApplicationStatus.PENDING);
    }

    //운영자가 주최 신청을 승인·반려하고, 승인 시 주최자 권한을 부여
    @Transactional
    public HostApplicationResponseDto review(Long applicationId, String role, HostApplicationSetHostRequestDto request) {
        if (!ADMIN_ROLE.equals(role)) {
            throw new ApiException(HttpStatus.FORBIDDEN, FORBIDDEN_ROLE, "운영자 권한이 없습니다");
        }
        HostApplication application = hostApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, APPLICATION_NOT_FOUND, "존재하지 않는 신청입니다"));
        if (application.getStatus() != HostApplicationStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, ALREADY_REVIEWED, "이미 처리된 신청입니다");
        }

        if (request.getStatus() == HostApplicationStatus.APPROVED) {
            application.approve();
            setHostRole(application.getUserId(), application.getId());
        } else if (request.getStatus() == HostApplicationStatus.REJECTED) {
            if (request.getRejectReason() == null || request.getRejectReason().isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, REJECT_REASON_REQUIRED, "반려 사유는 필수입니다");
            }
            application.reject(request.getRejectReason());
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
