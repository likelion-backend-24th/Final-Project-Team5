package org.example.paymentservice.domain.payment;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentMethodCategoryTest {

    @ParameterizedTest
    @CsvSource({
            "PaymentMethodCard, CARD",
            "PaymentMethodEasyPay, EASY_PAY",
            "PaymentMethodVirtualAccount, VIRTUAL_ACCOUNT",
            "CARD, CARD",
            "EASY_PAY, EASY_PAY",
            "VIRTUAL_ACCOUNT, VIRTUAL_ACCOUNT"
    })
    void PortOne_결제수단과_기존_저장값을_정규화한다(String rawMethod, PaymentMethodCategory expected) {
        assertThat(PaymentMethodCategory.fromRaw(rawMethod)).isEqualTo(expected);
    }
}
