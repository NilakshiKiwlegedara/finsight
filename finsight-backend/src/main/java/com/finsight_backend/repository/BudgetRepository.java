package com.finsight_backend.repository;

import com.finsight_backend.entity.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.List;
import java.util.Optional;

public interface BudgetRepository extends JpaRepository<Budget, Long> {
    List<Budget> findAllByUserId(Long userId);
    Optional<Budget> findByIdAndUserId(Long id, Long userId);

    @EntityGraph(attributePaths = "category")
    List<Budget> findAllByUserIdAndMonthAndYearOrderByIdAsc(Long userId, Integer month, Integer year);

    long countByUserId(Long userId);
}
