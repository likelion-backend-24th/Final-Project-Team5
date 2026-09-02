package org.example.authservice.auth.service;

import lombok.RequiredArgsConstructor;
import org.example.authservice.auth.dto.SignupRequest;
import org.example.authservice.auth.exception.AuthErrorCode;
import org.example.authservice.common.exception.ApiException;
import org.example.authservice.user.entity.AccountStatus;
import org.example.authservice.user.entity.Role;
import org.example.authservice.user.entity.User;
import org.example.authservice.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void signup(SignupRequest signupRequest){
        // 이메일 중복 검증
        if (userRepository.existsByUsername(signupRequest.getUsername())) {
            throw new ApiException(AuthErrorCode.DUPLICATE_USERNAME);
        }
        //닉네임중복 검증
        if (userRepository.existsByNickname(signupRequest.getNickname())){
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

}
