package com.resitrack.service;

import com.resitrack.dto.TaxCategoryDTO;
import com.resitrack.entity.Resident;
import com.resitrack.entity.TaxCategory;
import com.resitrack.exception.CustomException;
import com.resitrack.repository.ResidentRepository;
import com.resitrack.repository.TaxCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Manages custom Tax Category records for Owner residents.
 *
 * An owner may define any number of tax categories (name, type,
 * description, due date, reminder date). This service does not touch the
 * legacy fixed tax fields on Resident (ebDueDate / waterTaxDueDate /
 * buildingTaxDueDate / taxesInsuranceDueDate / taxesReminderEnabled),
 * which remain fully functional and untouched elsewhere in the codebase.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaxCategoryService {

    private final TaxCategoryRepository taxCategoryRepo;
    private final ResidentRepository    residentRepo;

    // ── Get all tax categories for the logged-in owner ─────────────────────
    public List<TaxCategoryDTO.Response> getMyTaxCategories(Long ownerId) {
        return taxCategoryRepo.findByResidentIdAndActiveTrueOrderByDueDateAsc(ownerId)
                .stream()
                .map(TaxCategoryDTO.Response::from)
                .toList();
    }

    // ── Get single tax category (must belong to owner) ─────────────────────
    public TaxCategoryDTO.Response getById(Long taxCategoryId, Long ownerId) {
        TaxCategory t = findAndVerifyOwner(taxCategoryId, ownerId);
        return TaxCategoryDTO.Response.from(t);
    }

    // ── Add tax category ────────────────────────────────────────────────────
    @Transactional
    public TaxCategoryDTO.Response addTaxCategory(Long ownerId, TaxCategoryDTO.Request req) {
        validate(req);
        Resident owner = residentRepo.findById(ownerId)
                .orElseThrow(() -> new CustomException("Owner not found", HttpStatus.NOT_FOUND));

        TaxCategory taxCategory = TaxCategory.builder()
                .resident(owner)
                .taxName(req.getTaxName().trim())
                .taxType(req.getTaxType().trim())
                .description(req.getDescription())
                .dueDate(req.getDueDate())
                .reminderDate(req.getReminderDate())
                .active(true)
                .build();

        TaxCategory saved = taxCategoryRepo.save(taxCategory);
        log.info("Tax category added: {} for owner {}", saved.getTaxName(), ownerId);
        return TaxCategoryDTO.Response.from(saved);
    }

    // ── Update tax category ─────────────────────────────────────────────────
    @Transactional
    public TaxCategoryDTO.Response updateTaxCategory(Long taxCategoryId, Long ownerId, TaxCategoryDTO.Request req) {
        TaxCategory t = findAndVerifyOwner(taxCategoryId, ownerId);
        validate(req);

        t.setTaxName(req.getTaxName().trim());
        t.setTaxType(req.getTaxType().trim());
        t.setDescription(req.getDescription());
        t.setDueDate(req.getDueDate());
        t.setReminderDate(req.getReminderDate());

        TaxCategory saved = taxCategoryRepo.save(t);
        log.info("Tax category updated: {}", saved.getId());
        return TaxCategoryDTO.Response.from(saved);
    }

    // ── Remove tax category (soft delete, keeps history/audit trail) ──────
    @Transactional
    public void removeTaxCategory(Long taxCategoryId, Long ownerId) {
        TaxCategory t = findAndVerifyOwner(taxCategoryId, ownerId);
        t.setActive(false);
        taxCategoryRepo.save(t);
        log.info("Tax category soft-deleted: {}", taxCategoryId);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private TaxCategory findAndVerifyOwner(Long taxCategoryId, Long ownerId) {
        TaxCategory t = taxCategoryRepo.findById(taxCategoryId)
                .orElseThrow(() -> new CustomException("Tax category not found", HttpStatus.NOT_FOUND));
        if (!t.getResident().getId().equals(ownerId)) {
            throw new CustomException("Access denied: this tax category does not belong to you",
                    HttpStatus.FORBIDDEN);
        }
        if (!t.isActive()) {
            throw new CustomException("Tax category has been removed", HttpStatus.NOT_FOUND);
        }
        return t;
    }

    private void validate(TaxCategoryDTO.Request req) {
        if (req.getTaxName() == null || req.getTaxName().isBlank()) {
            throw new CustomException("Tax name is required", HttpStatus.BAD_REQUEST);
        }
        if (req.getTaxType() == null || req.getTaxType().isBlank()) {
            throw new CustomException("Tax type is required", HttpStatus.BAD_REQUEST);
        }
        if (req.getDueDate() == null) {
            throw new CustomException("Due date is required", HttpStatus.BAD_REQUEST);
        }
    }
}
