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

import com.webstore.api.common.BadRequestException;
import com.webstore.api.common.ConflictException;
import com.webstore.api.common.NotFoundException;
import com.webstore.api.common.PageResponse;
import com.webstore.api.supplier.Supplier;
import com.webstore.api.supplier.SupplierRepository;

@Service
@Transactional(readOnly = true)
public class ProductService {

    private static final Set<String> SORT_FIELDS = Set.of("id", "name", "price");

    private final CatalogProductRepository catalog;
    private final ProductRepository products;
    private final CategoryRepository categories;
    private final SupplierRepository suppliers;

    public ProductService(CatalogProductRepository catalog, ProductRepository products,
            CategoryRepository categories, SupplierRepository suppliers) {
        this.catalog = catalog;
        this.products = products;
        this.categories = categories;
        this.suppliers = suppliers;
    }

    public PageResponse<ProductResponse> search(String query, Integer categoryId, BigDecimal minPrice,
            BigDecimal maxPrice, boolean inStockOnly, String sort, boolean descending, int page, int size) {
        List<Specification<CatalogProduct>> filters = new ArrayList<>();
        if (query != null && !query.isBlank()) {
            filters.add(CatalogProductRepository.nameStartsWith(query));
        }
        if (categoryId != null) {
            filters.add(CatalogProductRepository.inCategory(categoryId));
        }
        if (minPrice != null) {
            filters.add(CatalogProductRepository.priceAtLeast(minPrice));
        }
        if (maxPrice != null) {
            filters.add(CatalogProductRepository.priceAtMost(maxPrice));
        }
        if (inStockOnly) {
            filters.add(CatalogProductRepository.inStockOnly());
        }

        Sort order = Sort.by(descending ? Sort.Direction.DESC : Sort.Direction.ASC,
                SORT_FIELDS.contains(sort) ? sort : "id");
        Page<CatalogProduct> result = catalog.findAll(Specification.allOf(filters), PageRequest.of(page, size, order));

        return new PageResponse<>(
                result.map(ProductResponse::from).getContent(), page, size, result.getTotalElements());
    }

    public ProductResponse get(int id) {
        return catalog.findById(id)
                .map(ProductResponse::from)
                .orElseThrow(() -> new NotFoundException("Product " + id + " not found"));
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        Product product = new Product(request.name(), request.description(), request.price(),
                request.stockQuantity(), category(request.categoryId()), supplier(request.supplierId()));
        // Flush before reading the view: Hibernate doesn't know that v_product_catalog reads products.
        int id = products.saveAndFlush(product).getId();
        return get(id);
    }

    @Transactional
    public ProductResponse update(int id, ProductRequest request) {
        Product product = products.findById(id)
                .orElseThrow(() -> new NotFoundException("Product " + id + " not found"));
        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setStockQuantity(request.stockQuantity());
        product.setCategory(category(request.categoryId()));
        product.setSupplier(supplier(request.supplierId()));
        products.flush();
        return get(id);
    }

    /** Products on existing orders can't be deleted: the order lines refer to them. */
    @Transactional
    public void delete(int id) {
        Product product = products.findById(id)
                .orElseThrow(() -> new NotFoundException("Product " + id + " not found"));
        if (products.isOrdered(id)) {
            throw new ConflictException("Product " + id + " is on orders and can't be deleted");
        }
        products.delete(product);
    }

    private ProductCategory category(Integer id) {
        return id == null ? null : categories.findById(id)
                .orElseThrow(() -> new BadRequestException("Category " + id + " doesn't exist"));
    }

    private Supplier supplier(Integer id) {
        return id == null ? null : suppliers.findById(id)
                .orElseThrow(() -> new BadRequestException("Supplier " + id + " doesn't exist"));
    }
}
