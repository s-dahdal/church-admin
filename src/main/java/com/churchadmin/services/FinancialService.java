package com.churchadmin.services;

import com.churchadmin.models.Transaction;
import com.churchadmin.models.TransactionCategory;
import com.churchadmin.repositories.TransactionCategoryRepository;
import com.churchadmin.repositories.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FinancialService {

    private final TransactionRepository transactionRepository;
    private final TransactionCategoryRepository categoryRepository;

    // ---- Balance ----

    public BigDecimal getCurrentBalance() {
        return transactionRepository.getCurrentBalance();
    }

    public BigDecimal getTotalIncome(LocalDate from, LocalDate to) {
        return transactionRepository.sumByTypeAndPeriod(Transaction.TransactionType.INCOME, from, to);
    }

    public BigDecimal getTotalExpenses(LocalDate from, LocalDate to) {
        return transactionRepository.sumByTypeAndPeriod(Transaction.TransactionType.EXPENSE, from, to);
    }

    // ---- Transactions ----

    public List<Transaction> findAll() {
        return transactionRepository.findAll();
    }

    public List<Transaction> findRecent() {
        return transactionRepository.findTop10ByOrderByDateDesc();
    }

    public List<Transaction> findByPeriod(LocalDate from, LocalDate to) {
        return transactionRepository.findByDateBetween(from, to);
    }

    public List<Transaction> findByMember(String memberId) {
        return transactionRepository.findByMemberId(memberId);
    }

    public List<Transaction> findGeneralTransactions() {
        return transactionRepository.findByMemberIdIsNull();
    }

    public Optional<Transaction> findById(String id) {
        return transactionRepository.findById(id);
    }

    @Transactional
    public Transaction save(Transaction transaction) {
        return transactionRepository.save(transaction);
    }

    @Transactional
    public void delete(String id) {
        transactionRepository.deleteById(id);
    }

    // ---- Categories ----

    public List<TransactionCategory> findAllCategories() {
        return categoryRepository.findAll();
    }

    public List<TransactionCategory> findCategoriesByType(Transaction.TransactionType type) {
        return categoryRepository.findByType(type);
    }

    @Transactional
    public TransactionCategory saveCategory(TransactionCategory category) {
        return categoryRepository.save(category);
    }

    @Transactional
    public void deleteCategory(String id) {
        TransactionCategory cat = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Category not found: " + id));
        if (cat.isDefault()) {
            throw new IllegalStateException("Default categories cannot be deleted");
        }
        categoryRepository.deleteById(id);
    }
}
