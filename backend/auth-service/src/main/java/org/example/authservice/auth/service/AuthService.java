package org.example.authservice.auth.service;

import lombok.RequiredArgsConstructor;
import org.example.authservice.auth.dto.LoginRequest;
import org.example.authservice.auth.dto.SignupRequest;
import org.example.authservice.auth.dto.TokenResponse;
import org.example.authservice.auth.dto.oauth.GoogleUserInfoResponse;
import org.example.authservice.auth.dto.oauth.KakaoUserInfoResponse;
import org.example.authservice.auth.entity.OauthAccount;
import org.example.authservice.auth.entity.RefreshToken;
import org.example.authservice.auth.exception.AuthErrorCode;
import org.example.authservice.auth.repository.OauthAccountRepository;
import org.example.authservice.auth.repository.RefreshTokenRepository;
import org.example.authservice.auth.security.JwtTokenProvider;
import org.example.authservice.common.exception.ApiException;
import org.example.authservice.user.entity.AccountStatus;
import org.example.authservice.user.entity.Role;
import org.example.authservice.user.entity.User;
import org.example.authservice.user.exception.UserErrorCode;
import org.example.authservice.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenRevocationService refreshTokenRevocationService;
    private final EmailVerificationService emailVerificationService;
    private final LoginAttemptService loginAttemptService;
    private final KakaoApiClient kakaoApiClient;
    private final GoogleApiClient googleApiClient;
    private final OauthAccountRepository oauthAccountRepository;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    //회원가입
    @Transactional
    public void signup(SignupRequest signupRequest) {
        Optional<User> existingUser = userRepository.findByUsername(signupRequest.getUsername());

        // 유저가 존재하고 비밀번호도 갖고있으면 중복으로 회원가입 불가
        if(existingUser.isPresent() && existingUser.get().getPassword() != null){
            throw new ApiException(AuthErrorCode.DUPLICATE_USERNAME);
        }

        //닉네임중복 검증
        if (userRepository.existsByNickname(signupRequest.getNickname())) {
            throw new ApiException(AuthErrorCode.DUPLICATE_NICKNAME);
        }
        if (!signupRequest.isTermsAgreed()) {
            throw new ApiException(AuthErrorCode.TERMS_NOT_AGREED);
        }

        //이메일 인증이 완료 여부
        emailVerificationService.checkVerified(signupRequest.getUsername());

        // 기존 소셜 계정에 비밀번호만 연결(계정 연동)
        if(existingUser.isPresent()){
            User user = existingUser.get();
            user.setPassword(passwordEncoder.encode(signupRequest.getPassword()));
            userRepository.save(user);
            return; //여기서 메서드 종료해야함 밑으로 가면 안됨.
        }

        User user = new User();
        user.setName(signupRequest.getName());
        user.setUsername(signupRequest.getUsername());
        user.setPassword(passwordEncoder.encode(signupRequest.getPassword()));
        user.setNickname(signupRequest.getNickname());
        user.setRole(Role.USER);
        user.setStatus(AccountStatus.ACTIVE);
        user.setTermsAgreeAt(LocalDateTime.now());

        userRepository.save(user);
    }

    //로그인
    @Transactional
    public TokenResponse login(LoginRequest loginRequest) {
        // 회원가입 되어있는지 조회
        User user = userRepository.findByUsername(loginRequest.getUsername())
                .orElseThrow(() -> new ApiException(AuthErrorCode.USER_NOT_FOUND));
        //잠금 상태 확인
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())){
            throw new ApiException(AuthErrorCode.ACCOUNT_LOCKED);
        }
        // 비밀번호 불일치 검증
        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
            //비번 틀릴 때마다 실패횟수 1씩 증가
            loginAttemptService.recordFailedLoginAttempt(user);
            throw new ApiException(AuthErrorCode.INVALID_PASSWORD);
        }

        // 회원 탈퇴/정지 계정인지 체크
        checkAccountActive(user);
        //로그인 성공하면 다시 초기화
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        // 토큰(엑세스,리플레쉬) 생성
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name(), user.getFestivalId());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUsername());

        // DB에 RefreshToken 저장
        RefreshToken newRefreshToken = new RefreshToken();
        newRefreshToken.setUser(user);
        newRefreshToken.setTokenHash(hashToken(refreshToken)); //해쉬토큰 메서드로 평문방지 보안처리
        newRefreshToken.setExpiresAt(LocalDateTime.now().plus(Duration.ofMillis(refreshTokenExpiration)));
        refreshTokenRepository.save(newRefreshToken);

        return new TokenResponse(accessToken, refreshToken);
    }

    // 재사용 탐지 시, 폐기된 지 이 시간 안이면 "짧은 시간 내 반복 새로고침으로 인한 경합"으로 보고
    // 전체 로그아웃 대신 최신 후속 토큰으로 자연스럽게 재발급해준다. 이 구간을 벗어난 재사용은
    // 여전히 진짜 탈취로 간주한다 — 공격자가 로테이션 순간을 실시간으로 가로채지 않는 한 우회 불가.
    private static final Duration REUSE_GRACE_PERIOD = Duration.ofSeconds(5);

    // reissue(재발급)
    @Transactional
    public TokenResponse reissue(String refreshToken){
        // jwt 자체 유효성 검증 (서명/만료)
        if (!jwtTokenProvider.validateToken(refreshToken)){
            throw new ApiException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }
        // 받은 토큰을 해시해서 DB조회
        String tokenHash = hashToken(refreshToken);
        RefreshToken savedRefreshToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ApiException(AuthErrorCode.INVALID_REFRESH_TOKEN));

        // 이미 폐기된 토큰이 재사용됐는지 확인
        if (savedRefreshToken.getRevokedAt() != null){
            RefreshToken healedToken = resolveGraceHealedToken(savedRefreshToken);
            if (healedToken == null) {
                refreshTokenRevocationService.revokeAllTokens(savedRefreshToken.getUser());
                throw new ApiException(AuthErrorCode.REFRESH_TOKEN_REUSED);
            }
            return rotate(healedToken);
        }
        // DB에서도 만료 여부 확인-> 이중체크
        if (savedRefreshToken.getExpiresAt().isBefore(LocalDateTime.now())){
            throw new ApiException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        return rotate(savedRefreshToken);
    }

    // 폐기된 지 REUSE_GRACE_PERIOD 이내라면 replacedByTokenId 체인을 따라가 현재 유효한 최신 토큰을
    // 찾아 반환한다. 유예 구간을 벗어났거나(오래된 재사용) 체인이 끊겼으면(revokeAllTokens로 통째로
    // 폐기된 경우 등) null을 반환해 호출부가 진짜 재사용(탈취)으로 처리하게 한다.
    private RefreshToken resolveGraceHealedToken(RefreshToken revokedToken) {
        if (Duration.between(revokedToken.getRevokedAt(), LocalDateTime.now()).compareTo(REUSE_GRACE_PERIOD) > 0) {
            return null;
        }

        RefreshToken current = revokedToken;
        while (current.getRevokedAt() != null) {
            Long nextId = current.getReplacedByTokenId();
            if (nextId == null) {
                return null;
            }
            current = refreshTokenRepository.findById(nextId).orElse(null);
            if (current == null) {
                return null;
            }
        }

        if (current.getExpiresAt().isBefore(LocalDateTime.now())) {
            return null;
        }
        return current;
    }

    // 검증이 끝난 리프레시 토큰을 실제로 로테이션한다 (신규 발급 + 기존 토큰 폐기).
    private TokenResponse rotate(RefreshToken savedRefreshToken) {
        User user = savedRefreshToken.getUser();
        //회탈/정지 검증
        checkAccountActive(user);

        String newAccessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name(), user.getFestivalId());
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getUsername());

        RefreshToken newRefreshTokenEntity = new RefreshToken();
        newRefreshTokenEntity.setUser(user);
        newRefreshTokenEntity.setTokenHash(hashToken(newRefreshToken));
        newRefreshTokenEntity.setExpiresAt(LocalDateTime.now().plus(Duration.ofMillis(refreshTokenExpiration)));
        refreshTokenRepository.save(newRefreshTokenEntity);

        // 예전 토큰 폐기 처리, 새 토큰과 연결 (Rotation)
        savedRefreshToken.setRevokedAt(LocalDateTime.now());
        savedRefreshToken.setReplacedByTokenId(newRefreshTokenEntity.getId());
        refreshTokenRepository.save(savedRefreshToken);

        return new TokenResponse(newAccessToken, newRefreshToken);
    }

    // 로그아웃
    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null) {
            return; // 쿠키 자체가 없으면 이미 로그아웃 상태나 다름없으니 종료
        }
        String tokenHash = hashToken(refreshToken);
        refreshTokenRepository.findByTokenHash(tokenHash)
                .ifPresent(token -> {
                    token.setRevokedAt(LocalDateTime.now());
                    refreshTokenRepository.save(token);
                });
    }

    // 비밀번호 재설정 (이메일 인증 완료 -> 새 비밀번호 설정)
    @Transactional
    public void resetPassword(String username,String newPassword){
        //이메일 인증 완료 확인
        emailVerificationService.checkVerified(username);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ApiException(AuthErrorCode.USER_NOT_FOUND));

        //소셜 로그인은 변경 불가 로직
        if(user.getPassword() == null){
            throw new ApiException(UserErrorCode.SOCIAL_USER_CANNOT_CHANGE_PASSWORD);
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        refreshTokenRevocationService.revokeAllTokens(user);
    }

    // 계정 상태(정지/탈퇴) 확인 편의메서드 -나증에 OAuth,재발급 때에도 쓰여서 만들어놓음
    private void checkAccountActive(User user) {
        if (user.getStatus() == AccountStatus.SUSPENDED) {
            throw new ApiException(AuthErrorCode.ACCOUNT_SUSPENDED);
        }
        if (user.getStatus() == AccountStatus.WITHDRAWN) {
            throw new ApiException(AuthErrorCode.ACCOUNT_WITHDRAWN);
        }
    }

    // DB에는 토큰 원본을 그대로 저장하지 않고 해시값만 저장해, DB 유출 시에도 실제 토큰이 복원되지 않도록 함
    // 평문을 해시로 변환하는 메서드
    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }

    // Kakao 최초 로그인 시 회원가입
    private User registerKakaoUser(KakaoUserInfoResponse kakaoUserInfo, String providerId){
        User user = new User();
        user.setUsername("kakao_" + providerId + "@kakao.local");
        user.setName(kakaoUserInfo.getKakao_account().getProfile().getNickname());
        user.setNickname(generateUniqueNickname(kakaoUserInfo.getKakao_account().getProfile().getNickname())); //뒤에 랜덤 숫자4자리 붙임
        user.setPassword(null);                                   //카카오에서 실명을 주지 않아서 일단 닉네임으로 채우고 나중에 마이페이지에서 닉네임 수정 유도
        user.setRole(Role.USER);
        user.setStatus(AccountStatus.ACTIVE);

        userRepository.save(user);

        OauthAccount oauthAccount = new OauthAccount();
        oauthAccount.setUser(user);
        oauthAccount.setProvider("KAKAO");
        oauthAccount.setProviderId(providerId);
        oauthAccount.setLinkedAt(LocalDateTime.now());

        oauthAccountRepository.save(oauthAccount);

        return user;
    }

    // Kakao 로그인
    @Transactional
    public TokenResponse kakaoLogin(String code) {
        String kakaoAccessToken;
        KakaoUserInfoResponse kakaoUserInfo;
        try {
            kakaoAccessToken = kakaoApiClient.getAccessToken(code);
            kakaoUserInfo = kakaoApiClient.getUserInfo(kakaoAccessToken); //엑세스토큰으로 카카오 사용자 정보 조회
        } catch (HttpClientErrorException e) {
            throw new ApiException(AuthErrorCode.OAUTH_TOKEN_INVALID);
        }

        String providerId = String.valueOf(kakaoUserInfo.getId());

        User user = oauthAccountRepository.findByProviderAndProviderId("KAKAO", providerId)
                .map(oauthAccount -> oauthAccount.getUser()) //이미 카카오 회원가입 한상태
                .orElseGet(() -> registerKakaoUser(kakaoUserInfo, providerId)); //처음 회원가입

        // 회원 탈퇴/정지 계정인지 체크
        checkAccountActive(user);

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name(), user.getFestivalId());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUsername());

        // DB에 RefreshToken 저장
        RefreshToken newRefreshToken = new RefreshToken();
        newRefreshToken.setUser(user);
        newRefreshToken.setTokenHash(hashToken(refreshToken));
        newRefreshToken.setExpiresAt(LocalDateTime.now().plus(Duration.ofMillis(refreshTokenExpiration)));
        refreshTokenRepository.save(newRefreshToken);

        return new TokenResponse(accessToken, refreshToken);
    }

    // Google 최초 로그인 시 회원가입 또는 기존 계정 연결
    private User registerGoogleUser(GoogleUserInfoResponse googleUserInfo,String providerId){
        User user = userRepository.findByUsername(googleUserInfo.getEmail())
                .orElseGet(() -> createNewGoogleUser(googleUserInfo));

        OauthAccount oauthAccount = new OauthAccount();
        oauthAccount.setUser(user);
        oauthAccount.setProvider("GOOGLE");
        oauthAccount.setProviderId(providerId);
        oauthAccount.setLinkedAt(LocalDateTime.now());

        oauthAccountRepository.save(oauthAccount);

        return user;
    }

    // 완전히 새로운 구글 유저 생성
    private User createNewGoogleUser(GoogleUserInfoResponse googleUserInfo) {
        User user = new User();
        user.setUsername(googleUserInfo.getEmail());
        user.setName(googleUserInfo.getName());
        user.setNickname(generateUniqueNickname(googleUserInfo.getName())); // 뒤에 랜덤 숫자 6자리 붙임
        user.setPassword(null);
        user.setRole(Role.USER);
        user.setStatus(AccountStatus.ACTIVE);

        return userRepository.save(user);
    }

    // Google 로그인
    @Transactional
    public TokenResponse googleLogin(String code) {
        String googleAccessToken;
        GoogleUserInfoResponse googleUserInfo;
        try {
            googleAccessToken = googleApiClient.getAccessToken(code);
            googleUserInfo = googleApiClient.getUserInfo(googleAccessToken);
        } catch (HttpClientErrorException e) {
            throw new ApiException(AuthErrorCode.OAUTH_TOKEN_INVALID);
        }

        String providerId = googleUserInfo.getId();

        User user = oauthAccountRepository.findByProviderAndProviderId("GOOGLE", providerId)
                .map(oauthAccount -> oauthAccount.getUser())
                .orElseGet(() -> registerGoogleUser(googleUserInfo, providerId));

        checkAccountActive(user);

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name(), user.getFestivalId());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUsername());

        RefreshToken newRefreshToken = new RefreshToken();
        newRefreshToken.setUser(user);
        newRefreshToken.setTokenHash(hashToken(refreshToken));
        newRefreshToken.setExpiresAt(LocalDateTime.now().plus(Duration.ofMillis(refreshTokenExpiration)));
        refreshTokenRepository.save(newRefreshToken);

        return new TokenResponse(accessToken, refreshToken);
    }

    // 이름 뒤에 랜덤6자리 숫자 붙여서 닉네임 생성 메서드
    private String generateUniqueNickname(String name) {
        Random random = new Random();
        String nickname;

        do {
            int randomNumber = random.nextInt(1000000); // 0 ~ 999999
            nickname = name + "_" + String.format("%06d", randomNumber); // 항상 6자리로 고정 (부족하면 앞에 0 채움)
        } while (userRepository.existsByNickname(nickname));

        return nickname;
    }
}
