package com.resitrack.service;

import com.resitrack.dto.PersonalDocumentDTO;
import com.resitrack.entity.InsuranceDetail;
import com.resitrack.entity.LicenseDetail;
import com.resitrack.entity.PersonalDocument;
import com.resitrack.entity.Resident;
import com.resitrack.exception.CustomException;
import com.resitrack.repository.InsuranceDetailRepository;
import com.resitrack.repository.LicenseDetailRepository;
import com.resitrack.repository.PersonalDocumentRepository;
import com.resitrack.repository.ResidentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;

/**
 * Manages Documents for the currently authenticated resident (owner or
 * family member — personal, not property-scoped). A document may optionally
 * reference an existing Insurance or License record by id, in which case
 * that reference is ownership-validated too — a resident can never attach a
 * document to someone else's insurance/license record.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PersonalDocumentService {

    private static final Set<String> RELATED_TYPES = Set.of("PERSONAL", "INSURANCE", "LICENSE", "OTHER");

    private final PersonalDocumentRepository documentRepo;
    private final ResidentRepository         residentRepo;
    private final InsuranceDetailRepository  insuranceRepo;
    private final LicenseDetailRepository    licenseRepo;
    private final DocumentUploadService      documentUploadService;

    public List<PersonalDocumentDTO.Response> getMine(Long residentId) {
        return documentRepo.findByResidentIdAndActiveTrueOrderByCreatedAtDesc(residentId)
                .stream()
                .map(e -> PersonalDocumentDTO.Response.from(e, relatedRecordLabel(e)))
                .toList();
    }

    public List<PersonalDocumentDTO.Response> getForRelatedRecord(
            Long residentId, String relatedRecordType, Long relatedRecordId) {
        return documentRepo.findByResidentIdAndRelatedRecordTypeAndRelatedRecordIdAndActiveTrueOrderByCreatedAtDesc(
                        residentId, relatedRecordType, relatedRecordId)
                .stream()
                .map(e -> PersonalDocumentDTO.Response.from(e, relatedRecordLabel(e)))
                .toList();
    }

    public PersonalDocumentDTO.Response getById(Long id, Long residentId) {
        PersonalDocument e = findAndVerifyOwner(id, residentId);
        return PersonalDocumentDTO.Response.from(e, relatedRecordLabel(e));
    }

    /** Returns the raw file bytes for an authenticated, ownership-verified download. */
    public byte[] download(Long id, Long residentId, StringBuilder outFileName, StringBuilder outMimeType) {
        PersonalDocument e = findAndVerifyOwner(id, residentId);
        outFileName.append(e.getOriginalFileName() != null ? e.getOriginalFileName() : e.getDocumentName());
        outMimeType.append(e.getMimeType() != null ? e.getMimeType() : "application/octet-stream");
        return documentUploadService.readDocument(e.getFilePath());
    }

    @Transactional
    public PersonalDocumentDTO.Response add(Long residentId, PersonalDocumentDTO.Request req, MultipartFile file) {
        Resident owner = residentRepo.findById(residentId)
                .orElseThrow(() -> new CustomException("Resident not found", HttpStatus.NOT_FOUND));

        String relatedType = normalizeRelatedType(req.getRelatedRecordType());
        Long   relatedId   = validateRelatedRecord(residentId, relatedType, req.getRelatedRecordId());

        String relativePath = documentUploadService.saveDocument(file);

        String name = (req.getDocumentName() == null || req.getDocumentName().isBlank())
                ? file.getOriginalFilename() : req.getDocumentName().trim();

        PersonalDocument e = PersonalDocument.builder()
                .resident(owner)
                .documentName(name != null ? name : "Document")
                .documentType(req.getDocumentType().trim())
                .relatedRecordType(relatedType)
                .relatedRecordId(relatedId)
                .filePath(relativePath)
                .originalFileName(file.getOriginalFilename())
                .mimeType(file.getContentType())
                .fileSizeBytes(file.getSize())
                .expiryDate(req.getExpiryDate())
                .status(req.getStatus() == null || req.getStatus().isBlank() ? "Active" : req.getStatus().trim())
                .active(true)
                .build();

        PersonalDocument saved = documentRepo.save(e);
        log.info("Document added: '{}' for resident {}", saved.getDocumentName(), residentId);
        return PersonalDocumentDTO.Response.from(saved, relatedRecordLabel(saved));
    }

    @Transactional
    public void remove(Long id, Long residentId) {
        PersonalDocument e = findAndVerifyOwner(id, residentId);
        documentUploadService.deleteDocument(e.getFilePath());
        e.setActive(false);
        documentRepo.save(e);
        log.info("Document deleted: {}", id);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private PersonalDocument findAndVerifyOwner(Long id, Long residentId) {
        PersonalDocument e = documentRepo.findById(id)
                .orElseThrow(() -> new CustomException("Document not found", HttpStatus.NOT_FOUND));
        if (!e.getResident().getId().equals(residentId)) {
            throw new CustomException("Access denied: this document does not belong to you",
                    HttpStatus.FORBIDDEN);
        }
        if (!e.isActive()) {
            throw new CustomException("Document has been removed", HttpStatus.NOT_FOUND);
        }
        return e;
    }

    private String normalizeRelatedType(String raw) {
        if (raw == null || raw.isBlank()) return "PERSONAL";
        String upper = raw.trim().toUpperCase();
        if (!RELATED_TYPES.contains(upper)) {
            throw new CustomException(
                    "Invalid related record type. Use PERSONAL, INSURANCE, LICENSE, or OTHER.",
                    HttpStatus.BAD_REQUEST);
        }
        return upper;
    }

    /**
     * When the document is linked to an Insurance or License record, verifies
     * that record actually belongs to this resident before allowing the
     * link — a resident can never attach a document to someone else's
     * insurance/license row by guessing its id.
     */
    private Long validateRelatedRecord(Long residentId, String relatedType, Long relatedId) {
        if (relatedId == null) return null;
        switch (relatedType) {
            case "INSURANCE" -> insuranceRepo.findByIdAndResidentId(relatedId, residentId)
                    .orElseThrow(() -> new CustomException(
                            "Insurance policy not found or does not belong to you", HttpStatus.FORBIDDEN));
            case "LICENSE" -> licenseRepo.findByIdAndResidentId(relatedId, residentId)
                    .orElseThrow(() -> new CustomException(
                            "License not found or does not belong to you", HttpStatus.FORBIDDEN));
            default -> {
                return null; // PERSONAL / OTHER don't carry a cross-reference
            }
        }
        return relatedId;
    }

    private String relatedRecordLabel(PersonalDocument e) {
        if (e.getRelatedRecordId() == null || e.getRelatedRecordType() == null) return null;
        try {
            return switch (e.getRelatedRecordType()) {
                case "INSURANCE" -> insuranceRepo.findById(e.getRelatedRecordId())
                        .map(InsuranceDetail::getPolicyNumber)
                        .map(p -> "Insurance — " + p)
                        .orElse(null);
                case "LICENSE" -> licenseRepo.findById(e.getRelatedRecordId())
                        .map(LicenseDetail::getLicenseNumber)
                        .map(n -> "License — " + n)
                        .orElse(null);
                default -> null;
            };
        } catch (Exception ex) {
            return null;
        }
    }
}
