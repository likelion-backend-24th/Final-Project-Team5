package org.example.paymentservice.domain.payment;

public enum PaymentMethodCategory {
    CARD(750), EASY_PAY(750), VIRTUAL_ACCOUNT(500), UNKNOWN(null);

    private final Integer rateBps;

    PaymentMethodCategory(Integer rateBps) {
        this.rateBps = rateBps;
    }

    public Integer rateBps() {
        return rateBps;
    }

    public static PaymentMethodCategory fromRaw(String type) {
        if (type == null) {
            return UNKNOWN;
        }
        return switch (type) {
            case "CARD", "PaymentMethodCard" -> CARD;
            case "EASY_PAY", "PaymentMethodEasyPay" -> EASY_PAY;
            case "VIRTUAL_ACCOUNT", "PaymentMethodVirtualAccount" -> VIRTUAL_ACCOUNT;
            default -> UNKNOWN;
        };
    }
}
