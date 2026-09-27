package org.example.authservice.admin.service;

import lombok.RequiredArgsConstructor;
import org.example.authservice.admin.dto.AdminUserResponse;
import org.example.authservice.admin.exception.AdminErrorCode;
import org.example.authservice.auth.entity.OauthAccount;
import org.example.authservice.auth.repository.OauthAccountRepository;
import org.example.authservice.auth.service.RefreshTokenRevocationService;
import org.example.authservice.common.exception.ApiException;
import org.example.authservice.user.entity.AccountStatus;
import org.example.authservice.user.entity.Role;
import org.example.authservice.user.entity.User;
import org.example.authservice.user.exception.UserErrorCode;
import org.example.authservice.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final OauthAccountRepository oauthAccountRepository;
    private final RefreshTokenRevocationService refreshTokenRevocationService;

    // 회원 목록 조회 (검색 + 권한 필터 + 상태 필터 + 페이징)
    @Transactional(readOnly = true)
    public Page<AdminUserResponse> getUsers(String role, String keyword, Role roleFilter,
                                            AccountStatus statusFilter, Pageable pageable) {
        checkAdmin(role);

        // 검색창이 비어 있으면 null로 바꿔서 "검색 조건 없음"으로 처리
        String searchKeyword = null;
        if (keyword != null && !keyword.isBlank()) {
            searchKeyword = keyword.trim();
        }

        Page<User> userPage = userRepository.searchForAdmin(searchKeyword, roleFilter, statusFilter, pageable);

        // 이번 페이지 회원들의 id 목록
        List<Long> userIds = new ArrayList<>();
        for (User user : userPage.getContent()) {
            userIds.add(user.getId());
        }

        // 회원 id별 가입 경로 목록 만들기 (쿼리 1번)
        Map<Long, List<String>> providersByUserId = new HashMap<>();
        if (!userIds.isEmpty()) {
            List<OauthAccount> accounts = oauthAccountRepository.findAllByUser_IdIn(userIds);
            for (OauthAccount account : accounts) {
                Long userId = account.getUser().getId();
                if (!providersByUserId.containsKey(userId)) {
                    providersByUserId.put(userId, new ArrayList<>());
                }
                providersByUserId.get(userId).add(account.getProvider());
            }
        }

        // User → AdminUserResponse 변환
        return userPage.map(user -> {
            AdminUserResponse response = new AdminUserResponse();
            response.setId(user.getId());
            response.setNickname(user.getNickname());
            response.setEmail(user.getUsername());
            response.setRole(user.getRole());
            response.setStatus(user.getStatus());
            response.setJoinedAt(user.getCreatedAt());
            response.setSuspendReason(user.getSuspendReason());
            response.setSuspendedAt(user.getSuspendedAt());

            // 탈퇴 회원은 소셜 연결이 삭제돼서 가입 경로를 알 수 없음 → null
            if (user.getStatus() == AccountStatus.WITHDRAWN) {
                response.setProviders(null);
            } else {
                response.setProviders(providersByUserId.getOrDefault(user.getId(), new ArrayList<>()));
            }
            return response;
        });
    }

    // 회원 정지
    @Transactional
    public void suspendUser(String role, Long adminId, Long targetUserId, String reason) {
        checkAdmin(role);

        if (adminId.equals(targetUserId)) {
            throw new ApiException(AdminErrorCode.CANNOT_SUSPEND_SELF);
        }

        User user = userRepository.findLockedById(targetUserId)
                .orElseThrow(() -> new ApiException(UserErrorCode.USER_NOT_FOUND));

        if (user.getRole() == Role.ADMIN) {
            throw new ApiException(AdminErrorCode.CANNOT_SUSPEND_ADMIN);
        }
        if (user.getRole() == Role.HELPER || user.getRole() == Role.STOREHOST) {
            throw new ApiException(AdminErrorCode.CANNOT_SUSPEND_MANAGED_ACCOUNT);
        }
        if (user.getStatus() != AccountStatus.ACTIVE) {
            throw new ApiException(AdminErrorCode.USER_NOT_ACTIVE);
        }

        user.setStatus(AccountStatus.SUSPENDED);
        user.setSuspendReason(reason.trim());
        user.setSuspendedAt(LocalDateTime.now(ZoneId.of("Asia/Seoul")));

        // 재발급을 막기 위해 refresh token 전부 폐기
        refreshTokenRevocationService.revokeAllTokens(user);
    }

    // 회원 정지 해제
    @Transactional
    public void unsuspendUser(String role, Long targetUserId) {
        checkAdmin(role);

        User user = userRepository.findLockedById(targetUserId)
                .orElseThrow(() -> new ApiException(UserErrorCode.USER_NOT_FOUND));

        if (user.getStatus() != AccountStatus.SUSPENDED) {
            throw new ApiException(AdminErrorCode.USER_NOT_SUSPENDED);
        }

        user.setStatus(AccountStatus.ACTIVE);
        user.setSuspendReason(null);
        user.setSuspendedAt(null);
    }

    // ADMIN 권한 체크 (gateway가 넘겨준 X-User-Role 헤더 값)
    private void checkAdmin(String role) {
        if (!Role.ADMIN.name().equals(role)) {
            throw new ApiException(UserErrorCode.FORBIDDEN_ADMIN_ROLE);
        }
    }
}