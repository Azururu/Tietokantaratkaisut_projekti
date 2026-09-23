package com.webstore.api.order;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderLogRepository extends JpaRepository<OrderLog, Integer> {

    List<OrderLog> findByOrderIdOrderByLogId(Integer orderId);
}
