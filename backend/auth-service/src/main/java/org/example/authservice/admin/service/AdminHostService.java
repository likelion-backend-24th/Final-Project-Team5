package org.example.authservice.admin.service;

import lombok.RequiredArgsConstructor;
import org.example.authservice.common.exception.ApiException;
import org.example.authservice.admin.dto.AdminHostResponse;
import org.example.authservice.user.entity.AccountStatus;
import org.example.authservice.user.entity.Role;
import org.example.authservice.user.exception.UserErrorCode;
import org.example.authservice.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * 운영자에게 실제 HOST 계정 목록을 제공하고 역할 접근을 검증한다.
 */
@Service
@RequiredArgsConstructor
public class AdminHostService {

    private static final String ADMIN_ROLE = "ADMIN";

    private final UserRepository userRepository;

    public Page<AdminHostResponse> listHosts(String role, String keyword, AccountStatus status, Pageable pageable) {
        if (!ADMIN_ROLE.equals(role)) {
            throw new ApiException(UserErrorCode.FORBIDDEN_ADMIN_ROLE);
        }

        // 검색창이 비어 있으면 null로 바꿔서 "검색 조건 없음"으로 처리
        String searchKeyword = null;
        if (keyword != null && !keyword.isBlank()) {
            searchKeyword = keyword.trim();
        }

        // 회원 관리 검색 쿼리를 재사용하고, 권한만 HOST로 고정
        return userRepository.searchForAdmin(searchKeyword, Role.HOST, status, pageable)
                .map(AdminHostResponse::from);
    }
}
