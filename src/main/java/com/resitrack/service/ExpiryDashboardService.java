package com.resitrack.service;

import com.resitrack.dto.ExpiryDashboardDTO;
import com.resitrack.dto.ExpiryItemDTO;
import com.resitrack.entity.InsuranceDetail;
import com.resitrack.entity.LicenseDetail;
import com.resitrack.entity.PersonalDocument;
import com.resitrack.repository.InsuranceDetailRepository;
import com.resitrack.repository.LicenseDetailRepository;
import com.resitrack.repository.PersonalDocumentRepository;
import com.resitrack.util.ExpiryStatusUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregates Insurance, License, and Document records into the Active /
 * Expiring Soon / Expired buckets shown on the Personal Management → Expiry
 * Management dashboard.
 *
 * Read-only against the existing Phase 2 repositories — does not modify or
 * redesign Insurance/License in any way. Records whose manual status is a
 * meaningful override (Cancelled / Suspended) are intentionally left out of
 * all three buckets here (they are not "expiry-tracked" in the dashboard
 * sense) but remain fully visible with their own status badge in the
 * Insurance/License/Documents list sections.
 */
@Service
@RequiredArgsConstructor
public class ExpiryDashboardService {

    private final InsuranceDetailRepository  insuranceRepo;
    private final LicenseDetailRepository    licenseRepo;
    private final PersonalDocumentRepository documentRepo;

    public ExpiryDashboardDTO getDashboard(Long residentId) {
        List<ExpiryItemDTO> all = new ArrayList<>();

        for (InsuranceDetail p : insuranceRepo.findByResidentIdAndActiveTrueOrderByCreatedAtDesc(residentId)) {
            String effective = ExpiryStatusUtil.computeEffectiveStatus(p.getStatus(), p.getExpiryDate());
            if (ExpiryStatusUtil.isManualOverride(effective)) continue;
            all.add(ExpiryItemDTO.builder()
                    .recordType("INSURANCE")
                    .recordId(p.getId())
                    .name(p.getInsuranceType() + " Insurance — " + p.getPolicyNumber())
                    .expiryDate(p.getExpiryDate())
                    .status(p.getStatus())
                    .effectiveStatus(effective)
                    .build());
        }

        for (LicenseDetail l : licenseRepo.findByResidentIdAndActiveTrueOrderByCreatedAtDesc(residentId)) {
            String effective = ExpiryStatusUtil.computeEffectiveStatus(l.getStatus(), l.getExpiryDate());
            if (ExpiryStatusUtil.isManualOverride(effective)) continue;
            all.add(ExpiryItemDTO.builder()
                    .recordType("LICENSE")
                    .recordId(l.getId())
                    .name(l.getLicenseType() + " — " + l.getLicenseNumber())
                    .expiryDate(l.getExpiryDate())
                    .status(l.getStatus())
                    .effectiveStatus(effective)
                    .build());
        }

        for (PersonalDocument d : documentRepo.findByResidentIdAndActiveTrueOrderByCreatedAtDesc(residentId)) {
            String effective = ExpiryStatusUtil.computeEffectiveStatus(d.getStatus(), d.getExpiryDate());
            if (ExpiryStatusUtil.isManualOverride(effective)) continue;
            all.add(ExpiryItemDTO.builder()
                    .recordType("DOCUMENT")
                    .recordId(d.getId())
                    .name(d.getDocumentName())
                    .expiryDate(d.getExpiryDate())
                    .status(d.getStatus())
                    .effectiveStatus(effective)
                    .build());
        }

        List<ExpiryItemDTO> active       = all.stream().filter(i -> "Active".equals(i.getEffectiveStatus())).toList();
        List<ExpiryItemDTO> expiringSoon = all.stream().filter(i -> "Expiring Soon".equals(i.getEffectiveStatus())).toList();
        List<ExpiryItemDTO> expired      = all.stream().filter(i -> "Expired".equals(i.getEffectiveStatus())).toList();

        return ExpiryDashboardDTO.builder()
                .active(active)
                .expiringSoon(expiringSoon)
                .expired(expired)
                .build();
    }
}
