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
            case "CARD" -> CARD;
            case "EASY_PAY" -> EASY_PAY;
            case "VIRTUAL_ACCOUNT" -> VIRTUAL_ACCOUNT;
            default -> UNKNOWN;
        };
    }
}
