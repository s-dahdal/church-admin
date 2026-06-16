package com.churchadmin.services;

import com.churchadmin.models.Member;
import com.churchadmin.repositories.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;

    public List<Member> findAll() {
        return memberRepository.findAll();
    }

    public List<Member> findActive() {
        return memberRepository.findByStatus(Member.MemberStatus.ACTIVE);
    }

    public List<Member> search(String query) {
        if (query == null || query.isBlank()) return findAll();
        return memberRepository.search(query.trim());
    }

    public Optional<Member> findById(String id) {
        return memberRepository.findById(id);
    }

    public List<Member> findByHousehold(String householdGroup) {
        return memberRepository.findByHouseholdGroup(householdGroup);
    }

    public List<Member> findByCategory(String category) {
        return memberRepository.findByCategory(category);
    }

    public long countActive() {
        return memberRepository.countByStatus(Member.MemberStatus.ACTIVE);
    }

    @Transactional
    public Member save(Member member) {
        return memberRepository.save(member);
    }

    @Transactional
    public Member deactivate(String id) {
        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Member not found: " + id));
        member.setStatus(Member.MemberStatus.INACTIVE);
        return memberRepository.save(member);
    }

    @Transactional
    public void delete(String id) {
        memberRepository.deleteById(id);
    }
}
