package com.resitrack.repository;

import com.resitrack.entity.PersonalDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PersonalDocumentRepository extends JpaRepository<PersonalDocument, Long> {

    List<PersonalDocument> findByResidentIdAndActiveTrueOrderByCreatedAtDesc(Long residentId);

    List<PersonalDocument> findByResidentIdAndRelatedRecordTypeAndRelatedRecordIdAndActiveTrueOrderByCreatedAtDesc(
            Long residentId, String relatedRecordType, Long relatedRecordId);

    Optional<PersonalDocument> findByIdAndResidentId(Long id, Long residentId);
}
