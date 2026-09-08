package org.example.authservice.user.repository;

import org.example.authservice.user.entity.AccountStatus;
import org.example.authservice.user.entity.Role;
import org.example.authservice.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User,Long> {

    // 이메일 조회
    Optional<User> findByUsername(String username);
    // 이메일 증복체크때 사용
    boolean existsByUsername(String username);
    // 닉네임 중복체크때 사용
    boolean existsByNickname(String nickname);

    // 호스트가 자기 페스티벌에 발급해둔 도우미 계정 목록을 조회할 때 사용
    List<User> findByRoleAndFestivalIdAndStatus(Role role, Long festivalId, AccountStatus status);

    // 도우미 계정 자동 탈퇴 배치가 회수 대상(페스티벌 종료 후 유예시간이 지난 계정)을 찾을 때 사용
    List<User> findByRoleAndStatusAndFestivalEndAtBefore(
            Role role, AccountStatus status, LocalDateTime revokeThreshold);

}
