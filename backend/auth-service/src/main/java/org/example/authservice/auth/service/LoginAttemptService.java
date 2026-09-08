package org.example.authservice.auth.service;

import lombok.RequiredArgsConstructor;
import org.example.authservice.user.entity.User;
import org.example.authservice.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final UserRepository userRepository;

    // 로그인 실패 기록은 로그인로직의 트랜잭션과 무관하게 독립적으로 저장되어야함
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailedLoginAttempt(User user) {
        // 비번 틀리면  실패 횟수 증가
        user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
        //실패 횟수가 5번 넘어가면 10분동안 잠김 그리고 다시 0번으로 리셋
        if (user.getFailedLoginAttempts() >= 5){
            user.setLockedUntil(LocalDateTime.now().plusMinutes(10));
            user.setFailedLoginAttempts(0);
        }
        userRepository.save(user);
    }
}
