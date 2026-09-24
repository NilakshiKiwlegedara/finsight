package com.finsight_backend.service;

import com.finsight_backend.entity.Category;
import com.finsight_backend.dto.CategoryRequest;
import com.finsight_backend.repository.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CurrentUserService currentUserService;

    public CategoryService(CategoryRepository categoryRepository, CurrentUserService currentUserService) {
        this.categoryRepository = categoryRepository;
        this.currentUserService = currentUserService;
    }

    public Category createCategory(CategoryRequest category) {
        Category created = new Category();
        created.setName(category.name());
        created.setType(category.type());
        created.setDefault(false);
        created.setUser(currentUserService.getCurrentUser());
        return categoryRepository.save(created);
    }

    @Transactional(readOnly = true)
    public List<Category> getAllCategories() {
        return categoryRepository.findAllByUserId(currentUserService.getCurrentUser().getId());
    }

    @Transactional(readOnly = true)
    public Category getCategoryById(Long id) {
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category id is required");
        }
        return categoryRepository.findByIdAndUserId(id, currentUserService.getCurrentUser().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
    }
}
