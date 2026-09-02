package org.example.authservice.user.repository;

import org.example.authservice.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User,Long> {

    // 이메일 조회
    Optional<User> findByUsername(String username);
    // 이메일 증복체크때 사용
    boolean existsByUsername(String username);
    // 닉네임 중복체크때 사용
    boolean existsByNickname(String nickname);

}
