package org.example.authservice.role.service;

import lombok.RequiredArgsConstructor;
import org.example.authservice.auth.exception.AuthErrorCode;
import org.example.authservice.common.exception.ApiException;
import org.example.authservice.role.dto.GrantRoleRequest;
import org.example.authservice.role.entity.RoleGrant;
import org.example.authservice.role.exception.RoleErrorCode;
import org.example.authservice.role.repository.RoleGrantRepository;
import org.example.authservice.user.entity.Role;
import org.example.authservice.user.entity.User;
import org.example.authservice.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final UserRepository userRepository;
    private final RoleGrantRepository roleGrantRepository;

    //Festival-Service가 같은 applicationId로 재요청해도(응답 유실 뒤 재시도 포함) Role을 다시 부여하지 않는다
    @Transactional
    public void grantRole(GrantRoleRequest request) {
        if (roleGrantRepository.findByApplicationId(request.applicationId()).isPresent()) {
            return; // 이미 처리된 요청 — 멱등하게 성공으로 간주
        }

        Role role;
        try {
            role = Role.valueOf(request.role());
        } catch (IllegalArgumentException e) {
            throw new ApiException(RoleErrorCode.INVALID_ROLE);
        }

        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ApiException(RoleErrorCode.USER_NOT_FOUND));
        user.setRole(role);
        userRepository.save(user);

        roleGrantRepository.save(new RoleGrant(request.applicationId(), request.userId(), request.role()));
    }
}
