package com.resitrack.service;

import com.resitrack.entity.InsuranceDetail;
import com.resitrack.entity.LicenseDetail;
import com.resitrack.entity.Notification;
import com.resitrack.entity.PersonalDocument;
import com.resitrack.entity.PersonalReminder;
import com.resitrack.entity.Resident;
import com.resitrack.entity.TaxCategory;
import com.resitrack.repository.InsuranceDetailRepository;
import com.resitrack.repository.LicenseDetailRepository;
import com.resitrack.repository.NotificationRepository;
import com.resitrack.repository.PersonalDocumentRepository;
import com.resitrack.repository.PersonalReminderRepository;
import com.resitrack.repository.ResidentRepository;
import com.resitrack.repository.TaxCategoryRepository;
import com.resitrack.util.ExpiryStatusUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderSchedulerService {

    private final ResidentRepository         residentRepo;
    private final NotificationRepository     notifRepo;
    private final TaxCategoryRepository      taxCategoryRepo;
    private final InsuranceDetailRepository  insuranceRepo;
    private final LicenseDetailRepository    licenseRepo;
    private final PersonalDocumentRepository documentRepo;
    private final PersonalReminderRepository personalReminderRepo;

    @Scheduled(cron = "0 0 8 * * *")
    public void checkAndSendReminders() {
        LocalDate today      = LocalDate.now();
        LocalDate targetDate = today.plusDays(2);
        log.info("ReminderScheduler: checking reminders for target date {}", targetDate);

        List<Resident> allOwners = residentRepo.findAllActiveApprovedOwners();

        for (Resident r : allOwners) {
            sendInsuranceReminderIfDue(r, targetDate);
            if (r.isTaxesReminderEnabled()) {
                sendTaxesRemindersIfDue(r, targetDate);
            }
            sendCustomTaxCategoryRemindersIfDue(r, today, targetDate);
        }

        // ── Personal Management (Phase 3): Insurance / License / Document
        // expiry reminders, plus manually-created personal reminders. Same
        // single scheduled entry point — no second scheduler or notification
        // system is created. Scoped to ANY resident role (owner or family
        // member), since these records are personal, not tied to the
        // flat/property the way the legacy reminders above are.
        checkPersonalManagementExpiries(today);
    }

    // ── Personal Management expiry reminders (Phase 3) + manual reminders ──

    private void checkPersonalManagementExpiries(LocalDate today) {
        List<Resident> allResidents = residentRepo.findAllActiveApprovedResidents();

        for (Resident r : allResidents) {
            for (InsuranceDetail p : insuranceRepo.findByResidentIdAndActiveTrueOrderByCreatedAtDesc(r.getId())) {
                if (ExpiryStatusUtil.isManualOverride(p.getStatus())) continue; // Cancelled/Suspended — no reminders
                maybeSendExpiryReminder(r, today, p.getExpiryDate(), "INSURANCE", p.getId(),
                        p.getInsuranceType() + " insurance policy (" + p.getPolicyNumber() + ")");
            }

            for (LicenseDetail l : licenseRepo.findByResidentIdAndActiveTrueOrderByCreatedAtDesc(r.getId())) {
                if (ExpiryStatusUtil.isManualOverride(l.getStatus())) continue;
                maybeSendExpiryReminder(r, today, l.getExpiryDate(), "LICENSE", l.getId(),
                        l.getLicenseType() + " (" + l.getLicenseNumber() + ")");
            }

            for (PersonalDocument d : documentRepo.findByResidentIdAndActiveTrueOrderByCreatedAtDesc(r.getId())) {
                if (ExpiryStatusUtil.isManualOverride(d.getStatus())) continue;
                maybeSendExpiryReminder(r, today, d.getExpiryDate(), "DOCUMENT", d.getId(),
                        "document '" + d.getDocumentName() + "'");
            }

            for (PersonalReminder pr : personalReminderRepo
                    .findByResidentIdAndReminderDateAndActiveTrueAndCompletedFalse(r.getId(), today)) {
                sendPersonalReminderIfDue(r, pr);
            }
        }
    }

    private void sendPersonalReminderIfDue(Resident r, PersonalReminder reminder) {
        boolean alreadySent = notifRepo.existsExpiryReminder(
                r.getId(), Notification.NotificationType.REMINDER,
                "PERSONAL_REMINDER", reminder.getId(), 0);
        if (alreadySent) return;

        notifRepo.save(Notification.builder()
                .title(reminder.getTitle())
                .message("Reminder: " + reminder.getTitle()
                        + (reminder.getNotes() != null && !reminder.getNotes().isBlank()
                                ? " — " + reminder.getNotes() : ""))
                .type(Notification.NotificationType.REMINDER)
                .targetResidentId(r.getId())
                .residentName(r.getFullName())
                .flatNumber(r.getFlatNumber())
                .recipientRole("USER")
                .relatedRecordType("PERSONAL_REMINDER")
                .relatedRecordId(reminder.getId())
                .reminderDays(0)
                .isRead(false)
                .build());

        log.info("Sent personal reminder to resident {} for reminder #{}", r.getId(), reminder.getId());
    }

    /**
     * Fires at most once per (resident, record, checkpoint) — checkpoints are
     * 30 / 15 / 7 days before expiry, plus 0 (on the expiry date itself).
     */
    private void maybeSendExpiryReminder(Resident r, LocalDate today, LocalDate expiryDate,
                                          String relatedRecordType, Long relatedRecordId, String subject) {
        if (expiryDate == null) return;
        if (expiryDate.isBefore(today)) return; // already past the on-expiry checkpoint — nothing new to fire

        long daysLeft = ChronoUnit.DAYS.between(today, expiryDate);
        int checkpoint = -1;
        for (int c : ExpiryStatusUtil.REMINDER_CHECKPOINTS) {
            if (daysLeft == c) { checkpoint = c; break; }
        }
        if (checkpoint == -1) return; // not one of the 30/15/7/0 checkpoints today

        boolean alreadySent = notifRepo.existsExpiryReminder(
                r.getId(), Notification.NotificationType.EXPIRY_REMINDER,
                relatedRecordType, relatedRecordId, checkpoint);
        if (alreadySent) return;

        String message = checkpoint == 0
                ? "Dear " + r.getFullName() + ", your " + subject + " has expired."
                : "Dear " + r.getFullName() + ", your " + subject + " will expire in " + checkpoint + " days.";

        notifRepo.save(Notification.builder()
                .title("Expiry Reminder")
                .message(message)
                .type(Notification.NotificationType.EXPIRY_REMINDER)
                .targetResidentId(r.getId())
                .residentName(r.getFullName())
                .flatNumber(r.getFlatNumber())
                .recipientRole("USER")
                .relatedRecordType(relatedRecordType)
                .relatedRecordId(relatedRecordId)
                .reminderDays(checkpoint)
                .isRead(false)
                .build());

        log.info("Sent expiry reminder ({} days) to resident {} for {} #{}",
                checkpoint, r.getId(), relatedRecordType, relatedRecordId);
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

    // ── Custom Tax Categories (owner-defined, dynamic — separate from the
    // 4 legacy fixed tax fields above, which remain untouched) ─────────────
    private void sendCustomTaxCategoryRemindersIfDue(Resident r, LocalDate today, LocalDate targetDate) {
        List<TaxCategory> categories = taxCategoryRepo.findByResidentIdAndActiveTrueOrderByDueDateAsc(r.getId());

        for (TaxCategory t : categories) {
            // Fire on the owner-chosen reminder date if set; otherwise fall
            // back to the same "2 days before due date" convention used by
            // the legacy tax/insurance reminders above.
            boolean isDue = t.getReminderDate() != null
                    ? today.equals(t.getReminderDate())
                    : targetDate.equals(t.getDueDate());
            if (!isDue) continue;

            boolean alreadySent = notifRepo.existsByTargetResidentIdAndTypeAndTitleContainingAndCreatedAtDate(
                    r.getId(),
                    Notification.NotificationType.TAXES_REMINDER,
                    t.getTaxName(),
                    today);
            if (alreadySent) continue;

            String typeInfo = t.getTaxType() != null && !t.getTaxType().isBlank()
                    ? " (" + t.getTaxType() + ")" : "";
            String descInfo = t.getDescription() != null && !t.getDescription().isBlank()
                    ? " — " + t.getDescription() : "";

            notifRepo.save(Notification.builder()
                    .title(t.getTaxName() + " Due Reminder")
                    .message("Dear " + r.getFullName()
                            + ", your " + t.getTaxName() + typeInfo
                            + " payment is due on " + t.getDueDate() + descInfo
                            + ". Please ensure timely payment to avoid penalties.")
                    .type(Notification.NotificationType.TAXES_REMINDER)
                    .targetResidentId(r.getId())
                    .residentName(r.getFullName())
                    .flatNumber(r.getFlatNumber())
                    .recipientRole("USER")
                    .isRead(false)
                    .build());

            log.info("Sent custom tax category reminder '{}' to resident {} (flat {})",
                    t.getTaxName(), r.getFullName(), r.getFlatNumber());
        }
    }
}