package com.churchadmin.repositories;

import com.churchadmin.models.Member;
import com.churchadmin.models.enums.MemberStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MemberRepository extends JpaRepository<Member, String> {

    List<Member> findByStatus(MemberStatus status);

    List<Member> findByParish(String parish);

    List<Member> findByFullNameContainingIgnoreCase(String name);

    Optional<Member> findByMemberNumber(String memberNumber);

    List<Member> findAllByOrderByFullNameAsc();

    long countByStatus(MemberStatus status);

    @Query("SELECT m FROM Member m WHERE " +
           "LOWER(m.fullName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(m.email)    LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(m.memberNumber) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(m.parish)   LIKE LOWER(CONCAT('%', :q, '%')) " +
           "ORDER BY m.fullName ASC")
    List<Member> search(@Param("q") String query);
}
