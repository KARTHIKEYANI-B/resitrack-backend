package com.resitrack.service;

import com.resitrack.dto.NotificationRequest;
import com.resitrack.dto.PaymentResponseDTO;
import com.resitrack.entity.Admin;
import com.resitrack.entity.Maintenance;
import com.resitrack.entity.Notification;
import com.resitrack.entity.Payment;
import com.resitrack.entity.PaymentVerificationRequest;
import com.resitrack.entity.PropertyType;
import com.resitrack.entity.Resident;
import com.resitrack.exception.CustomException;
import com.resitrack.repository.AdminRepository;
import com.resitrack.repository.MaintenanceRepository;
import com.resitrack.repository.NotificationRepository;
import com.resitrack.repository.PaymentRepository;
import com.resitrack.repository.ResidentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notifRepo;
    private final ResidentRepository     residentRepo;
    private final MaintenanceRepository  maintenanceRepo;
    private final PaymentRepository      paymentRepo;
    private final AdminRepository        adminRepo;

    public List<Notification> getAllForAdmin() {
        return notifRepo.findAllAdminNotifications();
    }

    public List<Notification> getAllForAdmin(Long adminId, boolean isSuperAdmin) {
        return notifRepo.findAllAdminNotificationsForAdmin(adminId, isSuperAdmin);
    }

    public long getAdminUnreadCount() {
        return notifRepo.countUnreadForAdmin();
    }

    public long getAdminUnreadCount(Long adminId, boolean isSuperAdmin) {
        return notifRepo.countUnreadForAdminId(adminId, isSuperAdmin);
    }

    public List<Notification> getForResident(Long residentId) {
        PropertyType pt = residentRepo.findById(residentId)
                .map(Resident::getPropertyType)
                .orElse(PropertyType.FLAT);
        return notifRepo.findAllForResident(residentId, pt);
    }

    public long getResidentUnreadCount(Long residentId) {
        PropertyType pt = residentRepo.findById(residentId)
                .map(Resident::getPropertyType)
                .orElse(PropertyType.FLAT);
        return notifRepo.countUnreadForResident(residentId, pt);
    }

    public Notification markRead(Long id) {
        Notification n = notifRepo.findById(id)
                .orElseThrow(() -> new CustomException(
                        "Notification not found: " + id, HttpStatus.NOT_FOUND));
        n.setRead(true);
        return notifRepo.save(n);
    }

    public void delete(Long id) {
        if (!notifRepo.existsById(id))
            throw new CustomException("Notification not found: " + id, HttpStatus.NOT_FOUND);
        notifRepo.deleteById(id);
    }

    public Notification create(NotificationRequest req) {
        String audience = req.getAudience() != null
                ? req.getAudience().trim().toUpperCase() : "ALL";

        Long         targetResidentId   = null;
        String       residentName       = null;
        String       flatNumber         = req.getFlatNumber();
        PropertyType targetPropertyType = null;

        switch (audience) {
            case "RESIDENT" -> {
                if (req.getResidentId() == null)
                    throw new CustomException(
                            "residentId is required when audience is RESIDENT",
                            HttpStatus.BAD_REQUEST);

                Resident target = residentRepo.findById(req.getResidentId())
                        .orElseThrow(() -> new CustomException(
                                "Resident not found: " + req.getResidentId(),
                                HttpStatus.NOT_FOUND));

                if ("__DELETED__".equals(target.getPassword()) ||
                        target.getStatus() == Resident.ResidentStatus.INACTIVE)
                    throw new CustomException(
                            "Cannot send notification — resident account is inactive or deleted.",
                            HttpStatus.BAD_REQUEST);

                targetResidentId = target.getId();
                residentName     = target.getFullName();
                if (flatNumber == null) flatNumber = target.getFlatNumber();
            }
            case "FLAT"  -> targetPropertyType = PropertyType.FLAT;
            case "VILLA" -> targetPropertyType = PropertyType.VILLA;
            case "ALL"   -> { /* both targets null → broadcast to all active owners */ }
            default       -> throw new CustomException(
                    "Invalid audience '" + audience + "'. Use ALL, FLAT, VILLA, or RESIDENT.",
                    HttpStatus.BAD_REQUEST);
        }

        Notification n = Notification.builder()
                .title(req.getTitle())
                .message(req.getMessage())
                .type(req.getType() != null
                        ? Notification.NotificationType.valueOf(req.getType())
                        : Notification.NotificationType.ANNOUNCEMENT)
                .targetResidentId(targetResidentId)
                .targetPropertyType(targetPropertyType)
                .residentName(residentName)
                .flatNumber(flatNumber)
                .recipientRole("USER")   // admin → owners; invisible to admin panel
                .isRead(false)
                .build();

        return notifRepo.save(n);
    }

    public void notifyAdminNewRegistration(Resident r) {
        notifRepo.save(Notification.builder()
                .title("New Registration Request")
                .message("New resident registration: " + r.getFullName()
                        + " | " + (r.getPropertyType() != null ? r.getPropertyType().name() : "")
                        + " " + r.getFlatNumber()
                        + " | Phone: " + r.getPhone()
                        + ". Please review and approve/reject.")
                .type(Notification.NotificationType.REGISTRATION)
                .residentName(r.getFullName())
                .flatNumber(r.getFlatNumber())
                .targetResidentId(r.getId())
                .recipientRole("ADMIN")
                .isRead(false)
                .build());
    }

    /**
     * @deprecated The adminId parameter was unused. The notification is a
     *             broadcast (targetAdminId = NULL) visible to all admins.
     *             Calling this once per admin created duplicate rows.
     *             Use {@link #notifyAdminNewRegistration(Resident)} instead.
     */
    @Deprecated
    public void notifyAdminNewRegistration(Long adminId, Resident r) {
        // Delegate to the broadcast method — adminId is intentionally ignored.
        notifyAdminNewRegistration(r);
    }

    public void notifyUserApproved(Resident r) {
        notifRepo.save(Notification.builder()
                .title("Registration Approved")
                .message("Welcome " + r.getFullName()
                        + "! Your registration for "
                        + (r.getPropertyType() != null ? r.getPropertyType().name() + " " : "")
                        + r.getFlatNumber()
                        + " has been approved. You can now log in to ResiTrack.")
                .type(Notification.NotificationType.REGISTRATION)
                .targetResidentId(r.getId())
                .residentName(r.getFullName())
                .flatNumber(r.getFlatNumber())
                .recipientRole("USER")
                .isRead(false)
                .build());
    }

    public void notifyUserRejected(Resident r) {
        notifRepo.save(Notification.builder()
                .title("Registration Not Approved")
                .message("Your registration was not approved. Reason: "
                        + (r.getRejectedReason() != null
                                ? r.getRejectedReason() : "Not specified")
                        + ". Please contact the admin for assistance.")
                .type(Notification.NotificationType.REGISTRATION)
                .targetResidentId(r.getId())
                .residentName(r.getFullName())
                .flatNumber(r.getFlatNumber())
                .recipientRole("USER")
                .isRead(false)
                .build());
    }

    public void sendPaymentVerificationRequest(Payment payment) {
        Resident r = payment.getResident();
        notifRepo.save(Notification.builder()
                .title("Payment Verification Required")
                .message("Resident " + r.getFullName()
                        + " (Flat: " + r.getFlatNumber() + ")"
                        + " submitted ₹" + payment.getAmount()
                        + " via " + payment.getPaymentMethod()
                        + ". TXN: " + payment.getTransactionId() + ". Please verify.")
                .type(Notification.NotificationType.PAYMENT)
                .targetResidentId(r.getId())
                .residentName(r.getFullName())
                .flatNumber(r.getFlatNumber())
                .transactionId(payment.getTransactionId())
                .paymentId(payment.getId())
                .paymentAmount(payment.getAmount())
                .paymentMethod(payment.getPaymentMethod())
                .recipientRole("ADMIN")
                .isRead(false)
                .build());
    }

    public void sendPaymentApprovedNotification(Payment payment) {
        Resident r = payment.getResident();
        if (r == null) return;
        notifRepo.save(Notification.builder()
                .title("Payment Verified")
                .message("Your payment of ₹" + payment.getAmount()
                        + " for " + (payment.getPaymentMonth() != null
                                ? payment.getPaymentMonth() : "maintenance")
                        + " has been verified. Receipt generated.")
                .type(Notification.NotificationType.PAYMENT)
                .targetResidentId(r.getId())
                .residentName(r.getFullName())
                .flatNumber(r.getFlatNumber())
                .transactionId(payment.getTransactionId())
                .paymentId(payment.getId())
                .paymentAmount(payment.getAmount())
                .paymentMethod(payment.getPaymentMethod())
                .recipientRole("USER")
                .isRead(false)
                .build());
    }

    public void sendPaymentRejectedNotification(Payment payment) {
        Resident r = payment.getResident();
        if (r == null) return;
        notifRepo.save(Notification.builder()
                .title("Payment Verification Failed")
                .message("Your payment of ₹" + payment.getAmount()
                        + " could not be verified. Reason: "
                        + (payment.getRejectionReason() != null
                                ? payment.getRejectionReason() : "Not specified")
                        + ". Please contact admin or resubmit.")
                .type(Notification.NotificationType.PAYMENT)
                .targetResidentId(r.getId())
                .residentName(r.getFullName())
                .flatNumber(r.getFlatNumber())
                .transactionId(payment.getTransactionId())
                .paymentId(payment.getId())
                .paymentAmount(payment.getAmount())
                .paymentMethod(payment.getPaymentMethod())
                .recipientRole("USER")
                .isRead(false)
                .build());
    }

    public void sendDueReminder(Long residentId) {
        Resident r = residentRepo.findById(residentId)
                .orElseThrow(() -> new CustomException(
                        "Resident not found: " + residentId, HttpStatus.NOT_FOUND));

        if ("__DELETED__".equals(r.getPassword()) ||
                r.getStatus() == Resident.ResidentStatus.INACTIVE)
            throw new CustomException(
                    "Cannot notify — resident account is inactive or deleted.",
                    HttpStatus.BAD_REQUEST);

        Maintenance maint = maintenanceRepo
                .findFirstByActiveOrderByCreatedAtDesc(true).orElse(null);
        double amount = maint != null ? maint.getAmount().doubleValue() : 0.0;
        String month  = java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy"));
        sendDueReminderInternal(r, amount, month);
    }

    public void sendDueReminder(Resident r, double amount, String month) {
        sendDueReminderInternal(r, amount, month);
    }

    private void sendDueReminderInternal(Resident r, double amount, String month) {
        notifRepo.save(Notification.builder()
                .title("Maintenance Due Reminder")
                .message("Dear " + r.getFullName()
                        + ", your maintenance of ₹" + amount
                        + " for " + month
                        + " is pending. Please pay before the due date to avoid late fees.")
                .type(Notification.NotificationType.REMINDER)
                .targetResidentId(r.getId())
                .residentName(r.getFullName())
                .flatNumber(r.getFlatNumber())
                .recipientRole("USER")
                .isRead(false)
                .build());
    }

    public PaymentResponseDTO approvePaymentFromNotification(Long notifId) {
        Notification n = notifRepo.findById(notifId)
                .orElseThrow(() -> new CustomException(
                        "Notification not found: " + notifId, HttpStatus.NOT_FOUND));

        if (n.getPaymentId() == null)
            throw new CustomException(
                    "This notification is not linked to a payment.", HttpStatus.BAD_REQUEST);

        Payment p = paymentRepo.findById(n.getPaymentId())
                .orElseThrow(() -> new CustomException(
                        "Payment not found: " + n.getPaymentId(), HttpStatus.NOT_FOUND));

        if (p.getPaymentStatus() != Payment.PaymentStatus.PENDING_VERIFICATION)
            throw new CustomException(
                    "Payment is not in PENDING_VERIFICATION status — it may have already been processed.",
                    HttpStatus.BAD_REQUEST);

        p.setPaymentStatus(Payment.PaymentStatus.PAID);
        p.setVerificationStatus(Payment.VerificationStatus.VERIFIED);
        p.setPaymentDate(java.time.LocalDate.now());
        paymentRepo.save(p);

        n.setRead(true);
        notifRepo.save(n);

        sendPaymentApprovedNotification(p);

        return PaymentResponseDTO.from(p);
    }

    public PaymentResponseDTO rejectPaymentFromNotification(Long notifId, String reason) {
        Notification n = notifRepo.findById(notifId)
                .orElseThrow(() -> new CustomException(
                        "Notification not found: " + notifId, HttpStatus.NOT_FOUND));

        if (n.getPaymentId() == null)
            throw new CustomException(
                    "This notification is not linked to a payment.", HttpStatus.BAD_REQUEST);

        Payment p = paymentRepo.findById(n.getPaymentId())
                .orElseThrow(() -> new CustomException(
                        "Payment not found: " + n.getPaymentId(), HttpStatus.NOT_FOUND));

        if (p.getPaymentStatus() != Payment.PaymentStatus.PENDING_VERIFICATION)
            throw new CustomException(
                    "Payment is not in PENDING_VERIFICATION status — it may have already been processed.",
                    HttpStatus.BAD_REQUEST);

        p.setPaymentStatus(Payment.PaymentStatus.PENDING);
        p.setVerificationStatus(Payment.VerificationStatus.REJECTED);
        p.setRejectionReason(reason != null ? reason : "Verification failed");
        paymentRepo.save(p);

        n.setRead(true);
        notifRepo.save(n);

        sendPaymentRejectedNotification(p);

        return PaymentResponseDTO.from(p);
    }

    public void sendPaymentVerificationRequestToAdmin(PaymentVerificationRequest req) {
        Resident r = req.getResident();
        notifRepo.save(Notification.builder()
                .title("Payment Verification Request — " + req.getSubmittedName())
                .message("Resident " + req.getSubmittedName()
                        + " (Flat: " + req.getFlatNumber() + ")"
                        + " submitted ₹" + req.getPaymentAmount()
                        + ". TXN: " + req.getTransactionId()
                        + ". Screenshot attached. Please verify.")
                .type(Notification.NotificationType.PAYMENT_VERIFICATION)
                .targetResidentId(r != null ? r.getId() : null)
                .residentName(req.getSubmittedName())
                .flatNumber(req.getFlatNumber())
                .transactionId(req.getTransactionId())
                .paymentAmount(req.getPaymentAmount())
                .paymentMethod("UPI")
                .recipientRole("ADMIN")
                .isRead(false)
                .build());
    }

    public void sendCashPaymentRequestToAdmin(PaymentVerificationRequest req,
                                               Long paidToAdminId,
                                               String paidToAdminName) {
        Resident r = req.getResident();
        String title   = "Cash Payment — " + req.getSubmittedName();
        String message = "Cash payment received by: " + paidToAdminName
                + "\nResident: "    + req.getSubmittedName()
                + " | Flat: "       + req.getFlatNumber()
                + " | Phone: "      + (req.getPhoneNumber() != null ? req.getPhoneNumber() : "—")
                + "\nAmount: ₹"     + req.getPaymentAmount()
                + " | Method: CASH"
                + " | Month: "      + req.getPaymentMonth()
                + "\nPlease verify this cash payment.";

        notifRepo.save(Notification.builder()
                .title(title)
                .message(message)
                .type(Notification.NotificationType.PAYMENT_VERIFICATION)
                .targetResidentId(r != null ? r.getId() : null)
                .residentName(req.getSubmittedName())
                .flatNumber(req.getFlatNumber())
                .paymentAmount(req.getPaymentAmount())
                .paymentMethod("CASH")
                .targetAdminId(paidToAdminId)
                .recipientRole("ADMIN")
                .isRead(false)
                .build());

        adminRepo.findFirstBySuperAdminTrue().ifPresent(superAdmin -> {
            if (!superAdmin.getId().equals(paidToAdminId)) {
                notifRepo.save(Notification.builder()
                        .title(title)
                        .message(message)
                        .type(Notification.NotificationType.PAYMENT_VERIFICATION)
                        .targetResidentId(r != null ? r.getId() : null)
                        .residentName(req.getSubmittedName())
                        .flatNumber(req.getFlatNumber())
                        .paymentAmount(req.getPaymentAmount())
                        .paymentMethod("CASH")
                        .targetAdminId(superAdmin.getId())
                        .recipientRole("ADMIN")
                        .isRead(false)
                        .build());
            }
        });
    }

    public void sendBankTransferVerificationRequestToAdmin(PaymentVerificationRequest req) {
        Resident r = req.getResident();
        notifRepo.save(Notification.builder()
                .title("Bank Transfer Verification — " + req.getSubmittedName())
                .message("Resident " + req.getSubmittedName()
                        + " (Flat: " + req.getFlatNumber() + ")"
                        + " submitted bank transfer of ₹" + req.getPaymentAmount()
                        + ". Ref: " + (req.getTransactionId() != null ? req.getTransactionId() : "—")
                        + (req.getBankName() != null ? ". Bank: " + req.getBankName() : "")
                        + ". Screenshot attached. Please verify.")
                .type(Notification.NotificationType.PAYMENT_VERIFICATION)
                .targetResidentId(r != null ? r.getId() : null)
                .residentName(req.getSubmittedName())
                .flatNumber(req.getFlatNumber())
                .transactionId(req.getTransactionId())
                .paymentAmount(req.getPaymentAmount())
                .paymentMethod("BANK_TRANSFER")
                // targetAdminId = NULL → all admins see this (same as GPAY)
                .recipientRole("ADMIN")
                .isRead(false)
                .build());
    }

    public void sendPaymentVerifiedByScreenshotNotification(
            Payment payment, Resident resident) {
        if (resident == null) return;
        notifRepo.save(Notification.builder()
                .title("Payment Verified")
                .message("Your payment of ₹" + payment.getAmount()
                        + " for " + payment.getPaymentMonth()
                        + " has been verified by admin. Receipt generated."
                        + " TXN: " + payment.getTransactionId())
                .type(Notification.NotificationType.PAYMENT_APPROVED)
                .targetResidentId(resident.getId())
                .residentName(resident.getFullName())
                .flatNumber(resident.getFlatNumber())
                .transactionId(payment.getTransactionId())
                .paymentId(payment.getId())
                .paymentAmount(payment.getAmount())
                .paymentMethod(payment.getPaymentMethod())
                .recipientRole("USER")
                .isRead(false)
                .build());
    }

    public void sendPaymentRejectedByScreenshotNotification(
            PaymentVerificationRequest req) {
        Resident r = req.getResident();
        if (r == null) return;
        notifRepo.save(Notification.builder()
                .title("Payment Verification Rejected")
                .message("Your payment verification request of ₹" + req.getPaymentAmount()
                        + " (TXN: " + req.getTransactionId() + ") was rejected. Reason: "
                        + (req.getRejectionReason() != null
                                ? req.getRejectionReason()
                                : "Details could not be verified")
                        + ". Please contact admin for assistance.")
                .type(Notification.NotificationType.PAYMENT_REJECTED)
                .targetResidentId(r.getId())
                .residentName(r.getFullName())
                .flatNumber(r.getFlatNumber())
                .transactionId(req.getTransactionId())
                .paymentAmount(req.getPaymentAmount())
                .paymentMethod("UPI")
                .recipientRole("USER")
                .isRead(false)
                .build());
    }
}