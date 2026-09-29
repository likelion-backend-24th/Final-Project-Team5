package org.example.authservice.common.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.authservice.user.entity.AccountStatus;
import org.example.authservice.user.entity.Role;
import org.example.authservice.user.entity.User;
import org.example.authservice.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 로컬 재현(docker-compose.local.yml) 전용 시드 계정.
 * 새 환경에서는 메일 인증 없이 가입할 수 없고 ADMIN·STOREHOST는 부여 API가 없어 DB를 직접 고쳐야 했으므로,
 * LOCAL_SEED_ENABLED=true일 때만 운영자·부스 운영자·참가자 계정을 만든다. 운영 compose는 이 값을 켜지 않는다.
 * 같은 이메일이 이미 있으면 건드리지 않는다(재기동해도 멱등). 비밀번호는 LOCAL_SEED_PASSWORD로만 받는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "local-seed.enabled", havingValue = "true")
public class LocalAccountSeeder implements ApplicationRunner {

    static final String ADMIN_EMAIL = "local-admin@fevalgo.test";
    static final String STOREHOST_EMAIL = "local-storehost@fevalgo.test";
    static final String USER_EMAIL = "local-user@fevalgo.test";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${local-seed.password:}")
    private String password;

    @Override
    public void run(ApplicationArguments args) {
        if (password == null || password.isBlank()) {
            log.warn("LOCAL_SEED_PASSWORD가 비어 있어 로컬 시드 계정을 만들지 않는다.");
            return;
        }
        seed(ADMIN_EMAIL, "로컬 운영자", "로컬운영자", Role.ADMIN);
        seed(STOREHOST_EMAIL, "로컬 부스 운영자", "로컬부스운영자", Role.STOREHOST);
        seed(USER_EMAIL, "로컬 참가자", "로컬참가자", Role.USER);
    }

    private void seed(String email, String name, String nickname, Role role) {
        if (userRepository.existsByUsername(email)) {
            return;
        }
        User user = new User();
        user.setName(name);
        user.setUsername(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(nickname);
        user.setRole(role);
        user.setStatus(AccountStatus.ACTIVE);
        user.setTermsAgreeAt(LocalDateTime.now());
        userRepository.save(user);
        log.info("로컬 시드 계정 생성: {} ({})", email, role);
    }
}
