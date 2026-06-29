package com.resitrack.service;

import com.resitrack.dto.PaymentVerificationRequestDTO;
import com.resitrack.entity.*;
import com.resitrack.exception.CustomException;
import com.resitrack.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentVerificationService {

    private final PaymentVerificationRequestRepository verificationRepo;
    private final PaymentRepository                    paymentRepo;
    private final ResidentRepository                   residentRepo;
    private final MaintenanceRepository                maintenanceRepo;
    private final MaintenanceService                   maintenanceService;
    private final ReceiptRepository                    receiptRepo;
    private final NotificationService                  notificationService;
    private final AdminRepository                      adminRepo;
    private final FamilyMemberRepository               familyMemberRepo;

    @Value("${app.upload.dir:./uploads}")
    private String uploadDir;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    private static final long   MAX_BYTES      = 10L * 1024 * 1024; // 10 MB
    private static final Set<String> ALLOWED   = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp", "application/pdf");

    @Transactional
    public PaymentVerificationRequestDTO submitRequest(
            Long residentId,
            Long submittedByResidentId,
            String submittedName,
            String phoneNumber,
            BigDecimal paymentAmount,
            String transactionId,
            MultipartFile screenshot) throws IOException {

        Resident resident = resolveOwner(residentId);

        if (paymentAmount == null || paymentAmount.compareTo(BigDecimal.ZERO) <= 0)
            throw new CustomException("Payment amount must be greater than 0", HttpStatus.BAD_REQUEST);

        validateRemainingBalance(resident, paymentAmount);

        if (transactionId == null || transactionId.isBlank())
            throw new CustomException("Transaction ID is required", HttpStatus.BAD_REQUEST);

        String screenshotPath = null;
        String screenshotFileName = null;
        if (screenshot != null && !screenshot.isEmpty()) {
            screenshotPath     = saveScreenshot(screenshot);
            screenshotFileName = screenshot.getOriginalFilename();
        }

        String currentMonth = currentMonthStr();

        PaymentVerificationRequest req = PaymentVerificationRequest.builder()
                .resident(resident)
                .submittedByResidentId(submittedByResidentId)
                .submittedName(submittedName != null ? submittedName : resident.getFullName())
                .flatNumber(resident.getFlatNumber())
                .phoneNumber(phoneNumber != null ? phoneNumber : resident.getPhone())
                .paymentAmount(paymentAmount)
                .transactionId(transactionId.trim())
                .screenshotPath(screenshotPath)
                .screenshotFileName(screenshotFileName)
                .paymentMonth(currentMonth)
                .paymentMethod("GPAY")
                .status(PaymentVerificationRequest.RequestStatus.PENDING)
                .build();

        PaymentVerificationRequest saved = verificationRepo.save(req);
        notificationService.sendPaymentVerificationRequestToAdmin(saved);
        return toDTO(saved);
    }

    @Transactional
    public PaymentVerificationRequestDTO submitCashRequest(
            Long residentId,
            Long submittedByResidentId,
            String submittedName,
            String phoneNumber,
            BigDecimal paymentAmount,
            Long paidToAdminId) {

        Resident resident = resolveOwner(residentId);

        if (paymentAmount == null || paymentAmount.compareTo(BigDecimal.ZERO) <= 0)
            throw new CustomException("Payment amount must be greater than 0", HttpStatus.BAD_REQUEST);

        validateRemainingBalance(resident, paymentAmount);

        if (paidToAdminId == null)
            throw new CustomException("Please select the admin you paid cash to", HttpStatus.BAD_REQUEST);

        Admin paidToAdmin = adminRepo.findById(paidToAdminId)
                .orElseThrow(() -> new CustomException("Selected admin not found", HttpStatus.NOT_FOUND));

        String currentMonth = currentMonthStr();

        PaymentVerificationRequest req = PaymentVerificationRequest.builder()
                .resident(resident)
                .submittedByResidentId(submittedByResidentId)
                .submittedName(submittedName != null ? submittedName : resident.getFullName())
                .flatNumber(resident.getFlatNumber())
                .phoneNumber(phoneNumber != null ? phoneNumber : resident.getPhone())
                .paymentAmount(paymentAmount)
                .transactionId(null)   // CASH has no transaction ID
                .screenshotPath(null)
                .screenshotFileName(null)
                .paymentMonth(currentMonth)
                .paymentMethod("CASH")
                .paidToAdminId(paidToAdminId)
                .paidToAdminName(paidToAdmin.getName())
                .status(PaymentVerificationRequest.RequestStatus.PENDING)
                .build();

        PaymentVerificationRequest saved = verificationRepo.save(req);

        // Notify ONLY: selected admin + super admin
        notificationService.sendCashPaymentRequestToAdmin(saved, paidToAdminId, paidToAdmin.getName());

        log.info("CASH payment request {} submitted by resident {} to admin {}",
                saved.getId(), resident.getId(), paidToAdmin.getName());
        return toDTO(saved);
    }

    @Transactional
    public PaymentVerificationRequestDTO submitBankTransferRequest(
            Long residentId,
            Long submittedByResidentId,
            String submittedName,
            String phoneNumber,
            BigDecimal paymentAmount,
            String referenceId,
            String bankName,
            MultipartFile screenshot) throws IOException {

        Resident resident = resolveOwner(residentId);

        if (paymentAmount == null || paymentAmount.compareTo(BigDecimal.ZERO) <= 0)
            throw new CustomException("Payment amount must be greater than 0", HttpStatus.BAD_REQUEST);

        validateRemainingBalance(resident, paymentAmount);

        if (referenceId == null || referenceId.isBlank())
            throw new CustomException("Reference / Transaction ID is required for bank transfer",
                    HttpStatus.BAD_REQUEST);

        String screenshotPath = null;
        String screenshotFileName = null;
        if (screenshot != null && !screenshot.isEmpty()) {
            screenshotPath     = saveScreenshot(screenshot);
            screenshotFileName = screenshot.getOriginalFilename();
        }

        String currentMonth = currentMonthStr();

        PaymentVerificationRequest req = PaymentVerificationRequest.builder()
                .resident(resident)
                .submittedByResidentId(submittedByResidentId)
                .submittedName(submittedName != null ? submittedName : resident.getFullName())
                .flatNumber(resident.getFlatNumber())
                .phoneNumber(phoneNumber != null ? phoneNumber : resident.getPhone())
                .paymentAmount(paymentAmount)
                .transactionId(referenceId.trim())
                .screenshotPath(screenshotPath)
                .screenshotFileName(screenshotFileName)
                .paymentMonth(currentMonth)
                .paymentMethod("BANK_TRANSFER")
                .bankName(bankName != null && !bankName.isBlank() ? bankName.trim() : null)
                .status(PaymentVerificationRequest.RequestStatus.PENDING)
                .build();

        PaymentVerificationRequest saved = verificationRepo.save(req);

        notificationService.sendBankTransferVerificationRequestToAdmin(saved);

        log.info("BANK_TRANSFER payment request {} submitted by resident {} ref={}",
                saved.getId(), resident.getId(), referenceId);
        return toDTO(saved);
    }

    public List<PaymentVerificationRequestDTO> getAllRequests() {
        return verificationRepo.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<PaymentVerificationRequestDTO> getRequestsByStatus(String status) {
        PaymentVerificationRequest.RequestStatus s =
                PaymentVerificationRequest.RequestStatus.valueOf(status.toUpperCase());
        return verificationRepo.findByStatus(s)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public long getPendingCount() {
        return verificationRepo.countByStatus(PaymentVerificationRequest.RequestStatus.PENDING);
    }

    public List<PaymentVerificationRequestDTO> getResidentRequests(Long residentId) {
        return verificationRepo.findByResidentIdOrderByCreatedAtDesc(residentId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public PaymentVerificationRequestDTO verifyRequest(Long requestId) {
        PaymentVerificationRequest req = verificationRepo.findById(requestId)
                .orElseThrow(() -> new CustomException("Request not found", HttpStatus.NOT_FOUND));

        if (req.getStatus() != PaymentVerificationRequest.RequestStatus.PENDING)
            throw new CustomException(
                    "Request is already " + req.getStatus().name().toLowerCase(), HttpStatus.BAD_REQUEST);

        Resident resident = req.getResident();

        if (resident.getResidentRole() == Resident.ResidentRole.FAMILY_MEMBER
                && resident.getOwnerResidentId() != null) {
            Resident ownerResident = residentRepo.findById(resident.getOwnerResidentId())
                    .orElse(resident);
            log.warn("verifyRequest: request {} was stored under FM resident {} — " +
                     "resolving to owner resident {} for Payment record",
                    requestId, resident.getId(), ownerResident.getId());
            resident = ownerResident;
        }

        String method = req.getPaymentMethod() != null ? req.getPaymentMethod() : "GPAY";
        // Map internal method names to stored payment_method values
        String storedMethod = switch (method) {
            case "CASH"          -> "CASH";
            case "BANK_TRANSFER" -> "BANK_TRANSFER";
            default              -> "UPI";   // GPAY = UPI (existing behaviour preserved)
        };

        // ── Task 2 — re-validate remaining balance at VERIFY time ──────────
        //
        // validateRemainingBalance() already runs once when a resident first
        // submits a GPay/Cash/Bank-Transfer request (submitRequest /
        // submitCashRequest / submitBankTransferRequest), but it only checks
        // against payments that are ALREADY VERIFIED (PAID) at that moment —
        // a still-PENDING request never reduces what a later submission is
        // allowed to ask for, intentionally, since it might yet be rejected.
        //
        // That means two (or more) PENDING requests for the same resident +
        // month can each individually pass the submission-time check (e.g.
        // a ₹3000 bill: request A for ₹3000 submitted, then request B for
        // ₹3000 submitted before A is verified — both pass, since neither
        // sees the other as PAID yet) and then BOTH get verified here,
        // producing two PAID Payment rows that together exceed the required
        // maintenance amount — a duplicate/overpayment exactly like the
        // admin-recorded-duplicate scenario this task's cleanup targets,
        // just arriving through the screenshot-verification flow instead.
        //
        // Re-checking here, immediately before the PAID Payment row is
        // created, closes that gap: once any one PENDING request for a
        // month has been verified, every other still-PENDING request for
        // that same resident + month is rejected at verify time rather than
        // being allowed to create a second PAID row. The admin still sees
        // exactly which request failed and why, and can reject it via the
        // existing rejectRequest() flow — no other verification mechanics
        // change.
        validateRemainingBalanceAtVerification(resident, req.getPaymentMonth(), req.getPaymentAmount());

        Payment payment = Payment.builder()
                .resident(resident)
                .amount(req.getPaymentAmount())
                .lateFeeAmount(BigDecimal.ZERO)
                .paymentStatus(Payment.PaymentStatus.PAID)
                .verificationStatus(Payment.VerificationStatus.VERIFIED)
                .paymentDate(LocalDate.now())
                .paymentMethod(storedMethod)
                .transactionId(req.getTransactionId())
                .submittedResidentName(req.getSubmittedName())
                .paymentMonth(req.getPaymentMonth())
                .paymentYear(req.getPaymentMonth().substring(0, 4))
                .description("Verified from " + method.toLowerCase() + " payment submission")
                .adminCreated(false)
                .build();

        Payment savedPayment = paymentRepo.save(payment);

        generateReceipt(savedPayment);

        req.setStatus(PaymentVerificationRequest.RequestStatus.VERIFIED);
        req.setPaymentId(savedPayment.getId());
        verificationRepo.save(req);

        notificationService.sendPaymentVerifiedByScreenshotNotification(savedPayment, resident);

        log.info("Payment verification request {} verified → payment {} created for resident {}",
                requestId, savedPayment.getId(), resident.getId());
        return toDTO(req);
    }


    @Transactional
    public PaymentVerificationRequestDTO rejectRequest(Long requestId, String reason) {
        PaymentVerificationRequest req = verificationRepo.findById(requestId)
                .orElseThrow(() -> new CustomException("Request not found", HttpStatus.NOT_FOUND));

        if (req.getStatus() != PaymentVerificationRequest.RequestStatus.PENDING)
            throw new CustomException(
                    "Request is already " + req.getStatus().name().toLowerCase(), HttpStatus.BAD_REQUEST);

        req.setStatus(PaymentVerificationRequest.RequestStatus.REJECTED);
        req.setRejectionReason(reason != null && !reason.isBlank()
                ? reason : "Payment details could not be verified");
        verificationRepo.save(req);

        notificationService.sendPaymentRejectedByScreenshotNotification(req);

        log.info("Payment verification request {} rejected", requestId);
        return toDTO(req);
    }

    public Path getScreenshotPath(Long requestId) {
        PaymentVerificationRequest req = verificationRepo.findById(requestId)
                .orElseThrow(() -> new CustomException("Request not found", HttpStatus.NOT_FOUND));
        if (req.getScreenshotPath() == null)
            throw new CustomException("No screenshot attached to this request", HttpStatus.NOT_FOUND);
        return Paths.get(uploadDir).resolve(req.getScreenshotPath());
    }

    private Resident resolveOwner(Long residentId) {
        Resident resident = residentRepo.findById(residentId)
                .orElseThrow(() -> new CustomException("Resident not found", HttpStatus.NOT_FOUND));
        if (resident.getResidentRole() == Resident.ResidentRole.FAMILY_MEMBER
                && resident.getOwnerResidentId() != null) {
            Resident owner = residentRepo.findById(resident.getOwnerResidentId())
                    .orElse(resident);
            log.info("resolveOwner: FM {} -> owner {}", resident.getId(), owner.getId());
            return owner;
        }
        return resident;
    }

    private String currentMonthStr() {
        LocalDate now = LocalDate.now();
        return now.getYear() + "-" + String.format("%02d", now.getMonthValue());
    }

    /**
     * Allow Partial Maintenance Payments — the gate every owner self-service
     * submission (GPay / Cash / Bank Transfer) passes through before a new
     * PaymentVerificationRequest is even created.
     *
     *   requiredAmount = MaintenanceService.getRequiredMaintenanceAmountFor(resident)
     *   totalPaidAmount = sum of all VERIFIED (PAID) payments for this resident's
     *                     property + this month (sumPaidAmountByPropertyAndPaymentMonth —
     *                     the exact same sum Maintenance Summary / Financial Summary /
     *                     the Owner Dashboard already use, so "remaining" here can never
     *                     disagree with what those screens show).
     *   remainingAmount = requiredAmount - totalPaidAmount
     *
     * Rejects when remainingAmount <= 0 (nothing left to pay) or when this
     * new submission's amount would push total paid beyond what's required
     * (prevents an accidental/duplicate overpayment slipping through while
     * still allowing any number of partial installments up to the exact
     * remaining balance).
     *
     * Only counts VERIFIED (PAID) payments — a still-PENDING verification
     * request does not reduce what a resident is allowed to submit next,
     * since it may yet be rejected. This check runs at submission time;
     * Task 2 additionally re-runs the equivalent check at VERIFY time
     * (validateRemainingBalanceAtVerification(), below) to close the gap
     * where two still-PENDING requests for the same month could otherwise
     * both pass this submission-time check and then both be verified.
     */
    private void validateRemainingBalance(Resident resident, BigDecimal requestedAmount) {
        BigDecimal required = maintenanceService.getRequiredMaintenanceAmountFor(resident);
        if (required.compareTo(BigDecimal.ZERO) <= 0) return; // nothing configured — nothing to gate

        String currentMonth = currentMonthStr();
        Double paidRaw = paymentRepo.sumPaidAmountByPropertyAndPaymentMonth(resident.getId(), currentMonth);
        BigDecimal totalPaid = paidRaw != null ? BigDecimal.valueOf(paidRaw) : BigDecimal.ZERO;

        BigDecimal remaining = required.subtract(totalPaid);

        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            throw new CustomException(
                    "Maintenance for this month has already been fully paid.", HttpStatus.BAD_REQUEST);
        }

        if (requestedAmount.compareTo(remaining) > 0) {
            throw new CustomException(
                    "Amount exceeds the remaining balance of " + remaining.setScale(2, java.math.RoundingMode.HALF_UP)
                            + " for this month.", HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Task 2 — remaining-balance re-check at VERIFY time (closes the gap
     * validateRemainingBalance() above intentionally leaves open at
     * submission time — see the call site in verifyRequest() for the full
     * scenario this guards against).
     *
     * Same formula as validateRemainingBalance(), but:
     *   - parameterized by the REQUEST's own billing month (paymentMonth),
     *     not always "this calendar month" — a verification request's
     *     paymentMonth is set once at submission and an admin can verify it
     *     in a later calendar month, so the check must gate against the
     *     month the resident actually billed against, exactly like
     *     PaymentService.validateRemainingBalance(resident, paymentMonth, amount)
     *     already does for the Admin Manual Payment Registration flow.
     *   - throws a clear, admin-facing message identifying this as a
     *     duplicate rather than a generic "already paid" submission error,
     *     since the person seeing this message is the admin reviewing a
     *     verification request, not the resident submitting one.
     */
    private void validateRemainingBalanceAtVerification(
            Resident resident, String paymentMonth, BigDecimal requestedAmount) {
        BigDecimal required = maintenanceService.getRequiredMaintenanceAmountFor(resident);
        if (required.compareTo(BigDecimal.ZERO) <= 0) return; // nothing configured — nothing to gate

        Double paidRaw = paymentRepo.sumPaidAmountByPropertyAndPaymentMonth(resident.getId(), paymentMonth);
        BigDecimal totalPaid = paidRaw != null ? BigDecimal.valueOf(paidRaw) : BigDecimal.ZERO;

        BigDecimal remaining = required.subtract(totalPaid);

        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            throw new CustomException(
                    "Cannot verify — maintenance for " + paymentMonth + " has already been fully paid " +
                    "by another verified payment. This request appears to be a duplicate; reject it instead.",
                    HttpStatus.BAD_REQUEST);
        }

        if (requestedAmount.compareTo(remaining) > 0) {
            throw new CustomException(
                    "Cannot verify — this amount exceeds the remaining balance of "
                            + remaining.setScale(2, java.math.RoundingMode.HALF_UP)
                            + " for " + paymentMonth + ". Another payment may have already been verified " +
                            "for this month; reject this request if it is a duplicate.",
                    HttpStatus.BAD_REQUEST);
        }
    }


    private String saveScreenshot(MultipartFile file) throws IOException {
        if (file.getSize() > MAX_BYTES)
            throw new CustomException("Screenshot too large. Maximum 10 MB.", HttpStatus.BAD_REQUEST);

        String ct = file.getContentType() != null ? file.getContentType().toLowerCase() : "";
        if (!ALLOWED.contains(ct))
            throw new CustomException(
                    "Invalid file type. Allowed: JPG, PNG, WEBP, PDF", HttpStatus.BAD_REQUEST);

        String original = file.getOriginalFilename() != null ? file.getOriginalFilename() : "screenshot";
        String ext = original.contains(".")
                ? original.substring(original.lastIndexOf('.'))
                : ".jpg";
        String filename = "payment-screenshots/" + UUID.randomUUID() + ext;

        Path dest = Paths.get(uploadDir).resolve(filename);
        Files.createDirectories(dest.getParent());
        Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);
        return filename;
    }

    private void generateReceipt(Payment payment) {
        if (receiptRepo.findByPaymentId(payment.getId()).isPresent()) return;

        String receiptNo = "REC-" + LocalDate.now().getYear()
                + "-" + String.format("%06d", System.currentTimeMillis() % 1_000_000);

        Resident r  = payment.getResident();
        BigDecimal lf = payment.getLateFeeAmount() != null
                ? payment.getLateFeeAmount() : BigDecimal.ZERO;

        Receipt receipt = Receipt.builder()
                .receiptNumber(receiptNo)
                .payment(payment)
                .resident(r)
                .residentName(r.getFullName())
                .flatNumber(r.getFlatNumber())
                .residentPhone(r.getPhone())
                .paymentDate(payment.getPaymentDate())
                .paidAmount(payment.getAmount())
                .lateFeeAmount(lf)
                .paymentMethod(payment.getPaymentMethod())
                .transactionId(payment.getTransactionId())
                .apartmentName("R R Dhurya Owners Welfare Association")
                .receiptFooter("Thank you for your payment. This is a computer-generated receipt.")
                .build();

        receiptRepo.save(receipt);
    }

    private PaymentVerificationRequestDTO toDTO(PaymentVerificationRequest r) {
        String url = null;
        if (r.getScreenshotPath() != null) {
            url = baseUrl + "/api/admin/payment-verification/" + r.getId() + "/screenshot";
        }

        PaymentVerificationRequestDTO dto = PaymentVerificationRequestDTO.from(r, url);

        Resident owner = r.getResident(); // always the property owner

        String submittedByLabel;
        String ownerName;

        Long submittedById = r.getSubmittedByResidentId();

        if (submittedById != null) {
            // ── PATH A: submittedByResidentId is stored — use it directly ──
            Resident submitter = residentRepo.findById(submittedById).orElse(null);

            if (submitter != null
                    && submitter.getResidentRole() == Resident.ResidentRole.FAMILY_MEMBER) {
                // Family Member submitted this request
                String relationship = resolveFmRelationship(submitter);
                submittedByLabel = submitter.getFullName() + " (" + relationship + ")";
                ownerName        = owner != null ? owner.getFullName() : "—";
            } else {
                // Owner (or unrecognised) submitted
                submittedByLabel = owner != null ? owner.getFullName() : r.getSubmittedName();
                ownerName        = "Owner";
            }

        } else {
            FamilyMember matchedFm = null;
            if (owner != null && r.getSubmittedName() != null) {
                String submittedLower = r.getSubmittedName().trim().toLowerCase();
                List<FamilyMember> fms = familyMemberRepo
                        .findByResidentIdOrderByCreatedAtAsc(owner.getId());
                for (FamilyMember fm : fms) {
                    if (fm.getName() != null
                            && fm.getName().trim().toLowerCase().equals(submittedLower)) {
                        matchedFm = fm;
                        break;
                    }
                }
            }

            if (matchedFm != null) {
                String relationship = matchedFm.getRelationship() != null
                        ? matchedFm.getRelationship().getDisplayName()
                        : "Family Member";
                submittedByLabel = matchedFm.getName() + " (" + relationship + ")";
                ownerName        = owner != null ? owner.getFullName() : "—";
            } else {
                submittedByLabel = owner != null ? owner.getFullName() : r.getSubmittedName();
                ownerName        = "Owner";
            }
        }

        dto.setSubmittedByLabel(submittedByLabel);
        dto.setOwnerName(ownerName);
        return dto;
    }

    private String resolveFmRelationship(Resident fmResident) {
        if (fmResident.getFamilyMemberId() != null) {
            Optional<FamilyMember> fmOpt =
                    familyMemberRepo.findById(fmResident.getFamilyMemberId());
            if (fmOpt.isPresent() && fmOpt.get().getRelationship() != null) {
                return fmOpt.get().getRelationship().getDisplayName();
            }
        }
        Optional<FamilyMember> byUserId =
                familyMemberRepo.findByUserId(fmResident.getId());
        if (byUserId.isPresent() && byUserId.get().getRelationship() != null) {
            return byUserId.get().getRelationship().getDisplayName();
        }
        return "Family Member";
    }
}