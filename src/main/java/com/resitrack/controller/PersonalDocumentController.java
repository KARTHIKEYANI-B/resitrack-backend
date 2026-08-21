package com.resitrack.controller;

import com.resitrack.dto.ApiResponse;
import com.resitrack.dto.PersonalDocumentDTO;
import com.resitrack.entity.Resident;
import com.resitrack.service.PersonalDocumentService;
import com.resitrack.service.ResidentService;
import com.resitrack.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Personal Management (Phase 3) — Documents. Ownership is always resolved
 * from the JWT-backed {@link Authentication} principal, never from a
 * client-supplied id, so a resident can only ever read, upload against, or
 * delete their own documents. Downloads stream through this authenticated
 * endpoint rather than a public static URL, since these files are sensitive.
 */
@RestController
@RequestMapping("/user/documents")
@PreAuthorize("hasRole('USER')")
@RequiredArgsConstructor
public class PersonalDocumentController {

    private final PersonalDocumentService documentService;
    private final ResidentService         residentService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PersonalDocumentDTO.Response>>> getMine(
            @RequestParam(required = false) String relatedRecordType,
            @RequestParam(required = false) Long relatedRecordId,
            Authentication auth) {
        Resident r = residentService.getByEmail(auth.getName());
        List<PersonalDocumentDTO.Response> result =
                (relatedRecordType != null && relatedRecordId != null)
                        ? documentService.getForRelatedRecord(r.getId(), relatedRecordType.toUpperCase(), relatedRecordId)
                        : documentService.getMine(r.getId());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PersonalDocumentDTO.Response>> getOne(
            @PathVariable Long id, Authentication auth) {
        Resident r = residentService.getByEmail(auth.getName());
        return ResponseEntity.ok(ApiResponse.success(documentService.getById(id, r.getId())));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long id, Authentication auth) {
        Resident r = residentService.getByEmail(auth.getName());
        StringBuilder fileName = new StringBuilder();
        StringBuilder mimeType = new StringBuilder();
        byte[] bytes = documentService.download(id, r.getId(), fileName, mimeType);

        String safeName = fileName.length() > 0 ? fileName.toString() : ("document-" + id);
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(mimeType.toString());
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + safeName + "\"")
                .contentType(mediaType)
                .body(bytes);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<PersonalDocumentDTO.Response>> upload(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "documentName", required = false) String documentName,
            @RequestParam(value = "documentType", required = false) String documentType,
            @RequestParam(value = "relatedRecordType", required = false) String relatedRecordType,
            @RequestParam(value = "relatedRecordId", required = false) String relatedRecordId,
            @RequestParam(value = "expiryDate", required = false) String expiryDate,
            @RequestParam(value = "status", required = false) String status,
            Authentication auth) {

        if (documentType == null || documentType.isBlank()) {
            throw new CustomException("Document type is required", org.springframework.http.HttpStatus.BAD_REQUEST);
        }

        Resident r = residentService.getByEmail(auth.getName());

        PersonalDocumentDTO.Request req = new PersonalDocumentDTO.Request();
        req.setDocumentName(documentName);
        req.setDocumentType(documentType);
        req.setRelatedRecordType(relatedRecordType);
        req.setRelatedRecordId(parseLong(relatedRecordId));
        req.setExpiryDate(parseDate(expiryDate));
        req.setStatus(status);

        PersonalDocumentDTO.Response created = documentService.add(r.getId(), req, file);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Document uploaded", created));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> remove(@PathVariable Long id, Authentication auth) {
        Resident r = residentService.getByEmail(auth.getName());
        documentService.remove(id, r.getId());
        return ResponseEntity.ok(ApiResponse.success("Document removed", null));
    }

    private Long parseLong(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try { return Long.parseLong(raw.trim()); } catch (Exception e) { return null; }
    }

    private java.time.LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try { return java.time.LocalDate.parse(raw.trim()); } catch (Exception e) { return null; }
    }
}
