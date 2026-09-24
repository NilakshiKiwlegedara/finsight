package com.finsight_backend.service;

import com.finsight_backend.entity.Transaction;
import com.finsight_backend.dto.TransactionRequest;
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

    public Transaction createTransaction(TransactionRequest transaction) {
        Transaction created = new Transaction();
        created.setAmount(transaction.amount());
        created.setType(transaction.type());
        created.setDate(transaction.date());
        created.setDescription(transaction.description());
        created.setUser(currentUserService.getCurrentUser());
        created.setCategory(categoryService.getCategoryById(transaction.category() == null ? null : transaction.category().id()));
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

    public Transaction updateTransaction(Long id, TransactionRequest updatedTransaction) {

        Transaction existingTransaction = getTransactionById(id);

        existingTransaction.setAmount(updatedTransaction.amount());
        existingTransaction.setType(updatedTransaction.type());
        existingTransaction.setDate(updatedTransaction.date());
        existingTransaction.setDescription(updatedTransaction.description());
        existingTransaction.setCategory(categoryService.getCategoryById(updatedTransaction.category() == null ? null : updatedTransaction.category().id()));

        return transactionRepository.save(existingTransaction);
    }

    public void deleteTransaction(Long id) {
        Transaction transaction = getTransactionById(id);
        transactionRepository.delete(transaction);
    }
}