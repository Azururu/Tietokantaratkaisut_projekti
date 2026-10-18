package com.webstore.api.product;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
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
import com.webstore.api.supplier.SupplierResponse;

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

    /**
     * Optimistic locking. The client sends back the version it loaded; if the product
     * has been changed since (another admin's save, an order, a bulk update), the
     * version no longer matches and the update is refused with 409 instead of
     * overwriting that change. Hibernate's own version check (... where id=? and
     * version=?) still covers two saves that reach the database at the same moment.
     */
    @Transactional
    public ProductResponse update(int id, ProductRequest request) {
        Product product = products.findById(id)
                .orElseThrow(() -> new NotFoundException("Product " + id + " not found"));
        if (request.version() == null) {
            throw new BadRequestException("version is required: send the version of the product you loaded");
        }
        if (request.version() != product.getVersion()) {
            throw new ConflictException("Product " + id + " was changed by someone else (current version "
                    + product.getVersion() + ", you sent " + request.version() + "). Reload it and try again.");
        }
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
    // ---------------------------------------------------------------------
    // Bulk operations (assignment 13)
    // ---------------------------------------------------------------------

    /**
     * Changes the price of every product in the category with one JPQL UPDATE.
     * The class is read-only by default, so this method needs its own @Transactional:
     * a bulk update must run inside a writable transaction.
     */
    @Transactional
    public int changePricesInCategory(int categoryId, BigDecimal factor) {
        category(categoryId);   // 400 if the category doesn't exist
        return products.updatePriceByCategory(factor, categoryId);
    }

        /**
     * Adds stock to every product matching the given conditions, with one Criteria API
     * UPDATE (ProductRepositoryCustomImpl). At least one condition is required:
     * without any, the update would hit every product in the table.
     */
    @Transactional
    public int restock(int amount, Integer categoryId, Integer supplierId, Integer stockBelow) {
        if (categoryId == null && supplierId == null && stockBelow == null) {
            throw new BadRequestException("Give at least one condition: categoryId, supplierId or stockBelow");
        }
        return products.addStock(amount, categoryId, supplierId, stockBelow);
    }



    // ---------------------------------------------------------------------
    // N:M Product <-> Supplier (join table product_suppliers)
    // ---------------------------------------------------------------------

    /** All suppliers of a product: reads Product.suppliers (LAZY, loaded here). */
    public List<SupplierResponse> suppliersOf(int productId) {
        Product product = findProduct(productId);
        return product.getSuppliers().stream()
                .sorted(Comparator.comparing(Supplier::getId))
                .map(s -> SupplierResponse.from(s, isPrimary(product, s)))
                .toList();
    }

    /** All products of a supplier, through the join table (ProductRepository.findBySupplierId). */
    public List<SupplierProductResponse> productsOf(int supplierId) {
        if (!suppliers.existsById(supplierId)) {
            throw new NotFoundException("Supplier " + supplierId + " not found");
        }
        return products.findBySupplierId(supplierId).stream()
                .sorted(Comparator.comparing(Product::getId))
                .map(p -> SupplierProductResponse.from(p, supplierId))
                .toList();
    }

    /**
     * Adds a row to product_suppliers. Only the owning side (Product.suppliers) is
     * written to the database; addSupplier also updates Supplier.products in memory.
     */
    @Transactional
    public List<SupplierResponse> addSupplier(int productId, int supplierId) {
        Product product = findProduct(productId);
        Supplier supplier = suppliers.findById(supplierId)
                .orElseThrow(() -> new NotFoundException("Supplier " + supplierId + " not found"));
        if (product.getSuppliers().contains(supplier)) {
            throw new ConflictException("Supplier " + supplierId + " is already linked to product " + productId);
        }
        product.addSupplier(supplier);
        products.flush();
        return suppliersOf(productId);
    }

    /** Deletes only that one row from product_suppliers; the supplier itself stays. */
    @Transactional
    public void removeSupplier(int productId, int supplierId) {
        Product product = findProduct(productId);
        Supplier supplier = product.getSuppliers().stream()
                .filter(s -> s.getId() == supplierId)
                .findFirst()
                .orElseThrow(() -> new NotFoundException(
                        "Supplier " + supplierId + " is not linked to product " + productId));
        product.removeSupplier(supplier);
    }

    private Product findProduct(int id) {
        return products.findById(id)
                .orElseThrow(() -> new NotFoundException("Product " + id + " not found"));
    }

    private static boolean isPrimary(Product product, Supplier supplier) {
        return product.getSupplier() != null && product.getSupplier().getId().equals(supplier.getId());
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
