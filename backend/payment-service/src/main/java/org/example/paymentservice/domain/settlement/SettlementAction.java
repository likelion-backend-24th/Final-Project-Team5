package org.example.paymentservice.domain.settlement;

import org.example.paymentservice.common.exception.ApiException;

public enum SettlementAction {
    CONFIRM("confirm"),
    REAPPROVE("reapprove"),
    MARK_PAID("mark-paid"),
    HOLD("hold"),
    RELEASE("release"),
    RECALCULATE("recalculate");

    private final String pathValue;

    SettlementAction(String pathValue) {
        this.pathValue = pathValue;
    }

    public String pathValue() {
        return pathValue;
    }

    public static SettlementAction fromPath(String path) {
        for (var action : values()) {
            if (action.pathValue.equals(path)) return action;
        }
        throw new ApiException(SettlementErrorCode.UNKNOWN_ACTION);
    }
}
