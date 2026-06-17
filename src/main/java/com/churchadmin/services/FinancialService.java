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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FinancialService {

    private final TransactionRepository transactionRepository;
    private final TransactionCategoryRepository categoryRepository;

    // ---- Balance ----------------------------------------------------------------

    /** All-time net balance (INCOME − EXPENSE). */
    public BigDecimal getCurrentBalance() {
        return sumByType(Transaction.TransactionType.INCOME)
                .subtract(sumByType(Transaction.TransactionType.EXPENSE));
    }

    /** All-time total INCOME. */
    public BigDecimal getTotalIncomeAll() {
        return sumByType(Transaction.TransactionType.INCOME);
    }

    /** All-time total EXPENSE. */
    public BigDecimal getTotalExpenseAll() {
        return sumByType(Transaction.TransactionType.EXPENSE);
    }

    private BigDecimal sumByType(Transaction.TransactionType type) {
        return transactionRepository.findAll().stream()
                .filter(t -> t.getType() == type)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Net contribution for a single member: INCOME minus EXPENSE for their transactions.
     */
    public BigDecimal getTotalByMember(String memberId) {
        return transactionRepository.findByMemberId(memberId).stream()
                .map(t -> t.getType() == Transaction.TransactionType.INCOME
                        ? t.getAmount() : t.getAmount().negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ---- Transactions -----------------------------------------------------------

    public List<Transaction> findAll() {
        return transactionRepository.findAll();
    }

    /** Last 5 transactions ordered by date descending. */
    public List<Transaction> findRecent() {
        return transactionRepository.findTop10ByOrderByDateDesc()
                .stream().limit(5).collect(Collectors.toList());
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

    /**
     * Flexible filter — all parameters are optional (pass null to skip).
     * Applies in-memory filtering after a single findAll() call; fine at
     * current data scale.
     */
    public List<Transaction> getFilteredTransactions(
            LocalDate from,
            LocalDate to,
            Transaction.TransactionType type,
            String categoryId,
            String memberId,
            String descriptionQuery) {

        return transactionRepository.findAll().stream()
                .filter(t -> from == null || !t.getDate().isBefore(from))
                .filter(t -> to == null   || !t.getDate().isAfter(to))
                .filter(t -> type == null || t.getType() == type)
                .filter(t -> categoryId == null || categoryId.isBlank()
                        || (t.getCategory() != null && categoryId.equals(t.getCategory().getId())))
                .filter(t -> memberId == null || memberId.isBlank()
                        || (t.getMember() != null && memberId.equals(t.getMember().getId())))
                .filter(t -> {
                    if (descriptionQuery == null || descriptionQuery.isBlank()) return true;
                    String q = descriptionQuery.toLowerCase();
                    return t.getDescription() != null && t.getDescription().toLowerCase().contains(q);
                })
                .sorted(Comparator.comparing(Transaction::getDate).reversed())
                .collect(Collectors.toList());
    }

    @Transactional
    public Transaction save(Transaction transaction) {
        return transactionRepository.save(transaction);
    }

    @Transactional
    public void delete(String id) {
        transactionRepository.deleteById(id);
    }

    // ---- Categories -------------------------------------------------------------

    public List<TransactionCategory> findAllCategories() {
        return categoryRepository.findAll();
    }

    public List<TransactionCategory> findCategoriesByType(Transaction.TransactionType type) {
        return categoryRepository.findByType(type);
    }

    public Optional<TransactionCategory> findCategoryById(String id) {
        return categoryRepository.findById(id);
    }

    @Transactional
    public TransactionCategory saveCategory(TransactionCategory category) {
        return categoryRepository.save(category);
    }

    /**
     * Blocks deletion of default categories and categories that have transactions.
     */
    @Transactional
    public void deleteCategory(String id) {
        TransactionCategory cat = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Category not found: " + id));
        if (cat.isDefault()) {
            throw new IllegalStateException("category.deleteBlocked.default");
        }
        boolean inUse = transactionRepository.findAll().stream()
                .anyMatch(t -> t.getCategory() != null && id.equals(t.getCategory().getId()));
        if (inUse) {
            throw new IllegalStateException("category.deleteBlocked.inUse");
        }
        categoryRepository.deleteById(id);
    }
}