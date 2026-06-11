package com.resitrack.repository;

import com.resitrack.entity.Complaint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ComplaintRepository extends JpaRepository<Complaint, Long> {
    List<Complaint> findAllByOrderByCreatedAtDesc();
    List<Complaint> findByResidentIdOrderByCreatedAtDesc(Long residentId);
    List<Complaint> findByStatusOrderByCreatedAtDesc(Complaint.ComplaintStatus status);
    long countByStatus(Complaint.ComplaintStatus status);
}