package org.example.authservice.auth.repository;

import org.example.authservice.auth.entity.RefreshToken;
import org.example.authservice.user.entity.User;
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
    //사용할 일이 생길수도 있어서 만들어놓음
    Optional<RefreshToken> findByUser(User user);

    //사용자 행을 삭제하기 전에 먼저 지워야 하는 토큰들.
    //refresh_token.user_id가 users를 FK(NO ACTION)로 참조하고 있어, 남겨두면 삭제가 제약 위반으로 실패한다.
    void deleteAllByUser_Id(Long userId);
}