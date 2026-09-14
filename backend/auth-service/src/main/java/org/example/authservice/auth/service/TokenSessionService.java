package org.example.authservice.auth.service;

import lombok.RequiredArgsConstructor;
import org.example.authservice.auth.dto.TokenResponse;
import org.example.authservice.auth.entity.RefreshToken;
import org.example.authservice.auth.repository.RefreshTokenRepository;
import org.example.authservice.auth.security.JwtTokenProvider;
import org.example.authservice.user.entity.Role;
import org.example.authservice.user.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class TokenSessionService {

    private final JwtTokenProvider jwtTokenProvider;

    private final RefreshTokenRepository refreshTokenRepository;

    private final AccountAccessPolicy accountAccessPolicy;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    @Transactional
    public TokenResponse issue(User user) {
        return issue(user, null);
    }

    @Transactional
    public TokenResponse issue(User user, RefreshToken previous) {
        accountAccessPolicy.check(user);
        String accessToken;
        if (user.getRole() == Role.HELPER) {
            accessToken = jwtTokenProvider.generateHelperAccessToken(
                    user.getId(), user.getUsername(), user.getFestivalId(), helperSessionVersionOf(user));
        } else {
            accessToken = jwtTokenProvider.generateAccessToken(
                    user.getId(), user.getUsername(), user.getRole().name(), user.getFestivalId());
        }
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUsername());
        RefreshToken next = new RefreshToken();
        next.setUser(user);
        if (user.getRole() == Role.HELPER) {
            next.setHelperSessionVersion(helperSessionVersionOf(user));
        }
        next.setTokenHash(hashToken(refreshToken));
        next.setExpiresAt(LocalDateTime.now().plus(Duration.ofMillis(refreshTokenExpiration)));
        refreshTokenRepository.save(next);
        if (previous != null) {
            previous.setRevokedAt(LocalDateTime.now());
            previous.setReplacedByTokenId(next.getId());
            refreshTokenRepository.save(previous);
        }
        return new TokenResponse(accessToken, refreshToken);
    }

    // 계정 잠금을 가진 활성화/해지 트랜잭션과 함께 커밋한다.
    public void revokeAll(User user) {
        var tokens = refreshTokenRepository.findAllByUser_IdAndRevokedAtIsNull(user.getId());
        tokens.forEach(token -> token.setRevokedAt(LocalDateTime.now()));
        refreshTokenRepository.saveAll(tokens);
    }

    private long helperSessionVersionOf(User user) {
        return user.getHelperSessionVersion() == null ? 0L : user.getHelperSessionVersion();
    }

    public static String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable");
        }
    }
}
