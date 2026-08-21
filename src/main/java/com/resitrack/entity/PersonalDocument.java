package com.resitrack.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A document belonging to a Resident (owner or family member — personal, not
 * property-scoped). May optionally reference an existing Insurance or License
 * record (by id) rather than duplicating the physical file per record.
 */
@Entity
@Table(name = "personal_documents", indexes = {
        @Index(name = "idx_personal_doc_resident_id", columnList = "resident_id"),
        @Index(name = "idx_personal_doc_related", columnList = "related_record_type, related_record_id")
})
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class PersonalDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resident_id", nullable = false)
    private Resident resident;

    @Column(name = "document_name", nullable = false, length = 200)
    private String documentName;

    @Column(name = "document_type", nullable = false, length = 30)
    private String documentType;

    /** PERSONAL | INSURANCE | LICENSE | OTHER */
    @Column(name = "related_record_type", length = 20)
    private String relatedRecordType;

    /** id of the InsuranceDetail / LicenseDetail row this document belongs to, if any. */
    @Column(name = "related_record_id")
    private Long relatedRecordId;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "original_file_name", length = 255)
    private String originalFileName;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    /** Manual status: Active | Expired | Cancelled. Never auto-overwritten once set to Cancelled. */
    @Column(length = 20)
    private String status;

    /** Soft-delete flag — same convention as Vehicle/InsuranceDetail/LicenseDetail. */
    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
