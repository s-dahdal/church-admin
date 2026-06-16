package com.churchadmin.repositories;

import com.churchadmin.models.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MemberRepository extends JpaRepository<Member, String> {

    List<Member> findByStatus(Member.MemberStatus status);

    List<Member> findByHouseholdGroup(String householdGroup);

    List<Member> findByCategory(String category);

    @Query("SELECT m FROM Member m WHERE LOWER(m.fullName) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(m.email) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR m.phone LIKE CONCAT('%', :query, '%')")
    List<Member> search(@Param("query") String query);

    long countByStatus(Member.MemberStatus status);
}
