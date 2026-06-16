package com.churchadmin.services;

import com.churchadmin.models.Member;
import com.churchadmin.models.Transaction;
import com.churchadmin.models.enums.MemberStatus;
import com.churchadmin.repositories.MemberRepository;
import com.churchadmin.repositories.TransactionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository    memberRepository;
    private final TransactionRepository transactionRepository;
    private final EntityManager       entityManager;

    // ── Queries ───────────────────────────────────────────────────────────────

    public List<Member> findAll() {
        return memberRepository.findAllByOrderByFullNameAsc();
    }

    public List<Member> findActive() {
        return memberRepository.findByStatus(MemberStatus.ACTIVE);
    }

    public List<Member> search(String query) {
        if (query == null || query.isBlank()) return findAll();
        return memberRepository.search(query.trim());
    }

    public Member findById(String id) {
        return memberRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Member not found: " + id));
    }

    public Optional<Member> findByMemberNumber(String memberNumber) {
        return memberRepository.findByMemberNumber(memberNumber);
    }

    public List<Member> findByParish(String parish) {
        return memberRepository.findByParish(parish);
    }

    public long countActive() {
        return memberRepository.countByStatus(MemberStatus.ACTIVE);
    }

    // ── Writes ────────────────────────────────────────────────────────────────

    @Transactional
    public Member save(Member member) {
        if (member.getMemberNumber() == null || member.getMemberNumber().isBlank()) {
            member.setMemberNumber(generateMemberNumber());
        }
        return memberRepository.save(member);
    }

    @Transactional
    public void deactivate(String id) {
        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Member not found: " + id));
        member.setStatus(MemberStatus.INACTIVE);
        memberRepository.save(member);
    }

    public boolean canDelete(String id) {
        return !transactionRepository.existsByMemberId(id);
    }

    @Transactional
    public void delete(String id) {
        if (transactionRepository.existsByMemberId(id)) {
            throw new IllegalStateException("Member has transactions and cannot be deleted.");
        }
        memberRepository.deleteById(id);
    }

    // ── Phase 3 stub ──────────────────────────────────────────────────────────

    /** Stub — replaced in Phase 3. */
    public List<Transaction> getTransactionsByMember(String memberId) {
        return Collections.emptyList();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String generateMemberNumber() {
        Number nextVal = (Number) entityManager
                .createNativeQuery("SELECT next_val FROM member_number_seq")
                .getSingleResult();
        entityManager
                .createNativeQuery("UPDATE member_number_seq SET next_val = next_val + 1")
                .executeUpdate();
        int seq = nextVal.intValue();
        String year = String.valueOf(LocalDate.now().getYear());
        return year + "-" + String.format("%03d", seq);
    }
}
