package com.webstore.api.product;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<ProductCategory, Integer> {

    List<ProductCategory> findAllByOrderByName();
}
