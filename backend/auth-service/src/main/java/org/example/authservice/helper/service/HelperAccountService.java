package org.example.authservice.helper.service;

import lombok.RequiredArgsConstructor;
import org.example.authservice.auth.dto.PasswordPolicy;
import org.example.authservice.auth.dto.TokenResponse;
import org.example.authservice.auth.repository.RefreshTokenRepository;
import org.example.authservice.auth.service.AccountAccessPolicy;
import org.example.authservice.auth.service.EmailService;
import org.example.authservice.auth.service.TokenSessionService;
import org.example.authservice.common.exception.ApiException;
import org.example.authservice.helper.dto.AcceptHelperInvitationRequest;
import org.example.authservice.helper.dto.CreateHelperAccountRequest;
import org.example.authservice.helper.dto.HelperAccountSummaryResponse;
import org.example.authservice.helper.dto.HelperInvitationInfo;
import org.example.authservice.helper.entity.HelperInvitation.DeliveryStatus;
import org.example.authservice.helper.entity.HelperInvitation.Status;
import org.example.authservice.helper.entity.HelperInvitation;
import org.example.authservice.helper.exception.HelperErrorCode;
import org.example.authservice.helper.repository.HelperInvitationRepository;
import org.example.authservice.user.entity.AccountStatus;
import org.example.authservice.user.entity.Role;
import org.example.authservice.user.entity.User;
import org.example.authservice.user.exception.UserErrorCode;
import org.example.authservice.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 도우미 계정과 초대의 수명을 관리한다.
 * 호스트 소유권 검증은 festival-service가 끝낸 뒤 내부 호출로 넘어오므로 여기서는 계정 자체만 다룬다.
 */
@Service
@RequiredArgsConstructor
public class HelperAccountService {

    private final UserRepository userRepository;

    private final HelperInvitationRepository invitations;

    private final HelperIdentityGenerator identityGenerator;

    private final PasswordEncoder passwordEncoder;

    private final RefreshTokenRepository refreshTokens;

    private final TokenSessionService sessions;

    private final EmailService emailService;

    private final AccountAccessPolicy policy;

    private final PlatformTransactionManager transactionManager;

    private final SecureRandom random = new SecureRandom();

    @Value("${app.frontend-url}")
    private String frontendUrl;

    // 외부 트랜잭션의 롤백으로 이미 보낸 링크가 사라지지 않도록 REQUIRES_NEW로 먼저 확정한다.
    // SMTP는 이 트랜잭션이 끝난 뒤 실행하고, 실패 상태도 독립적으로 커밋해 재발송할 수 있게 한다.
    private <T> T transaction(Supplier<T> action) {
        return transaction(action, false);
    }

