package com.finsight_backend.repository;

import com.finsight_backend.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findAllByUserId(Long userId);
    Optional<Transaction> findByIdAndUserId(Long id, Long userId);
}