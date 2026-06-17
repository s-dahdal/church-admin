package com.churchadmin.repositories;

import com.churchadmin.models.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, String> {

    List<Transaction> findByMemberId(String memberId);

    boolean existsByMemberId(String memberId);

    List<Transaction> findByType(Transaction.TransactionType type);

    List<Transaction> findByDateBetween(LocalDate from, LocalDate to);

    List<Transaction> findByCategoryId(String categoryId);

    List<Transaction> findByMemberIdIsNull();

    List<Transaction> findByMemberIdIsNotNull();

    List<Transaction> findTop10ByOrderByDateDesc();
}
