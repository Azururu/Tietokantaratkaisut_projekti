package com.webstore.api.order;

import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.ParameterMode;
import jakarta.persistence.StoredProcedureQuery;

/**
 * Calls the place_order and cancel_order stored procedures (sql/04_transactions.sql).
 *
 * The procedures run their own START TRANSACTION ... COMMIT, so they must not be
 * called inside a Spring @Transactional method: MariaDB has no nested transactions,
 * and the procedure's COMMIT would end the outer one. Each call therefore gets its
 * own EntityManager, which stays open until the OUT parameter has been read.
 */
@Repository
public class OrderProcedures {

    private final EntityManagerFactory emf;

    public OrderProcedures(EntityManagerFactory emf) {
        this.emf = emf;
    }

    /** Returns the id of the new order. */
    public int placeOrder(CreateOrderRequest request) {
        String itemsJson = request.items().stream()
                .map(item -> "{\"product_id\":" + item.productId() + ",\"quantity\":" + item.quantity() + "}")
                .collect(Collectors.joining(",", "[", "]"));

        try (EntityManager em = emf.createEntityManager()) {
            StoredProcedureQuery call = em.createStoredProcedureQuery("place_order");
            call.registerStoredProcedureParameter(1, Integer.class, ParameterMode.IN);
            call.registerStoredProcedureParameter(2, Integer.class, ParameterMode.IN);
            call.registerStoredProcedureParameter(3, String.class, ParameterMode.IN);
            call.registerStoredProcedureParameter(4, Integer.class, ParameterMode.OUT);
            call.setParameter(1, request.customerId());
            call.setParameter(2, request.shippingAddressId());
            call.setParameter(3, itemsJson);
            call.execute();
            return (Integer) call.getOutputParameterValue(4);
        }
    }

    public void cancelOrder(int orderId) {
        try (EntityManager em = emf.createEntityManager()) {
            StoredProcedureQuery call = em.createStoredProcedureQuery("cancel_order");
            call.registerStoredProcedureParameter(1, Integer.class, ParameterMode.IN);
            call.setParameter(1, orderId);
            call.execute();
        }
    }
}
