package com.webstore.api.order;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OrderRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public OrderRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<OrderDetails> findById(int id) {
        List<OrderDetails> orders = jdbc.query("""
                SELECT o.id, o.customer_id, CONCAT(c.first_name, ' ', c.last_name) AS customer_name,
                       o.order_date, o.delivery_date, o.status,
                       a.id AS address_id, a.street_address, a.postal_code, a.city, a.country
                  FROM orders o
                  JOIN customers c ON c.id = o.customer_id
                  LEFT JOIN customeraddresses a ON a.id = o.shipping_address_id
                 WHERE o.id = :id
                """, Map.of("id", id), (rs, i) -> {
                    Integer addressId = rs.getObject("address_id", Integer.class);
                    OrderDetails.ShippingAddress address = addressId == null ? null
                            : new OrderDetails.ShippingAddress(addressId,
                                    rs.getString("street_address"), rs.getString("postal_code"),
                                    rs.getString("city"), rs.getString("country"));
                    List<OrderDetails.Item> items = findItems(id);
                    BigDecimal total = items.stream()
                            .map(OrderDetails.Item::lineTotal)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new OrderDetails(
                            rs.getInt("id"),
                            rs.getInt("customer_id"),
                            rs.getString("customer_name"),
                            rs.getObject("order_date", LocalDateTime.class),
                            rs.getObject("delivery_date", LocalDateTime.class),
                            rs.getString("status"),
                            address,
                            items,
                            total);
                });
        return orders.stream().findFirst();
    }

    private List<OrderDetails.Item> findItems(int orderId) {
        return jdbc.query("""
                SELECT oi.product_id, p.name, oi.quantity, oi.unit_price,
                       oi.quantity * oi.unit_price AS line_total
                  FROM orderitems oi
                  JOIN products p ON p.id = oi.product_id
                 WHERE oi.order_id = :orderId
                 ORDER BY oi.product_id
                """, Map.of("orderId", orderId), (rs, i) -> new OrderDetails.Item(
                        rs.getInt("product_id"),
                        rs.getString("name"),
                        rs.getInt("quantity"),
                        rs.getBigDecimal("unit_price"),
                        rs.getBigDecimal("line_total")));
    }

    /**
     * Calls the place_order procedure, which runs the whole order as one transaction.
     * Returns the new order id.
     */
    public int placeOrder(CreateOrderRequest request) {
        String itemsJson = request.items().stream()
                .map(item -> "{\"product_id\":" + item.productId() + ",\"quantity\":" + item.quantity() + "}")
                .collect(Collectors.joining(",", "[", "]"));

        JdbcTemplate plain = jdbc.getJdbcTemplate();
        return plain.execute((Connection con) -> {
            try (CallableStatement call = con.prepareCall("{call place_order(?, ?, ?, ?)}")) {
                call.setInt(1, request.customerId());
                call.setObject(2, request.shippingAddressId(), Types.INTEGER);
                call.setString(3, itemsJson);
                call.registerOutParameter(4, Types.INTEGER);
                call.execute();
                return call.getInt(4);
            }
        });
    }

    public void cancelOrder(int id) {
        jdbc.getJdbcTemplate().update("CALL cancel_order(?)", id);
    }
}
