package com.churchadmin.repositories;

import com.churchadmin.models.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, String> {

    // ── Eager-fetch variants (category + member in single query) ──────────────
    // Used wherever the UI renders category name or member name in a table cell.
    // Without JOIN FETCH these associations are lazy and throw
    // LazyInitializationException once the Hibernate session closes.

    @Query("SELECT DISTINCT t FROM Transaction t " +
           "LEFT JOIN FETCH t.category " +
           "LEFT JOIN FETCH t.member " +
           "ORDER BY t.date DESC")
    List<Transaction> findAllEager();

    @Query("SELECT t FROM Transaction t " +
           "LEFT JOIN FETCH t.category " +
           "WHERE t.member.id = :memberId " +
           "ORDER BY t.date DESC")
    List<Transaction> findByMemberIdEager(@Param("memberId") String memberId);

    @Query("SELECT DISTINCT t FROM Transaction t " +
           "LEFT JOIN FETCH t.category " +
           "LEFT JOIN FETCH t.member " +
           "ORDER BY t.date DESC")
    List<Transaction> findTop10EagerOrderByDateDesc();   // limited to 5 in service layer

    // ── Standard derived queries (no association traversal needed) ────────────

    List<Transaction> findByMemberId(String memberId);

    boolean existsByMemberId(String memberId);

    List<Transaction> findByType(Transaction.TransactionType type);

    List<Transaction> findByDateBetween(LocalDate from, LocalDate to);

    List<Transaction> findByCategoryId(String categoryId);

    List<Transaction> findByMemberIdIsNull();

    List<Transaction> findByMemberIdIsNotNull();
}
