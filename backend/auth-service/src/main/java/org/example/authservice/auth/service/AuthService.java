package org.example.authservice.auth.service;

import lombok.RequiredArgsConstructor;
import org.example.authservice.auth.dto.LoginRequest;
import org.example.authservice.auth.dto.SignupRequest;
import org.example.authservice.auth.dto.TokenResponse;
import org.example.authservice.auth.entity.RefreshToken;
import org.example.authservice.auth.exception.AuthErrorCode;
import org.example.authservice.auth.repository.RefreshTokenRepository;
import org.example.authservice.auth.security.JwtTokenProvider;
import org.example.authservice.common.exception.ApiException;
import org.example.authservice.user.entity.AccountStatus;
import org.example.authservice.user.entity.Role;
import org.example.authservice.user.entity.User;
import org.example.authservice.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    @Transactional
    public void signup(SignupRequest signupRequest) {
        // 이메일 중복 검증
        if (userRepository.existsByUsername(signupRequest.getUsername())) {
            throw new ApiException(AuthErrorCode.DUPLICATE_USERNAME);
        }
        //닉네임중복 검증
        if (userRepository.existsByNickname(signupRequest.getNickname())) {
            throw new ApiException(AuthErrorCode.DUPLICATE_NICKNAME);
        }

        User user = new User();
        user.setName(signupRequest.getName());
        user.setUsername(signupRequest.getUsername());
        user.setPassword(passwordEncoder.encode(signupRequest.getPassword()));
        user.setNickname(signupRequest.getNickname());
        user.setRole(Role.USER);
        user.setStatus(AccountStatus.ACTIVE);

        userRepository.save(user);
    }

    //로그인
    @Transactional
    public TokenResponse login(LoginRequest loginRequest) {
        // 회원가입 되어있는지 조회
        User user = userRepository.findByUsername(loginRequest.getUsername())
                .orElseThrow(() -> new ApiException(AuthErrorCode.USER_NOT_FOUND));
        // 비밀번호 불일치 검증
        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
            throw new ApiException(AuthErrorCode.INVALID_PASSWORD);
        }
        // 회원 탈퇴/정지 계정인지 체크
        checkAccountActive(user);

        // 토큰(엑세스,리플레쉬) 생성
        String accessToken = jwtTokenProvider.generateAccessToken(user.getUsername(), user.getRole().name());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUsername());

        // DB에 RefreshToken 저장
        RefreshToken newRefreshToken = new RefreshToken();
        newRefreshToken.setUser(user);
        newRefreshToken.setTokenHash(hashToken(refreshToken)); //해쉬토큰 메서드로 평문방지 보안처리
        newRefreshToken.setExpiresAt(LocalDateTime.now().plus(Duration.ofMillis(refreshTokenExpiration)));
        refreshTokenRepository.save(newRefreshToken);

        return new TokenResponse(accessToken, refreshToken);
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

}
