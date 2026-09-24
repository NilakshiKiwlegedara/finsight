package com.finsight_backend.service;

import com.finsight_backend.entity.Budget;
import com.finsight_backend.repository.BudgetRepository;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final CurrentUserService currentUserService;
    private final CategoryService categoryService;

    public BudgetService(BudgetRepository budgetRepository, CurrentUserService currentUserService, CategoryService categoryService) {
        this.budgetRepository = budgetRepository;
        this.currentUserService = currentUserService;
        this.categoryService = categoryService;
    }

    public Budget createBudget(Budget budget) {
        Budget created = new Budget();
        created.setAmount(budget.getAmount());
        created.setMonth(budget.getMonth());
        created.setYear(budget.getYear());
        created.setUser(currentUserService.getCurrentUser());
        created.setCategory(categoryService.getCategoryById(budget.getCategory() == null ? null : budget.getCategory().getId()));
        return budgetRepository.save(created);
    }

    @Transactional(readOnly = true)
    public List<Budget> getAllBudgets() {
        return budgetRepository.findAllByUserId(currentUserService.getCurrentUser().getId());
    }

    @Transactional(readOnly = true)
    public Budget getBudgetById(Long id) {
        return budgetRepository.findByIdAndUserId(id, currentUserService.getCurrentUser().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Budget not found"));
    }

    public Budget updateBudget(Long id, Budget updatedBudget) {

        Budget existingBudget = getBudgetById(id);

        existingBudget.setAmount(updatedBudget.getAmount());
        existingBudget.setMonth(updatedBudget.getMonth());
        existingBudget.setYear(updatedBudget.getYear());
        existingBudget.setCategory(categoryService.getCategoryById(updatedBudget.getCategory() == null ? null : updatedBudget.getCategory().getId()));

        return budgetRepository.save(existingBudget);
    }

    public void deleteBudget(Long id) {
        Budget budget = getBudgetById(id);
        budgetRepository.delete(budget);
    }
}