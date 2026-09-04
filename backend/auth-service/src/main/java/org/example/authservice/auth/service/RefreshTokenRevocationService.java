package org.example.authservice.auth.service;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.authservice.auth.entity.RefreshToken;
import org.example.authservice.auth.repository.RefreshTokenRepository;
import org.example.authservice.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh Token 재사용(탈취)이 감지되면 해당 사용자의 살아있는 세션을 전부 폐기한다.
 * AuthService.reissue()는 재사용 감지 직후 예외를 던져 트랜잭션을 롤백시키므로, 이 폐기
 * 처리를 같은 트랜잭션에서 실행하면 롤백과 함께 취소되어 버린다. REQUIRES_NEW로 별도
 * 트랜잭션에서 즉시 커밋해 롤백의 영향을 받지 않게 한다.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenRevocationService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeAllTokens(User user) {
        List<RefreshToken> activeTokens = refreshTokenRepository.findAllByUser_IdAndRevokedAtIsNull(user.getId());
        LocalDateTime now = LocalDateTime.now();
        for (RefreshToken token : activeTokens) {
            token.setRevokedAt(now);
        }
        refreshTokenRepository.saveAll(activeTokens);
    }
}
