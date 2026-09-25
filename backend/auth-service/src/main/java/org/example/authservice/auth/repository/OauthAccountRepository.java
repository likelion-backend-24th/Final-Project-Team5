package org.example.authservice.auth.repository;

import org.example.authservice.auth.entity.OauthAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OauthAccountRepository extends JpaRepository<OauthAccount,Long> {
    // provider=Kakao,Google providerID=카카오나 구글고유ID 조회
    Optional<OauthAccount> findByProviderAndProviderId(String provider, String providerId);

    // 한 회원에 연결된 소셜 계정 전부(카카오/구글) — 비밀번호 변경 차단·내 정보 응답에 쓴다
    List<OauthAccount> findAllByUser_Id(Long userId);

    // 어드민 회원 목록 — 한 페이지에 나온 회원들의 소셜 계정을 한 번에 조회 (가입 경로 표시용)
    List<OauthAccount> findAllByUser_IdIn(Collection<Long> userIds);

    // 회원 탈퇴 시 소셜 연결을 끊어 같은 카카오/구글 계정으로 다시 가입할 수 있게 한다
    void deleteAllByUser_Id(Long userId);
}