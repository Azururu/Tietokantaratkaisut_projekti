package com.webstore.api.supplier;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.webstore.api.product.ProductService;
import com.webstore.api.product.SupplierProductResponse;

@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {

    private final ProductService products;

    public SupplierController(ProductService products) {
        this.products = products;
    }

    /** N:M example: every product the supplier delivers (through product_suppliers). */
    @GetMapping("/{id}/products")
    public List<SupplierProductResponse> products(@PathVariable int id) {
        return products.productsOf(id);
    }
}
