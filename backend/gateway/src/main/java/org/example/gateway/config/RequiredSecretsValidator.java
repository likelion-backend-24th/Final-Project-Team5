package org.example.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 비밀값 환경변수가 빠진 채 코드 기본값(CHANGE_ME_IN_ENV 등)으로 기동되는 것을 막는다.
 * 기본값은 저장소에 공개돼 있어, 그대로 운영에 올라가면 누구나 그 값으로 내부 API를 부르거나 토큰을 위조할 수 있다.
 * docker-compose.yml처럼 REQUIRE_CONFIGURED_SECRETS=true로 띄우는 환경에서는 기동을 실패시키고,
 * 로컬 bootRun·테스트처럼 켜지 않은 환경에서는 경고만 남긴다. 값 자체는 로그·예외 메시지에 남기지 않는다.
 */
@Slf4j
@Component
public class RequiredSecretsValidator {

    private static final Set<String> KNOWN_DEFAULTS =
            Set.of("CHANGE_ME_IN_ENV", "local-dev-jwt-secret-please-change-before-deploy");

    public RequiredSecretsValidator(
            @Value("${security.require-configured-secrets:false}") boolean required,
            @Value("${jwt.secret}") String jwtSecret,
            @Value("${INTERNAL_AUTH_TOKEN:CHANGE_ME_IN_ENV}") String internalAuthToken) {
        Map<String, String> secrets = new LinkedHashMap<>();
        secrets.put("JWT_SECRET", jwtSecret);
        secrets.put("INTERNAL_AUTH_TOKEN", internalAuthToken);
        verify(secrets, required);
    }

    //환경변수 이름 → 실제로 주입된 값. 기본값·빈 값인 이름만 모아 알린다.
    static void verify(Map<String, String> secrets, boolean required) {
        List<String> unconfigured = secrets.entrySet().stream()
                .filter(entry -> entry.getValue() == null || entry.getValue().isBlank()
                        || KNOWN_DEFAULTS.contains(entry.getValue().trim()))
                .map(Map.Entry::getKey)
                .toList();
        if (unconfigured.isEmpty()) {
            return;
        }
        if (required) {
            throw new IllegalStateException("비밀값 환경변수가 설정되지 않아 기동을 중단합니다: " + unconfigured);
        }
        log.warn("비밀값 환경변수가 기본값입니다 — 로컬 개발에서만 허용됩니다: {}", unconfigured);
    }
}
