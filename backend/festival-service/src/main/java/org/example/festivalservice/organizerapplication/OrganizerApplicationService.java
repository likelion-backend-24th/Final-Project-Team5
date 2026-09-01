package org.example.festivalservice.organizerapplication;

import lombok.RequiredArgsConstructor;
import org.example.festivalservice.common.ApiException;
import org.example.festivalservice.organizerapplication.dto.OrganizerApplicationResponse;
import org.example.festivalservice.organizerapplication.dto.OrganizerApplicationSubmitRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrganizerApplicationService {

    private static final String HOST_ROLE = "HOST";
    private static final String ALREADY_ORGANIZER = "ALREADY_ORGANIZER";
    private static final String DUPLICATE_APPLICATION = "DUPLICATE_APPLICATION";
    private static final String APPLICATION_NOT_FOUND = "APPLICATION_NOT_FOUND";

    private final OrganizerApplicationRepository organizerApplicationRepository;

    @Transactional
    public OrganizerApplicationResponse submit(Long userId, String role, OrganizerApplicationSubmitRequest request) {
        if (HOST_ROLE.equals(role)) {
            throw new ApiException(HttpStatus.CONFLICT, ALREADY_ORGANIZER, "이미 주최자 권한을 가지고 있습니다");
        }
        if (organizerApplicationRepository.existsByUserIdAndStatus(userId, OrganizerApplicationStatus.PENDING)) {
            throw new ApiException(HttpStatus.CONFLICT, DUPLICATE_APPLICATION, "이미 처리 대기 중인 신청이 있습니다");
        }

        OrganizerApplication application =
                new OrganizerApplication(userId, request.introduction(), request.contact());
        organizerApplicationRepository.save(application);
        return OrganizerApplicationResponse.from(application);
    }

    public OrganizerApplicationResponse getMy(Long userId) {
        OrganizerApplication application = organizerApplicationRepository
                .findFirstByUserIdOrderByCreatedAtDesc(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, APPLICATION_NOT_FOUND, "신청 내역이 없습니다"));
        return OrganizerApplicationResponse.from(application);
    }
}
