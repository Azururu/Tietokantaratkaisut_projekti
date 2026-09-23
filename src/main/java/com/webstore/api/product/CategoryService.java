package com.webstore.api.product;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.webstore.api.common.ConflictException;
import com.webstore.api.common.NotFoundException;

@Service
@Transactional
public class CategoryService {

    private final CategoryRepository categories;
    private final ProductRepository products;

    public CategoryService(CategoryRepository categories, ProductRepository products) {
        this.categories = categories;
        this.products = products;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list() {
        return categories.findAllByOrderByName().stream().map(CategoryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse get(int id) {
        return CategoryResponse.from(find(id));
    }

    public CategoryResponse create(CategoryRequest request) {
        return CategoryResponse.from(categories.save(new ProductCategory(request.name(), request.description())));
    }

    public CategoryResponse update(int id, CategoryRequest request) {
        ProductCategory category = find(id);
        category.setName(request.name());
        category.setDescription(request.description());
        return CategoryResponse.from(category);
    }

    /** A category that still has products can't be deleted: the products would lose their category. */
    public void delete(int id) {
        ProductCategory category = find(id);
        if (products.existsByCategoryId(id)) {
            throw new ConflictException("Category " + id + " has products and can't be deleted");
        }
        categories.delete(category);
    }

    private ProductCategory find(int id) {
        return categories.findById(id)
                .orElseThrow(() -> new NotFoundException("Category " + id + " not found"));
    }
}