    private <T> T transaction(Supplier<T> action, boolean readOnly) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setReadOnly(readOnly);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return tx.execute(status -> action.get());
    }

    // 활성화 링크를 받은 본인만 비밀번호를 정하도록 null 비밀번호와 PENDING_ACTIVATION 상태로 만든다.
    public HelperAccountSummaryResponse.HelperAccount createHelperAccount(CreateHelperAccountRequest request) {
        PendingMail mail;
        try {
            mail = transaction(() -> {
                checkFestival(request.festivalEndAt());
                rejectDuplicate(request);
                String suffix = unusedSuffix();
                User helper = new User();
                helper.setName("현장 도우미");
                helper.setUsername(identityGenerator.generateUsername(suffix));
                helper.setNickname(identityGenerator.generateNickname(suffix));
                helper.setRole(Role.HELPER);
                helper.setStatus(AccountStatus.PENDING_ACTIVATION);
                helper.setFestivalId(request.festivalId());
                helper.setFestivalEndAt(request.festivalEndAt());
                helper.setHelperSessionVersion(0L);
                userRepository.save(helper);
                return prepare(newInvitation(helper, request), false);
            });
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(HelperErrorCode.INVITATION_DUPLICATE);
        }
        return deliver(mail);
    }

    public HelperAccountSummaryResponse.HelperAccount convertLegacy(Long helperId, CreateHelperAccountRequest request) {
        PendingMail mail;
        try {
            mail = transaction(() -> {
                User helper = getLockedHelper(request.festivalId(), helperId);
                checkFestival(helper.getFestivalEndAt());
                policy.check(helper);
                if (invitations.findByHelperUser_Id(helperId).isPresent()) {
                    throw new ApiException(HelperErrorCode.INVITATION_DUPLICATE);
                }
                rejectDuplicate(request);
                return prepare(newInvitation(helper, request), false);
            });
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(HelperErrorCode.INVITATION_DUPLICATE);
        }
        return deliver(mail);
    }

    // 재발송은 digest를 교체하므로 전달이 늦어진 이전 메일의 링크를 다시 사용할 수 없다.
    public HelperAccountSummaryResponse.HelperAccount resend(Long festivalId, Long helperId) {
        return deliver(transaction(() -> {
            User helper = getLockedHelper(festivalId, helperId);
            checkFestival(helper.getFestivalEndAt());
            HelperInvitation invitation = invitationOf(helperId);
            if (helper.getStatus() == AccountStatus.REVOKED || invitation.getStatus() == Status.REVOKED) {
                throw new ApiException(HelperErrorCode.INVITATION_REVOKED);
            }
            if (invitation.getStatus() == Status.ACCEPTED) {
                throw new ApiException(HelperErrorCode.INVITATION_ACCEPTED);
            }
            return prepare(invitation, true);
        }));
    }

    public HelperAccountSummaryResponse.HelperAccount revoke(Long festivalId, Long helperId) {
        return transaction(() -> {
            User helper = getLockedHelper(festivalId, helperId);
            helper.setStatus(AccountStatus.REVOKED);
            // 같은 트랜잭션에서 세션 버전을 먼저 올리고 refresh token을 폐기해 모든 기존 세션을 끊는다.
            invalidateSessions(helper);
            invitations.findByHelperUser_Id(helperId).ifPresent(invitation -> {
                invitation.setStatus(Status.REVOKED);
                invitation.setRevokedAt(policy.now());
            });
            return summary(helper);
        });
    }

    public HelperAccountSummaryResponse listHelperAccounts(Long festivalId) {
        return transaction(() -> {
            var helpers = userRepository.findByRoleAndFestivalId(Role.HELPER, festivalId).stream()
                    .map(this::summary)
                    .toList();
            return new HelperAccountSummaryResponse(helpers.size(), helpers);
        }, true);
    }

    public HelperInvitationInfo inspect(String token) {
        return transaction(() -> {
            HelperInvitation invitation = byToken(token);
            validate(invitation);
            User helper = invitation.getHelperUser();
            String email = invitation.getNormalizedEmail();
            return new HelperInvitationInfo(
                    helper.getUsername(), invitation.getFestivalId(), invitation.getFestivalName(),
                    invitation.getFestivalStartAt(), helper.getFestivalEndAt(), invitation.getExpiresAt(),
                    email.substring(0, 1) + "***@" + email.substring(email.lastIndexOf('@') + 1));
        });
    }

    public TokenResponse accept(String token, AcceptHelperInvitationRequest request, String previousRefreshToken) {
        if (!request.password().equals(request.passwordConfirm())) {
            throw new ApiException(UserErrorCode.PASSWORD_CONFIRM_MISMATCH);
        }
        // BCrypt는 72바이트 제한이 있으므로 다국어 비밀번호도 안전하게 검증한다.
        if (request.password().length() < PasswordPolicy.MIN_LENGTH
                || request.password().getBytes(StandardCharsets.UTF_8).length > PasswordPolicy.MAX_LENGTH) {
            throw new ApiException(HelperErrorCode.INVITATION_PASSWORD_INVALID);
        }
        // 토큰을 먼저 검증해 유효하지 않은 요청으로 BCrypt 연산을 유발하지 않게 한다.
        Long helperId = transaction(() -> {
            HelperInvitation invitation = byToken(token);
            validate(invitation);
            return invitation.getHelperUser().getId();
        });
        return transaction(() -> {
            User helper = userRepository.findLockedById(helperId)
                .orElseThrow(() -> new ApiException(HelperErrorCode.INVITATION_INVALID));
            // 잠금을 얻은 후 토큰을 다시 읽어 수락/재발송/해지 경합을 검증한다.
            HelperInvitation invitation = byToken(token);
            validate(invitation);
            helper.setPassword(passwordEncoder.encode(request.password()));
            helper.setStatus(AccountStatus.ACTIVE);
            helper.setFailedLoginAttempts(0);
            helper.setLockedUntil(null);
            invalidateSessions(helper);
            invitation.setStatus(Status.ACCEPTED);
            invitation.setAcceptedAt(policy.now());
            if (previousRefreshToken != null) {
                refreshTokens.findByTokenHash(TokenSessionService.hashToken(previousRefreshToken))
                        .ifPresent(previous -> previous.setRevokedAt(LocalDateTime.now()));
            }
            return sessions.issue(helper);
        });
    }

    // Gateway가 HELPER 보호 API 요청마다 호출하여 JWT 발급 뒤의 해지·활성화·행사 종료를 반영한다.
    public void validateSession(Long userId, Long festivalId, long version) {
        transaction(() -> {
            User helper = userRepository.findById(userId)
                    .orElseThrow(() -> new ApiException(HelperErrorCode.HELPER_SESSION_REVOKED));
            if (helper.getRole() != Role.HELPER || !Objects.equals(festivalId, helper.getFestivalId())
                    || version != (helper.getHelperSessionVersion() == null ? 0L : helper.getHelperSessionVersion())) {
                throw new ApiException(HelperErrorCode.HELPER_SESSION_REVOKED);
            }
            policy.check(helper);
            return null;
        });
    }

    // 행사 종료 후 유예시간이 지난 계정은 개인정보를 계속 보관하지 않도록 상태와 무관하게 삭제한다.
    public int deleteExpiredHelperAccounts(LocalDateTime threshold) {
        return transaction(() -> {
            var helpers = userRepository.findByRoleAndFestivalEndAtBefore(Role.HELPER, threshold);
            for (User candidate : helpers) {
                User helper = userRepository.findLockedById(candidate.getId()).orElse(null);
                if (helper == null) {
                    continue;
                }
                // FK 때문에 발송 이력(초대의 컬렉션) → 초대 → refresh token → user 순서로 삭제한다.
                invitations.deleteAllByHelperUser_Id(helper.getId());
                invitations.flush();
                refreshTokens.deleteAllByUser_Id(helper.getId());
                refreshTokens.flush();
                userRepository.delete(helper);
            }
            return helpers.size();
        });
    }

    private HelperInvitation newInvitation(User helper, CreateHelperAccountRequest request) {
        HelperInvitation invitation = new HelperInvitation();
        invitation.setHelperUser(helper);
        invitation.setFestivalId(helper.getFestivalId());
        invitation.setFestivalName(request.festivalName());
        invitation.setFestivalStartAt(request.festivalStartAt());
        invitation.setNormalizedEmail(request.email());
        return invitation;
    }

    private PendingMail prepare(HelperInvitation invitation, boolean resend) {
        LocalDateTime now = policy.now();
        // 최초 발송과 실패도 24시간 10회에 포함해 반복 요청을 통한 메일 남용을 막는다.
        // 최근 시도 후 60초 간격을 두어 중복 클릭이나 짧은 재시도가 수신함을 채우지 않게 한다.
        var attempts = invitation.getSendAttempts();
        attempts.removeIf(time -> !time.isAfter(now.minusHours(24)));
        if (attempts.stream().anyMatch(time -> time.isAfter(now.minusSeconds(60)))) {
            throw new ApiException(HelperErrorCode.INVITATION_COOLDOWN);
        }
        if (attempts.size() >= 10) {
            throw new ApiException(HelperErrorCode.INVITATION_SEND_LIMIT);
        }
        attempts.add(now);
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        invitation.setTokenHash(TokenSessionService.hashToken(token));
        LocalDateTime end = invitation.getHelperUser().getFestivalEndAt();
        invitation.setExpiresAt(end.isBefore(now.plusHours(24)) ? end : now.plusHours(24));
        invitation.setStatus(Status.PENDING);
        invitation.setDeliveryStatus(DeliveryStatus.SENDING);
        if (resend) {
            invitation.setResendCount(invitation.getResendCount() + 1);
        }
        invitations.saveAndFlush(invitation);
        return new PendingMail(
                invitation.getHelperUser().getId(), invitation.getFestivalId(), invitation.getTokenHash(),
                invitation.getNormalizedEmail(), invitation.getFestivalName(), invitation.getHelperUser().getUsername(),
                invitation.getExpiresAt(), token);
    }

    private HelperAccountSummaryResponse.HelperAccount deliver(PendingMail mail) {
        boolean sent;
        try {
            emailService.sendHelperInvitation(mail.email(), mail.festivalName(), mail.username(), mail.expiresAt(),
                frontendUrl.replaceAll("/+$", "") + "/helper-invite/" + mail.token());
            sent = true;
        } catch (RuntimeException e) {
            sent = false;
        }
        final boolean delivered = sent;
        var result = transaction(() -> {
            User helper = getLockedHelper(mail.festivalId(), mail.helperId());
            HelperInvitation invitation = invitationOf(mail.helperId());
            // 느린 이전 SMTP 요청이 새 초대의 발송 상태를 덮어쓰지 않는다.
            if (invitation.getTokenHash().equals(mail.hash())) {
                invitation.setDeliveryStatus(delivered ? DeliveryStatus.SENT : DeliveryStatus.SEND_FAILED);
                if (delivered) {
                    if (invitation.getSentAt() == null) {
                        invitation.setSentAt(policy.now());
                    }
                    invitation.setLastSentAt(policy.now());
                }
            }
            return summary(helper);
        });
        if (!sent) {
            throw new ApiException(HelperErrorCode.INVITATION_SEND_FAILED);
        }
        return result;
    }

    private void validate(HelperInvitation invitation) {
        if (invitation.getStatus() == Status.REVOKED
                || invitation.getHelperUser().getStatus() == AccountStatus.REVOKED) {
            throw new ApiException(HelperErrorCode.INVITATION_REVOKED);
        }
        if (invitation.getStatus() == Status.ACCEPTED) {
            throw new ApiException(HelperErrorCode.INVITATION_ACCEPTED);
        }
        checkFestival(invitation.getHelperUser().getFestivalEndAt());
        if (!invitation.getExpiresAt().isAfter(policy.now())) {
            throw new ApiException(HelperErrorCode.INVITATION_EXPIRED);
        }
    }

    private void checkFestival(LocalDateTime end) {
        if (end == null || !end.isAfter(policy.now())) {
            throw new ApiException(HelperErrorCode.HELPER_FESTIVAL_ENDED);
        }
    }

    private void rejectDuplicate(CreateHelperAccountRequest request) {
        if (invitations.existsByFestivalIdAndNormalizedEmail(request.festivalId(), request.email())) {
            throw new ApiException(HelperErrorCode.INVITATION_DUPLICATE);
        }
    }

    private HelperInvitation byToken(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
            throw new ApiException(HelperErrorCode.INVITATION_INVALID);
        }
        return invitations.findByTokenHash(TokenSessionService.hashToken(token))
                .orElseThrow(() -> new ApiException(HelperErrorCode.INVITATION_INVALID));
    }

    private HelperInvitation invitationOf(Long helperId) {
        return invitations.findByHelperUser_Id(helperId)
                .orElseThrow(() -> new ApiException(HelperErrorCode.INVITATION_INVALID));
    }

    private User getLockedHelper(Long festivalId, Long helperId) {
        User helper = userRepository.findLockedById(helperId)
                    .orElseThrow(() -> new ApiException(HelperErrorCode.HELPER_ACCOUNT_NOT_FOUND));
        if (helper.getRole() != Role.HELPER) {
            throw new ApiException(HelperErrorCode.HELPER_ACCOUNT_NOT_FOUND);
        }
        if (!Objects.equals(helper.getFestivalId(), festivalId)) {
            throw new ApiException(HelperErrorCode.FORBIDDEN_HELPER_FESTIVAL);
        }
        return helper;
    }

    private void invalidateSessions(User helper) {
        helper.setPasswordChangedAt(LocalDateTime.now());
        long version = helper.getHelperSessionVersion() == null ? 0L : helper.getHelperSessionVersion();
        helper.setHelperSessionVersion(version + 1);
        sessions.revokeAll(helper);
    }

    private HelperAccountSummaryResponse.HelperAccount summary(User helper) {
        HelperInvitation invitation = invitations.findByHelperUser_Id(helper.getId()).orElse(null);
        String status = resolveStatus(helper, invitation);
        return new HelperAccountSummaryResponse.HelperAccount(helper.getId(), helper.getUsername(),
                invitation == null ? null : invitation.getNormalizedEmail(), status,
                invitation == null ? null : invitation.getDeliveryStatus().name(),
                invitation == null ? null : invitation.getSentAt(), invitation == null ? null : invitation.getLastSentAt(),
                invitation == null ? null : invitation.getExpiresAt(), helper.getCreatedAt(), invitation == null);
    }

    // 만료는 expiresAt으로 판정해 응답에서만 EXPIRED로 표시한다.
    private String resolveStatus(User helper, HelperInvitation invitation) {
        String status;
        if (helper.getStatus() == AccountStatus.REVOKED) {
            status = "REVOKED";
        } else if (invitation == null) {
            status = "ACTIVE";
        } else {
            status = invitation.getStatus().name();
        }
        if (invitation != null && invitation.getStatus() == Status.PENDING
                && !invitation.getExpiresAt().isAfter(policy.now())) {
            return "EXPIRED";
        }
        return status;
    }

    // 아이디·닉네임 충돌 시 최대 5번 다시 뽑는다. 현재 문자 집합은 31^8 조합이라 대개 첫 시도에 끝난다.
    private String unusedSuffix() {
        for (int i = 0; i < 5; i++) {
            String suffix = identityGenerator.generateSuffix();
            if (!userRepository.existsByUsername(identityGenerator.generateUsername(suffix))
                    && !userRepository.existsByNickname(identityGenerator.generateNickname(suffix))) {
                return suffix;
            }
        }
        throw new ApiException(HelperErrorCode.HELPER_ACCOUNT_GENERATION_FAILED);
    }

    private record PendingMail(
            Long helperId,
            Long festivalId,
            String hash,
            String email,
            String festivalName,
            String username,
            LocalDateTime expiresAt,
            String token
    ) {
        @Override
        public String toString() {
            return "PendingMail[REDACTED]";
        }
    }
}
