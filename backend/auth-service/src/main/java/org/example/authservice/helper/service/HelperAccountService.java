package org.example.authservice.helper.service;

import lombok.RequiredArgsConstructor;
import org.example.authservice.auth.service.RefreshTokenRevocationService;
import org.example.authservice.common.exception.ApiException;
import org.example.authservice.helper.dto.CreateHelperAccountRequest;
import org.example.authservice.helper.dto.HelperAccountCredentialResponse;
import org.example.authservice.helper.dto.HelperAccountSummaryResponse;
import org.example.authservice.helper.exception.HelperErrorCode;
import org.example.authservice.user.entity.AccountStatus;
import org.example.authservice.user.entity.Role;
import org.example.authservice.user.entity.User;
import org.example.authservice.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 주최자가 현장 입장 검증을 맡길 도우미 계정을 발급·재발급·조회한다.
 * 호스트 소유권 검증은 festival-service가 끝낸 뒤 내부 호출로 넘어오므로 여기서는 계정 자체만 다룬다.
 */
@Service
@RequiredArgsConstructor
public class HelperAccountService {

    private static final String HELPER_NAME = "현장 도우미";
    //아이디·닉네임이 우연히 겹쳤을 때 다시 뽑는 횟수. 31^8 조합이라 실제로는 첫 시도에서 끝난다.
    private static final int MAX_GENERATION_ATTEMPTS = 5;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final HelperCredentialGenerator credentialGenerator;
    private final RefreshTokenRevocationService refreshTokenRevocationService;

    //호출할 때마다 새 계정을 하나씩 발급한다(멱등하지 않음 — 호스트가 필요한 만큼 반복 호출한다).
    //평문 비밀번호는 이 응답에서만 노출되고 DB에는 해시만 남는다.
    @Transactional
    public HelperAccountCredentialResponse createHelperAccount(CreateHelperAccountRequest request) {
        String suffix = generateUnusedSuffix();
        String rawPassword = credentialGenerator.generatePassword();

        User helper = new User();
        helper.setName(HELPER_NAME);
        helper.setUsername(credentialGenerator.generateUsername(suffix));
        helper.setNickname(credentialGenerator.generateNickname(suffix));
        helper.setPassword(passwordEncoder.encode(rawPassword));
        helper.setRole(Role.HELPER);
        helper.setStatus(AccountStatus.ACTIVE);
        helper.setFestivalId(request.festivalId());
        helper.setFestivalEndAt(request.festivalEndAt());
        userRepository.save(helper);

        return new HelperAccountCredentialResponse(helper.getId(), helper.getUsername(), rawPassword);
    }

    //호스트가 발급 당시 비밀번호를 놓쳤을 때 쓰는 재발급 — 계정은 그대로 두고 비밀번호만 새로 만든다.
    @Transactional
    public HelperAccountCredentialResponse reissuePassword(Long festivalId, Long helperUserId) {
        User helper = getHelperOfFestival(festivalId, helperUserId);

        String rawPassword = credentialGenerator.generatePassword();
        helper.setPassword(passwordEncoder.encode(rawPassword));
        userRepository.save(helper);

        //이전 비밀번호로 로그인해 둔 세션이 남아있지 않도록 정리한다(계정을 다른 알바에게 넘기는 상황 대비).
        refreshTokenRevocationService.revokeAllTokens(helper);

        return new HelperAccountCredentialResponse(helper.getId(), helper.getUsername(), rawPassword);
    }

    //호스트가 몇 개의 계정을 발급했는지 확인한다. 비밀번호는 복원할 수 없으므로 담지 않는다.
    public HelperAccountSummaryResponse listHelperAccounts(Long festivalId) {
        List<HelperAccountSummaryResponse.HelperAccount> helpers =
                userRepository.findByRoleAndFestivalIdAndStatus(Role.HELPER, festivalId, AccountStatus.ACTIVE).stream()
                        .map(user -> new HelperAccountSummaryResponse.HelperAccount(
                                user.getId(), user.getUsername(), user.getCreatedAt()))
                        .toList();

        return new HelperAccountSummaryResponse(helpers.size(), helpers);
    }

    //페스티벌 종료 후 유예시간이 지난 도우미 계정을 일괄 탈퇴 처리한다. 배치가 호출한다.
    @Transactional
    public int withdrawExpiredHelperAccounts(LocalDateTime revokeThreshold) {
        List<User> expiredHelpers = userRepository.findByRoleAndStatusAndFestivalEndAtBefore(
                Role.HELPER, AccountStatus.ACTIVE, revokeThreshold);

        for (User helper : expiredHelpers) {
            helper.setStatus(AccountStatus.WITHDRAWN);
            helper.setWithdrawnAt(LocalDateTime.now());
            userRepository.save(helper);
            refreshTokenRevocationService.revokeAllTokens(helper);
        }

        return expiredHelpers.size();
    }

    //재발급 대상이 정말 그 페스티벌의 도우미인지 확인한다(다른 페스티벌 계정을 건드리지 못하게).
    private User getHelperOfFestival(Long festivalId, Long helperUserId) {
        User helper = userRepository.findById(helperUserId)
                .orElseThrow(() -> new ApiException(HelperErrorCode.HELPER_ACCOUNT_NOT_FOUND));
        if (helper.getRole() != Role.HELPER) {
            throw new ApiException(HelperErrorCode.HELPER_ACCOUNT_NOT_FOUND);
        }
        if (!festivalId.equals(helper.getFestivalId())) {
            throw new ApiException(HelperErrorCode.FORBIDDEN_HELPER_FESTIVAL);
        }
        return helper;
    }

    private String generateUnusedSuffix() {
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            String suffix = credentialGenerator.generateSuffix();
            boolean taken = userRepository.existsByUsername(credentialGenerator.generateUsername(suffix))
                    || userRepository.existsByNickname(credentialGenerator.generateNickname(suffix));
            if (!taken) {
                return suffix;
            }
        }
        throw new ApiException(HelperErrorCode.HELPER_ACCOUNT_GENERATION_FAILED);
    }
}
