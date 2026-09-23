package com.webstore.api.product;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.webstore.api.common.NotFoundException;
import com.webstore.api.common.PageResponse;

@Service
@Transactional(readOnly = true)
public class ProductService {

    private static final Set<String> SORT_FIELDS = Set.of("id", "name", "price");

    private final ProductRepository products;

    public ProductService(ProductRepository products) {
        this.products = products;
    }

    public PageResponse<ProductResponse> search(String query, Integer categoryId, BigDecimal minPrice,
            BigDecimal maxPrice, String sort, boolean descending, int page, int size) {
        List<Specification<Product>> filters = new ArrayList<>();
        if (query != null && !query.isBlank()) {
            filters.add(ProductRepository.nameStartsWith(query));
        }
        if (categoryId != null) {
            filters.add(ProductRepository.inCategory(categoryId));
        }
        if (minPrice != null) {
            filters.add(ProductRepository.priceAtLeast(minPrice));
        }
        if (maxPrice != null) {
            filters.add(ProductRepository.priceAtMost(maxPrice));
        }

        Sort order = Sort.by(descending ? Sort.Direction.DESC : Sort.Direction.ASC,
                SORT_FIELDS.contains(sort) ? sort : "id");
        Page<Product> result = products.findAll(Specification.allOf(filters), PageRequest.of(page, size, order));

        return new PageResponse<>(
                result.map(ProductResponse::from).getContent(), page, size, result.getTotalElements());
    }

    public ProductResponse get(int id) {
        return products.findById(id)
                .map(ProductResponse::from)
                .orElseThrow(() -> new NotFoundException("Product " + id + " not found"));
    }
}
