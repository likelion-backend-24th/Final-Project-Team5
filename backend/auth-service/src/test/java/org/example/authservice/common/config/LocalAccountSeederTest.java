package org.example.authservice.common.config;

import org.example.authservice.auth.repository.RefreshTokenRepository;
import org.example.authservice.user.entity.Role;
import org.example.authservice.user.entity.User;
import org.example.authservice.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 로컬 재현용 시드 계정이 켜졌을 때만, 역할별로 한 번만 만들어지는지 확인한다.
 * 다른 테스트가 같은 H2 DB를 비우므로 기동 시 실행 결과에 기대지 않고 시더를 직접 다시 호출한다.
 */
@SpringBootTest(properties = {"local-seed.enabled=true", "local-seed.password=local-seed-test-1234"})
class LocalAccountSeederTest {

    @Autowired
    private LocalAccountSeeder seeder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void 켜져_있으면_운영자_부스운영자_참가자를_한_번씩만_만든다() {
        seeder.run(null);
        seeder.run(null);

        User admin = userRepository.findByUsername(LocalAccountSeeder.ADMIN_EMAIL).orElseThrow();
        User storehost = userRepository.findByUsername(LocalAccountSeeder.STOREHOST_EMAIL).orElseThrow();
        User participant = userRepository.findByUsername(LocalAccountSeeder.USER_EMAIL).orElseThrow();
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(storehost.getRole()).isEqualTo(Role.STOREHOST);
        assertThat(participant.getRole()).isEqualTo(Role.USER);
        assertThat(passwordEncoder.matches("local-seed-test-1234", admin.getPassword())).isTrue();
        assertThat(userRepository.count()).isEqualTo(3);
    }

    @Test
    void 설정을_켜지_않으면_시더가_등록되지_않는다() {
        new ApplicationContextRunner()
                .withBean(UserRepository.class, () -> mock(UserRepository.class))
                .withBean(PasswordEncoder.class, () -> mock(PasswordEncoder.class))
                .withUserConfiguration(LocalAccountSeeder.class)
                .run(context -> assertThat(context).doesNotHaveBean(LocalAccountSeeder.class));
    }
}
