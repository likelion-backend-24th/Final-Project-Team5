package org.example.authservice.user.service;

import lombok.RequiredArgsConstructor;
import org.example.authservice.auth.repository.RefreshTokenRepository;
import org.example.authservice.auth.service.RefreshTokenRevocationService;
import org.example.authservice.common.exception.ApiException;
import org.example.authservice.user.dto.UserResponse;
import org.example.authservice.user.entity.AccountStatus;
import org.example.authservice.user.entity.User;
import org.example.authservice.user.exception.UserErrorCode;
import org.example.authservice.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenRevocationService refreshTokenRevocationService;

    // 내 정보 조회
    public UserResponse getMyInfo(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(UserErrorCode.USER_NOT_FOUND));

        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .name(user.getName())
                .nickname(user.getNickname())
                .role(user.getRole())
                .status(user.getStatus())
                .createdAt(user.getCreatedAt())
                .build();
    }

    // 닉네임 수정
    @Transactional
    public void updateNickname(Long userId,String newNickname){
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(UserErrorCode.USER_NOT_FOUND));

        if (user.getNickname().equals(newNickname)) {
            return; // 변경할 필요 없음
        }

        if(userRepository.existsByNickname(newNickname)){
            throw new ApiException(UserErrorCode.DUPLICATE_NICKNAME);
        }
        user.setNickname(newNickname);
        userRepository.save(user);
    }

    //비밀번호 변경
    @Transactional
    public  void updatePassword(Long userId,String currentPassword,String newPassword, String newpasswordConfirm){
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(UserErrorCode.USER_NOT_FOUND));
        //현재 비밀번호 불일치
        if(!passwordEncoder.matches(currentPassword,user.getPassword())){
            throw new ApiException(UserErrorCode.INVALID_CURRENT_PASSWORD);
        } //새 비밀번호와 새 비밀번호 확인이 불일치
        if(!newPassword.equals(newpasswordConfirm)){
            throw new ApiException((UserErrorCode.PASSWORD_CONFIRM_MISMATCH));
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // 비밀번호 변경 시 탈취 의심 상황에 대비해 기존 모든 세션(Refresh Token)을 무효화
        refreshTokenRevocationService.revokeAllTokens(user);
    }

    // 회원탈퇴
    @Transactional
    public void withdrawAccount(Long userId, String password){
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(UserErrorCode.USER_NOT_FOUND));
        if (user.getPassword() != null && !passwordEncoder.matches(password,user.getPassword())){
            throw new ApiException(UserErrorCode.INVALID_CURRENT_PASSWORD);
        }
        //나중에 티켓 예약이 있으면 어떻게할지 정책 정해야함

        user.setName("탈퇴한 사용자");
        user.setStatus(AccountStatus.WITHDRAWN);
        user.setNickname("탈퇴한사용자_" + user.getId());
        user.setWithdrawnAt(LocalDateTime.now());
        userRepository.save(user);

        refreshTokenRevocationService.revokeAllTokens(user);
    }
}
