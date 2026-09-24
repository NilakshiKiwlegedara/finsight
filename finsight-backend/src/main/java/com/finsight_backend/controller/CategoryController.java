package com.finsight_backend.controller;

import com.finsight_backend.entity.Category;
import com.finsight_backend.service.CategoryService;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import com.finsight_backend.dto.CategoryRequest;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PostMapping
    public Category createCategory(@Valid @RequestBody CategoryRequest category) {
        return categoryService.createCategory(category);
    }

    @GetMapping
    public List<Category> getAllCategories() {
        return categoryService.getAllCategories();
    }
}