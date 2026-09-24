package com.finsight_backend.service;

import com.finsight_backend.entity.Category;
import com.finsight_backend.entity.User;
import com.finsight_backend.enums.TransactionType;
import com.finsight_backend.repository.CategoryRepository;
import com.finsight_backend.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Service
public class DefaultCategoryService {
    private static final Map<String, TransactionType> DEFAULTS = Map.of(
            "Food", TransactionType.EXPENSE,
            "Transport", TransactionType.EXPENSE,
            "Shopping", TransactionType.EXPENSE,
            "Bills", TransactionType.EXPENSE,
            "Entertainment", TransactionType.EXPENSE,
            "Salary", TransactionType.INCOME);
    private final UserRepository users;
    private final CategoryRepository categories;

    public DefaultCategoryService(UserRepository users, CategoryRepository categories) {
        this.users = users;
        this.categories = categories;
    }

    @Transactional
    public void createForUser(User user) {
        // Serialize retries for the same user without introducing constraints on legacy categories.
        User owner = users.findByIdForUpdate(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        var existing = categories.findAllByUserId(owner.getId());
        DEFAULTS.forEach((name, type) -> {
            boolean present = existing.stream().anyMatch(category -> category.isDefault()
                    && name.equals(category.getName()) && type == category.getType());
            if (!present) {
                Category category = new Category();
                category.setName(name);
                category.setType(type);
                category.setDefault(true);
                category.setUser(owner);
                categories.save(category);
            }
        });
    }
}
