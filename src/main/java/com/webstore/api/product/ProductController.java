package com.webstore.api.product;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.webstore.api.common.PageResponse;
import com.webstore.api.supplier.SupplierResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService products;

    public ProductController(ProductService products) {
        this.products = products;
    }

    @GetMapping
    public PageResponse<ProductResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "false") boolean inStock,
            @RequestParam(defaultValue = "id") String sort,
            @RequestParam(defaultValue = "asc") String dir,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return products.search(q, categoryId, minPrice, maxPrice, inStock, sort,
                "desc".equalsIgnoreCase(dir), page, size);
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable int id) {
        return products.get(id);
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        ProductResponse product = products.create(request);
        return ResponseEntity.created(URI.create("/api/products/" + product.id())).body(product);
    }

    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable int id, @Valid @RequestBody ProductRequest request) {
        return products.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable int id) {
        products.delete(id);
        return ResponseEntity.noContent().build();
    }

    // Bulk operations (assignment 13)

    @PostMapping("/bulk/price")
    public Map<String, Integer> changePrices(
            @RequestParam int categoryId,
            @RequestParam @DecimalMin("0.01") @DecimalMax("10.00") BigDecimal factor) {
        return Map.of("updated", products.changePricesInCategory(categoryId, factor));
    }

    @PostMapping("/bulk/restock")
    public Map<String, Integer> restock(
            @RequestParam @Min(1) @Max(10000) int amount,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) Integer supplierId,
            @RequestParam(required = false) Integer stockBelow) {
        return Map.of("updated", products.restock(amount, categoryId, supplierId, stockBelow));
    }



    // N:M suppliers of a product (join table product_suppliers)

    @GetMapping("/{id}/suppliers")
    public List<SupplierResponse> suppliers(@PathVariable int id) {
        return products.suppliersOf(id);
    }

    @PostMapping("/{id}/suppliers/{supplierId}")
    public ResponseEntity<List<SupplierResponse>> addSupplier(@PathVariable int id, @PathVariable int supplierId) {
        return ResponseEntity.created(URI.create("/api/products/" + id + "/suppliers"))
                .body(products.addSupplier(id, supplierId));
    }

    @DeleteMapping("/{id}/suppliers/{supplierId}")
    public ResponseEntity<Void> removeSupplier(@PathVariable int id, @PathVariable int supplierId) {
        products.removeSupplier(id, supplierId);
        return ResponseEntity.noContent().build();
    }
}
