package org.example.authservice.user.service;

import lombok.RequiredArgsConstructor;
import org.example.authservice.common.exception.ApiException;
import org.example.authservice.user.dto.UserResponse;
import org.example.authservice.user.entity.User;
import org.example.authservice.user.exception.UserErrorCode;
import org.example.authservice.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

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
}
