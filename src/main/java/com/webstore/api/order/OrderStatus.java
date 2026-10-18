package com.webstore.api.order;

/**
 * Order statuses. dbValue is the exact text stored in orders.status, used by the
 * place_order / cancel_order procedures and the orderstatuschange trigger.
 */

public enum OrderStatus {
    NEW("NEW"),
    SHIPPED("SHIPPED"),
    CANCELLED("CANCELLED");

    private final String dbValue;

    OrderStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static OrderStatus fromDbValue(String Value) {
        for (OrderStatus status : values()) {
            if (status.dbValue.equals(Value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown OrderStatus: " + Value);
    }
}
