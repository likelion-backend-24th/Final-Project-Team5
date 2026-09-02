package org.example.authservice.auth.repository;

import org.example.authservice.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    // 재발급 요청 시 토큰 조회
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    // 재사용 탐지 시 유저의 살아있는 토큰 전체 조회 (강제 폐기용)
    List<RefreshToken> findAllByUser_IdAndRevokedAtIsNull(Long userId);
}