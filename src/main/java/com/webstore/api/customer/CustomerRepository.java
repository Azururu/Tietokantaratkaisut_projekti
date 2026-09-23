package com.webstore.api.customer;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<Customer, Integer> {

    /**
     * All versions of a customer, oldest first. FOR SYSTEM_TIME ALL is MariaDB syntax,
     * so this is a native query. Includes deleted customers: their history is kept.
     * row_end of the current version is the maximum timestamp; it is returned as null.
     */
    @Query(nativeQuery = true, value = """
            SELECT id, first_name AS firstName, last_name AS lastName, email, phone,
                   row_start AS validFrom,
                   IF(row_end > NOW(6), NULL, row_end) AS validTo
              FROM customers FOR SYSTEM_TIME ALL
             WHERE id = :id
             ORDER BY row_start
            """)
    List<CustomerVersion> findHistory(@Param("id") int id);
}
