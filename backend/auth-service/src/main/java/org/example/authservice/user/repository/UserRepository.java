package org.example.authservice.user.repository;

import jakarta.persistence.LockModeType;
import org.example.authservice.user.entity.Role;
import org.example.authservice.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    List<User> findByRoleAndFestivalId(Role role, Long festivalId);

    List<User> findByRoleAndFestivalEndAtBefore(Role role, LocalDateTime threshold);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findLockedById(@Param("id") Long id);
}
