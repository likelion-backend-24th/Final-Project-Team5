package org.example.festivalservice.domain.hostapplication;

import lombok.RequiredArgsConstructor;
import org.example.festivalservice.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HostApplicationService {

    private static final String HOST_ROLE = "HOST";
    private static final String ALREADY_HOST = "ALREADY_HOST";
    private static final String DUPLICATE_APPLICATION = "DUPLICATE_APPLICATION";
    private static final String APPLICATION_NOT_FOUND = "APPLICATION_NOT_FOUND";

    private final HostApplicationRepository hostApplicationRepository;

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
}
