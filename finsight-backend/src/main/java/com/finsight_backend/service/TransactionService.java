package com.finsight_backend.service;

import com.finsight_backend.entity.Transaction;
import com.finsight_backend.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final CurrentUserService currentUserService;
    private final CategoryService categoryService;

    public TransactionService(TransactionRepository transactionRepository, CurrentUserService currentUserService, CategoryService categoryService) {
        this.transactionRepository = transactionRepository;
        this.currentUserService = currentUserService;
        this.categoryService = categoryService;
    }

    public Transaction createTransaction(Transaction transaction) {
        Transaction created = new Transaction();
        created.setAmount(transaction.getAmount());
        created.setType(transaction.getType());
        created.setDate(transaction.getDate());
        created.setDescription(transaction.getDescription());
        created.setUser(currentUserService.getCurrentUser());
        created.setCategory(categoryService.getCategoryById(transaction.getCategory() == null ? null : transaction.getCategory().getId()));
        return transactionRepository.save(created);
    }

    @Transactional(readOnly = true)
    public List<Transaction> getAllTransactions() {
        return transactionRepository.findAllByUserId(currentUserService.getCurrentUser().getId());
    }

    @Transactional(readOnly = true)
    public Transaction getTransactionById(Long id) {
        return transactionRepository.findByIdAndUserId(id, currentUserService.getCurrentUser().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));
    }

    public Transaction updateTransaction(Long id, Transaction updatedTransaction) {

        Transaction existingTransaction = getTransactionById(id);

        existingTransaction.setAmount(updatedTransaction.getAmount());
        existingTransaction.setType(updatedTransaction.getType());
        existingTransaction.setDate(updatedTransaction.getDate());
        existingTransaction.setDescription(updatedTransaction.getDescription());
        existingTransaction.setCategory(categoryService.getCategoryById(updatedTransaction.getCategory() == null ? null : updatedTransaction.getCategory().getId()));

        return transactionRepository.save(existingTransaction);
    }

    public void deleteTransaction(Long id) {
        Transaction transaction = getTransactionById(id);
        transactionRepository.delete(transaction);
    }
}