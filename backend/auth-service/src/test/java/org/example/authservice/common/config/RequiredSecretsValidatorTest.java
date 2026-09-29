package org.example.authservice.common.config;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequiredSecretsValidatorTest {

    private static Map<String, String> secrets(String first, String second) {
        Map<String, String> secrets = new LinkedHashMap<>();
        secrets.put("FIRST_SECRET", first);
        secrets.put("SECOND_SECRET", second);
        return secrets;
    }

    @Test
    void 운영_모드에서_기본값이나_빈_값이면_기동을_막고_값은_메시지에_남기지_않는다() {
        assertThatThrownBy(() -> RequiredSecretsValidator.verify(secrets("CHANGE_ME_IN_ENV", " "), true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FIRST_SECRET")
                .hasMessageContaining("SECOND_SECRET")
                .hasMessageNotContaining("CHANGE_ME_IN_ENV");
        assertThatThrownBy(() -> RequiredSecretsValidator.verify(
                secrets("local-dev-jwt-secret-please-change-before-deploy", "real-value-1"), true))
                .hasMessageContaining("FIRST_SECRET")
                .hasMessageNotContaining("SECOND_SECRET");
    }

    @Test
    void 실제_값이_있거나_운영_모드가_아니면_기동한다() {
        assertThatCode(() -> RequiredSecretsValidator.verify(secrets("real-value-1", "real-value-2"), true))
                .doesNotThrowAnyException();
        assertThatCode(() -> RequiredSecretsValidator.verify(secrets("CHANGE_ME_IN_ENV", null), false))
                .doesNotThrowAnyException();
    }
}
