package com.webstore.api.order;

import java.time.LocalDateTime;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Written only by the orderstatuschange trigger, so the entity is read-only.
 */
@Entity
@Immutable
@Table(name = "orderlog")
public class OrderLog {

    @Id
    @Column(name = "log_id")
    private Integer logId;

    @Column(name = "order_id", nullable = false)
    private Integer orderId;

    @Column(name = "old_status", length = 50)
    private String oldStatus;

    @Column(name = "new_status", nullable = false, length = 50)
    private String newStatus;

    @Column(name = "time_of_change")
    private LocalDateTime timeOfChange;

    @Column(length = 100)
    private String name;

    protected OrderLog() {
    }

    public Integer getLogId() {
        return logId;
    }

    public Integer getOrderId() {
        return orderId;
    }

    public String getOldStatus() {
        return oldStatus;
    }

    public String getNewStatus() {
        return newStatus;
    }

    public LocalDateTime getTimeOfChange() {
        return timeOfChange;
    }

    public String getName() {
        return name;
    }
}
