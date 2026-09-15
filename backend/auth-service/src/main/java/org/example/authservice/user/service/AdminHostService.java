package org.example.authservice.user.service;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.example.authservice.common.exception.ApiException;
import org.example.authservice.user.dto.AdminHostResponse;
import org.example.authservice.user.entity.Role;
import org.example.authservice.user.exception.UserErrorCode;
import org.example.authservice.user.repository.UserRepository;
import org.springframework.stereotype.Service;

/**
 * 운영자에게 실제 HOST 계정 목록을 제공하고 역할 접근을 검증한다.
 */
@Service
@RequiredArgsConstructor
public class AdminHostService {

    private static final String ADMIN_ROLE = "ADMIN";

    private final UserRepository userRepository;

    public List<AdminHostResponse> listHosts(String role) {
        if (!ADMIN_ROLE.equals(role)) {
            throw new ApiException(UserErrorCode.FORBIDDEN_ADMIN_ROLE);
        }
        return userRepository.findByRoleOrderByCreatedAtDesc(Role.HOST).stream()
                .map(AdminHostResponse::from)
                .toList();
    }
}
