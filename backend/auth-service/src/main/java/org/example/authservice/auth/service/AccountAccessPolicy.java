package org.example.authservice.auth.service;

import org.example.authservice.auth.exception.AuthErrorCode;
import org.example.authservice.common.exception.ApiException;
import org.example.authservice.helper.exception.HelperErrorCode;
import org.example.authservice.user.entity.AccountStatus;
import org.example.authservice.user.entity.Role;
import org.example.authservice.user.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 로그인·재발급·내 정보 조회·게이트웨이 세션 검증이 공유하는 계정 상태 정책.
 */
@Component
public class AccountAccessPolicy {

    @Value("${app.timezone:Asia/Seoul}")
    private String timezone = "Asia/Seoul";

    public LocalDateTime now() {
        return LocalDateTime.now(ZoneId.of(timezone));
    }

    public void check(User user) {
        if (user.getStatus() == AccountStatus.PENDING_ACTIVATION) {
            throw new ApiException(HelperErrorCode.HELPER_PENDING_ACTIVATION);
        }
        if (user.getStatus() == AccountStatus.REVOKED) {
            throw new ApiException(HelperErrorCode.INVITATION_REVOKED);
        }
        if (user.getStatus() == AccountStatus.SUSPENDED) {
            throw new ApiException(AuthErrorCode.ACCOUNT_SUSPENDED);
        }
        if (user.getStatus() == AccountStatus.WITHDRAWN) {
            throw new ApiException(AuthErrorCode.ACCOUNT_WITHDRAWN);
        }
        if (user.getStatus() != AccountStatus.ACTIVE) {
            throw new ApiException(AuthErrorCode.USER_NOT_FOUND);
        }
        if (user.getRole() == Role.HELPER
                && (user.getFestivalEndAt() == null || !user.getFestivalEndAt().isAfter(now()))) {
            throw new ApiException(HelperErrorCode.HELPER_FESTIVAL_ENDED);
        }
    }
}
