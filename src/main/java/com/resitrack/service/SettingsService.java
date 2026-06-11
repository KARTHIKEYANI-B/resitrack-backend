package com.resitrack.service;

import com.resitrack.entity.AppSettings;
import com.resitrack.repository.AppSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SettingsService {

    private final AppSettingsRepository settingsRepo;

    public AppSettings getSettings() {
        return settingsRepo.findFirstByOrderByIdAsc()
                .orElseGet(() -> settingsRepo.save(AppSettings.builder()
                        .defaultMaintenanceAmount(3000.0)
                        .defaultDueDay(10)
                        .recurringCycle(AppSettings.RecurringCycle.MONTHLY)
                        .penaltyPercentage(5.0)
                        .lateFeeAmount(100.0)
                        .gracePeriodDays(5)
                        .currencyFormat("INR")
                        .receiptFooterText("Thank you for your timely payment.")
                        .apartmentName("R R Dhurya Owners Welfare Association")
                        .contactEmail("admin@rrdhurya.in")
                        .contactPhone("")
                        .apartmentAddress("")
                        .build()));
    }

    public AppSettings updateSettings(AppSettings updated) {
        AppSettings current = getSettings();

        current.setDefaultMaintenanceAmount(updated.getDefaultMaintenanceAmount());
        current.setDefaultDueDay(updated.getDefaultDueDay());
        current.setRecurringCycle(updated.getRecurringCycle());
        current.setPenaltyPercentage(updated.getPenaltyPercentage());
        current.setLateFeeAmount(updated.getLateFeeAmount());
        current.setGracePeriodDays(updated.getGracePeriodDays());
        current.setCurrencyFormat(updated.getCurrencyFormat());
        current.setReceiptFooterText(updated.getReceiptFooterText());
        current.setApartmentName(updated.getApartmentName());
        current.setApartmentAddress(updated.getApartmentAddress());
        current.setContactPhone(updated.getContactPhone());
        current.setContactEmail(updated.getContactEmail());

        return settingsRepo.save(current);
    }
}
