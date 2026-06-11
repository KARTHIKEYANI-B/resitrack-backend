package com.resitrack.service;

import com.resitrack.entity.Notification;
import com.resitrack.entity.Resident;
import com.resitrack.repository.NotificationRepository;
import com.resitrack.repository.ResidentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderSchedulerService {

    private final ResidentRepository    residentRepo;
    private final NotificationRepository notifRepo;

    @Scheduled(cron = "0 0 8 * * *")
    public void checkAndSendReminders() {
        LocalDate targetDate = LocalDate.now().plusDays(2);
        log.info("ReminderScheduler: checking reminders for target date {}", targetDate);

        List<Resident> allOwners = residentRepo.findAllActiveApprovedOwners();

        for (Resident r : allOwners) {
            sendInsuranceReminderIfDue(r, targetDate);
            if (r.isTaxesReminderEnabled()) {
                sendTaxesRemindersIfDue(r, targetDate);
            }
        }
    }

    private void sendInsuranceReminderIfDue(Resident r, LocalDate targetDate) {
        if (r.getInsuranceExpiryDate() == null) return;
        if (!r.getInsuranceExpiryDate().equals(targetDate)) return;

        boolean alreadySent = notifRepo.existsByTargetResidentIdAndTypeAndCreatedAtDate(
                r.getId(),
                Notification.NotificationType.INSURANCE_REMINDER,
                LocalDate.now());
        if (alreadySent) return;

        String providerInfo = r.getInsuranceProvider() != null
                ? " (" + r.getInsuranceProvider() + ")" : "";
        String numberInfo = r.getInsuranceNumber() != null
                ? " — Policy No: " + r.getInsuranceNumber() : "";

        notifRepo.save(Notification.builder()
                .title("Vehicle Insurance Expiry Reminder")
                .message("Dear " + r.getFullName()
                        + ", your vehicle insurance" + providerInfo + numberInfo
                        + " is expiring on " + r.getInsuranceExpiryDate()
                        + ". Please renew it to stay covered.")
                .type(Notification.NotificationType.INSURANCE_REMINDER)
                .targetResidentId(r.getId())
                .residentName(r.getFullName())
                .flatNumber(r.getFlatNumber())
                .recipientRole("USER")
                .isRead(false)
                .build());

        log.info("Sent insurance reminder to resident {} (flat {})", r.getFullName(), r.getFlatNumber());
    }

    private void sendTaxesRemindersIfDue(Resident r, LocalDate targetDate) {
        // EB Details
        sendTaxReminder(r, targetDate, "EB Bill",
                r.getEbDueDate(), r.getEbReferenceNumber());

        // Water Tax
        sendTaxReminder(r, targetDate, "Water Tax",
                r.getWaterTaxDueDate(), r.getWaterTaxReferenceNumber());

        // Building Tax
        sendTaxReminder(r, targetDate, "Building Tax",
                r.getBuildingTaxDueDate(), r.getBuildingTaxReferenceNumber());

        // Insurance (taxes module)
        sendTaxReminder(r, targetDate, "Insurance",
                r.getTaxesInsuranceDueDate(), r.getTaxesInsuranceReferenceNumber());
    }

    private void sendTaxReminder(Resident r, LocalDate targetDate,
                                  String taxName, LocalDate dueDate, String refNumber) {
        if (dueDate == null) return;
        if (!dueDate.equals(targetDate)) return;

        boolean alreadySent = notifRepo.existsByTargetResidentIdAndTypeAndTitleContainingAndCreatedAtDate(
                r.getId(),
                Notification.NotificationType.TAXES_REMINDER,
                taxName,
                LocalDate.now());
        if (alreadySent) return;

        String refInfo = refNumber != null && !refNumber.isBlank()
                ? " (Ref: " + refNumber + ")" : "";

        notifRepo.save(Notification.builder()
                .title(taxName + " Due Reminder")
                .message("Dear " + r.getFullName()
                        + ", your " + taxName + refInfo
                        + " payment is due on " + dueDate
                        + ". Please ensure timely payment to avoid penalties.")
                .type(Notification.NotificationType.TAXES_REMINDER)
                .targetResidentId(r.getId())
                .residentName(r.getFullName())
                .flatNumber(r.getFlatNumber())
                .recipientRole("USER")
                .isRead(false)
                .build());

        log.info("Sent {} reminder to resident {} (flat {})", taxName, r.getFullName(), r.getFlatNumber());
    }
}