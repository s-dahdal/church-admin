package com.churchadmin.repositories;

import com.churchadmin.models.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, String> {

    List<Transaction> findByMemberId(String memberId);

    List<Transaction> findByType(Transaction.TransactionType type);

    List<Transaction> findByDateBetween(LocalDate from, LocalDate to);

    List<Transaction> findByMemberIdIsNull();   // general church transactions only

    List<Transaction> findByMemberIdIsNotNull(); // member-linked only

    @Query("SELECT COALESCE(SUM(CASE WHEN t.type = 'INCOME' THEN t.amount ELSE -t.amount END), 0) FROM Transaction t")
    BigDecimal getCurrentBalance();

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.type = :type AND t.date BETWEEN :from AND :to")
    BigDecimal sumByTypeAndPeriod(@Param("type") Transaction.TransactionType type,
                                  @Param("from") LocalDate from,
                                  @Param("to") LocalDate to);

    List<Transaction> findTop10ByOrderByDateDesc();
}
