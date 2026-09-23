package com.webstore.api.product;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.webstore.api.common.PageResponse;

@Repository
public class ProductRepository {

    private static final String SELECT_PRODUCT = """
            SELECT p.id, p.name, p.description, p.price, p.stock_quantity,
                   p.category_id, c.name AS category_name,
                   p.supplier_id, s.name AS supplier_name
              FROM products p
              LEFT JOIN productcategories c ON c.id = p.category_id
              LEFT JOIN suppliers s ON s.id = p.supplier_id
            """;

    /** Allowed sort keys. Column names cannot be bound as parameters, so they are whitelisted. */
    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "id", "p.id",
            "name", "p.name",
            "price", "p.price");

    private static final RowMapper<Product> ROW_MAPPER = (rs, i) -> new Product(
            rs.getInt("id"),
            rs.getString("name"),
            rs.getString("description"),
            rs.getBigDecimal("price"),
            rs.getInt("stock_quantity"),
            rs.getObject("category_id", Integer.class),
            rs.getString("category_name"),
            rs.getObject("supplier_id", Integer.class),
            rs.getString("supplier_name"));

    private final NamedParameterJdbcTemplate jdbc;

    public ProductRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PageResponse<Product> search(String query, Integer categoryId, BigDecimal minPrice,
            BigDecimal maxPrice, String sort, boolean descending, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        MapSqlParameterSource params = new MapSqlParameterSource();

        if (query != null && !query.isBlank()) {
            // Prefix match so the idx_name index can be used.
            where.append(" AND p.name LIKE :query");
            params.addValue("query", query + "%");
        }
        if (categoryId != null) {
            where.append(" AND p.category_id = :categoryId");
            params.addValue("categoryId", categoryId);
        }
        if (minPrice != null) {
            where.append(" AND p.price >= :minPrice");
            params.addValue("minPrice", minPrice);
        }
        if (maxPrice != null) {
            where.append(" AND p.price <= :maxPrice");
            params.addValue("maxPrice", maxPrice);
        }

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM products p" + where, params, Long.class);

        String orderBy = " ORDER BY " + SORT_COLUMNS.getOrDefault(sort, "p.id")
                + (descending ? " DESC" : " ASC");
        params.addValue("limit", size);
        params.addValue("offset", page * size);
        List<Product> items = jdbc.query(
                SELECT_PRODUCT + where + orderBy + " LIMIT :limit OFFSET :offset", params, ROW_MAPPER);

        return new PageResponse<>(items, page, size, total == null ? 0 : total);
    }

    public Optional<Product> findById(int id) {
        return jdbc.query(SELECT_PRODUCT + " WHERE p.id = :id", Map.of("id", id), ROW_MAPPER)
                .stream().findFirst();
    }
}
