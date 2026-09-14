package org.example.paymentservice.domain.settlement;
public enum SettlementStatus {
    PENDING, CALCULATED, HELD, CONFIRMED, PAID, ADJUSTMENT_REQUIRED;
    public boolean recalculable() { return this == PENDING || this == CALCULATED || this == HELD; }
    public boolean permits(SettlementStatus next) {
        return switch (this) {
            case PENDING -> next == CALCULATED || next == HELD;
            case CALCULATED -> next == CALCULATED || next == HELD || next == CONFIRMED;
            case HELD -> next == CALCULATED || next == HELD;
            case CONFIRMED -> next == PAID || next == ADJUSTMENT_REQUIRED;
            case PAID -> next == ADJUSTMENT_REQUIRED;
            case ADJUSTMENT_REQUIRED -> next == CONFIRMED;
        };
    }
}
