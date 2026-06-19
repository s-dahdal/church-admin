package com.churchadmin.repositories;

import com.churchadmin.models.Transaction;
import com.churchadmin.models.TransactionCategory;
import com.churchadmin.models.enums.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionCategoryRepository extends JpaRepository<TransactionCategory, String> {

    List<TransactionCategory> findByType(TransactionType type);

    List<TransactionCategory> findByIsDefaultTrue();
}
