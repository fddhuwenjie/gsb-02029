package com.bookstore.controller;

import com.bookstore.dto.ApiResponse;
import com.bookstore.entity.Category;
import com.bookstore.repository.CategoryRepository;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {
    
    private final CategoryRepository categoryRepository;
    
    public CategoryController(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }
    
    @GetMapping
    public ApiResponse<?> getCategories() {
        List<Category> categories = categoryRepository.findByStatusOrderBySortOrderAsc(1);
        return ApiResponse.success(categories);
    }
    
    @GetMapping("/{id}")
    public ApiResponse<?> getCategory(@PathVariable Long id) {
        Category category = categoryRepository.findById(id).orElse(null);
        return ApiResponse.success(category);
    }
}
