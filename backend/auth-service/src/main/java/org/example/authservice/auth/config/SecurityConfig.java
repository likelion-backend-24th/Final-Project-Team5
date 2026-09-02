package org.example.authservice.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 인증은 게이트웨이(JwtAuthenticationGlobalFilter)가 전담하고, 이 서비스는 게이트웨이가
 * 전달한 X-User-Id/X-User-Role 헤더만 신뢰한다. spring-boot-starter-security를 의존성에
 * 추가하면 기본값으로 모든 요청에 로그인(Basic Auth)을 강제하므로, 여기서 그 기본 동작을
 * 끈다. 회원가입·로그인 API 자체는 원래 인증 없이 호출돼야 하는 공개 라우트이기도 하다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
