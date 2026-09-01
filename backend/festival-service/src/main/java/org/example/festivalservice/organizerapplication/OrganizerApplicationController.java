package org.example.festivalservice.organizerapplication;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.festivalservice.common.ApiResponse;
import org.example.festivalservice.organizerapplication.dto.OrganizerApplicationResponse;
import org.example.festivalservice.organizerapplication.dto.OrganizerApplicationSubmitRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/organizer-applications")
@RequiredArgsConstructor
public class OrganizerApplicationController {

    private final OrganizerApplicationService organizerApplicationService;

    @PostMapping
    public ResponseEntity<ApiResponse<OrganizerApplicationResponse>> submit(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody OrganizerApplicationSubmitRequest request
    ) {
        OrganizerApplicationResponse response = organizerApplicationService.submit(userId, role, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<OrganizerApplicationResponse>> getMy(
            @RequestHeader("X-User-Id") Long userId
    ) {
        OrganizerApplicationResponse response = organizerApplicationService.getMy(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
