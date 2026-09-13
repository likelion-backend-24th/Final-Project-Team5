package org.example.authservice.user.service;

import lombok.RequiredArgsConstructor;
import org.example.authservice.auth.entity.OauthAccount;
import org.example.authservice.auth.exception.AuthErrorCode;
import org.example.authservice.auth.repository.OauthAccountRepository;
import org.example.authservice.auth.repository.RefreshTokenRepository;
import org.example.authservice.auth.service.RefreshTokenRevocationService;
import org.example.authservice.common.exception.ApiException;
import org.example.authservice.user.dto.UserResponse;
import org.example.authservice.user.entity.AccountStatus;
import org.example.authservice.user.entity.Role;
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
    private final OauthAccountRepository oauthAccountRepository;

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
                //도우미가 로그인한 뒤 자기가 어느 페스티벌 담당인지 알아야 해당 화면을 열 수 있다.
                .festivalId(user.getFestivalId())
                //프론트가 소셜 계정의 비밀번호 변경 폼을 숨길 수 있도록 연결된 소셜 제공자와 비밀번호 유무를 내려준다.
                .socialProviders(oauthAccountRepository.findAllByUser_Id(userId).stream().map(OauthAccount::getProvider).toList())
                .hasPassword(user.getPassword() != null)
                //소셜 가입은 약관 동의·닉네임 입력 없이 계정이 만들어지므로, 약관 동의 시각이 비어 있으면 최초 1회
                //프로필 설정을 요구한다. 도우미는 주최자가 발급하는 임시 계정이라 제외한다.
                .profileSetupRequired(user.getTermsAgreeAt() == null && user.getRole() != Role.HELPER)
                .build();
    }

    //소셜 로그인 최초 진입 시 이름·닉네임 확정 + 약관 동의 (1회)
    @Transactional
    public void completeProfileSetup(Long userId, String name, String nickname, boolean termsAgreed) {
        if (!termsAgreed) {
            throw new ApiException(AuthErrorCode.TERMS_NOT_AGREED);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(UserErrorCode.USER_NOT_FOUND));

        String trimmedNickname = nickname.trim();
        if (!user.getNickname().equals(trimmedNickname) && userRepository.existsByNickname(trimmedNickname)) {
            throw new ApiException(UserErrorCode.DUPLICATE_NICKNAME);
        }
        user.setName(name.trim());
        user.setNickname(trimmedNickname);
        user.setTermsAgreeAt(LocalDateTime.now());
        userRepository.save(user);
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
        //도우미 계정은 주최자가 발급·회수하는 임시 계정이라 알바가 임의로 비밀번호를 바꿀 수 없다.
        //비밀번호를 분실하면 주최자가 재발급해준다(Gateway에서도 이 경로를 막지만 여기서 한 번 더 확인한다).
        if (user.getRole() == Role.HELPER) {
            throw new ApiException(UserErrorCode.HELPER_PASSWORD_CHANGE_NOT_ALLOWED);
        }
        //카카오·구글로 가입/연결된 계정은 비밀번호 변경을 막는다. 소셜 전용 계정은 비밀번호가 null이라
        //matches()가 항상 false가 되어 "현재 비밀번호 불일치"로 잘못 안내되고 있었다.
        if (user.getPassword() == null || !oauthAccountRepository.findAllByUser_Id(userId).isEmpty()) {
            throw new ApiException(UserErrorCode.SOCIAL_USER_CANNOT_CHANGE_PASSWORD);
        }
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
