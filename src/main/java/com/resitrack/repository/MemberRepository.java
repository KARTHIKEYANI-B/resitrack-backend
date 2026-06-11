package com.resitrack.repository;

import com.resitrack.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {

    List<Member> findAllByActiveTrueOrderByPositionAsc();

    Optional<Member> findByPosition(Member.Position position);

    Optional<Member> findByResidentId(Long residentId);

    boolean existsByPosition(Member.Position position);

    @Query("SELECT m FROM Member m WHERE m.active = true ORDER BY m.position ASC")
    List<Member> findAllActiveOrdered();
}