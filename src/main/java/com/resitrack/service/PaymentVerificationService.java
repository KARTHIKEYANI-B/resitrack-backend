package com.resitrack.service;

import com.resitrack.dto.FinancialYearMonthDTO;
import com.resitrack.dto.MonthAllocationDTO;
import com.resitrack.dto.PaymentVerificationRequestDTO;
import com.resitrack.entity.*;
import com.resitrack.exception.CustomException;
import com.resitrack.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
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
    private final CloudinaryService                    cloudinaryService;

    private static final String CLOUDINARY_FOLDER = "resitrack/payments";

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

        String screenshotUrl = null;
        String screenshotPublicId = null;
        String screenshotFileName = null;
        if (screenshot != null && !screenshot.isEmpty()) {
            CloudinaryService.UploadResult uploaded = cloudinaryService.upload(screenshot, CLOUDINARY_FOLDER);
            screenshotUrl      = uploaded.secureUrl();
            screenshotPublicId = uploaded.publicId();
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
                .screenshotUrl(screenshotUrl)
                .screenshotPublicId(screenshotPublicId)
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
                .screenshotUrl(null)
                .screenshotPublicId(null)
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

        String screenshotUrl = null;
        String screenshotPublicId = null;
        String screenshotFileName = null;
        if (screenshot != null && !screenshot.isEmpty()) {
            CloudinaryService.UploadResult uploaded = cloudinaryService.upload(screenshot, CLOUDINARY_FOLDER);
            screenshotUrl      = uploaded.secureUrl();
            screenshotPublicId = uploaded.publicId();
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
                .screenshotUrl(screenshotUrl)
                .screenshotPublicId(screenshotPublicId)
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

    // ── Multi-Month Maintenance Payment ─────────────────────────────────

    private static final int FY_START_MONTH = 4; // April
    private static final int FY_END_MONTH   = 3; // March
    private static final String[] MONTH_NAMES = {
        "January","February","March","April","May","June",
        "July","August","September","October","November","December"
    };

    private String monthLabelFor(String yyyyMM) {
        if (yyyyMM == null || !yyyyMM.matches("\\d{4}-\\d{2}")) return yyyyMM;
        String[] parts = yyyyMM.split("-");
        int yr = Integer.parseInt(parts[0]);
        int mo = Integer.parseInt(parts[1]);
        return MONTH_NAMES[mo - 1] + " " + yr;
    }

    /**
     * The Financial-Year (Apr → Mar) month selector shown when an Owner /
     * Family Member opens "Pay Maintenance". Only months up to and
     * including the current calendar month are returned — a resident can
     * settle THIS month and any earlier pending months in the active FY,
     * never a future month that hasn't billed yet.
     */
    @Transactional(readOnly = true)
    public List<FinancialYearMonthDTO> getFinancialYearMonths(Long residentId) {
        Resident resident = resolveOwner(residentId);

        LocalDate now = LocalDate.now();
        int fyStartYear = now.getMonthValue() >= FY_START_MONTH ? now.getYear() : now.getYear() - 1;
        String currentMonthKey = currentMonthStr();

        BigDecimal required = maintenanceService.getRequiredMaintenanceAmountFor(resident);

        List<String> monthKeys = new ArrayList<>();
        for (int m = FY_START_MONTH; m <= 12; m++) {
            monthKeys.add(fyStartYear + "-" + String.format("%02d", m));
        }
        for (int m = 1; m <= FY_END_MONTH; m++) {
            monthKeys.add((fyStartYear + 1) + "-" + String.format("%02d", m));
        }

        Set<String> lockedMonths = pendingMonthsForResident(resident.getId());

        List<FinancialYearMonthDTO> result = new ArrayList<>();
        for (String key : monthKeys) {
            boolean isFuture = key.compareTo(currentMonthKey) > 0;

            Double paidRaw = paymentRepo.sumPaidAmountByPropertyAndPaymentMonth(resident.getId(), key);
            BigDecimal paidSoFar = paidRaw != null ? BigDecimal.valueOf(paidRaw) : BigDecimal.ZERO;
            BigDecimal remaining = required.subtract(paidSoFar).max(BigDecimal.ZERO);

            String status;
            boolean selectable;

            if (isFuture) {
                status = "NOT_DUE";
                selectable = false;
            } else if (required.compareTo(BigDecimal.ZERO) <= 0 || remaining.compareTo(BigDecimal.ZERO) <= 0) {
                status = "PAID";
                selectable = false;
            } else if (lockedMonths.contains(key)) {
                status = "PENDING_VERIFICATION";
                selectable = false;
            } else if (paidSoFar.compareTo(BigDecimal.ZERO) > 0) {
                status = "PARTIALLY_PAID";
                selectable = true;
            } else {
                status = "UNPAID";
                selectable = true;
            }

            result.add(FinancialYearMonthDTO.builder()
                    .month(key)
                    .monthLabel(monthLabelFor(key))
                    .dueAmount(required)
                    .paidSoFar(paidSoFar)
                    .remainingDue(remaining)
                    .status(status)
                    .selectable(selectable)
                    .build());
        }
        return result;
    }

    /** Every billing month already covered by a still-PENDING request (single- or multi-month). */
    private Set<String> pendingMonthsForResident(Long residentId) {
        Set<String> months = new HashSet<>();
        for (PaymentVerificationRequest r :
                verificationRepo.findByResidentIdAndStatus(residentId, PaymentVerificationRequest.RequestStatus.PENDING)) {
            if (r.getMonthAllocations() != null && !r.getMonthAllocations().isEmpty()) {
                for (PaymentVerificationRequestMonth a : r.getMonthAllocations()) {
                    months.add(a.getPaymentMonth());
                }
            } else if (r.getPaymentMonth() != null) {
                months.add(r.getPaymentMonth());
            }
        }
        return months;
    }

    /**
     * Validates the resident's selected months and builds the (unsaved)
     * per-month allocation rows, each amount computed AUTHORITATIVELY on
     * the server as the exact remaining balance for that month — the
     * client only sends which months were selected, never a per-month
     * amount, so a tampered/mismatched client total can never be stored.
     */
    private List<PaymentVerificationRequestMonth> buildValidatedMonthAllocations(
            Resident resident, List<String> months) {

        if (months == null || months.isEmpty())
            throw new CustomException("Select at least one month to pay", HttpStatus.BAD_REQUEST);

        List<String> distinctMonths = months.stream().distinct().sorted().collect(Collectors.toList());
        if (distinctMonths.size() != months.size())
            throw new CustomException("Duplicate months in selection", HttpStatus.BAD_REQUEST);

        BigDecimal required = maintenanceService.getRequiredMaintenanceAmountFor(resident);
        if (required.compareTo(BigDecimal.ZERO) <= 0)
            throw new CustomException("No maintenance amount configured for this property", HttpStatus.BAD_REQUEST);

        String currentMonthKey = currentMonthStr();
        Set<String> lockedMonths = pendingMonthsForResident(resident.getId());

        List<PaymentVerificationRequestMonth> allocations = new ArrayList<>();
        for (String month : distinctMonths) {
            if (month == null || !month.matches("\\d{4}-\\d{2}"))
                throw new CustomException("Invalid month: " + month, HttpStatus.BAD_REQUEST);

            if (month.compareTo(currentMonthKey) > 0)
                throw new CustomException(
                        monthLabelFor(month) + " has not been billed yet and cannot be paid in advance.",
                        HttpStatus.BAD_REQUEST);

            if (lockedMonths.contains(month))
                throw new CustomException(
                        "A payment request for " + monthLabelFor(month) + " is already pending verification.",
                        HttpStatus.BAD_REQUEST);

            Double paidRaw = paymentRepo.sumPaidAmountByPropertyAndPaymentMonth(resident.getId(), month);
            BigDecimal paidSoFar = paidRaw != null ? BigDecimal.valueOf(paidRaw) : BigDecimal.ZERO;
            BigDecimal remaining = required.subtract(paidSoFar).max(BigDecimal.ZERO);

            if (remaining.compareTo(BigDecimal.ZERO) <= 0)
                throw new CustomException(
                        monthLabelFor(month) + " has already been fully paid.", HttpStatus.BAD_REQUEST);

            allocations.add(PaymentVerificationRequestMonth.builder()
                    .paymentMonth(month)
                    .amount(remaining)
                    .build());
        }
        return allocations;
    }

    @Transactional
    public PaymentVerificationRequestDTO submitMultiMonthRequest(
            Long residentId,
            Long submittedByResidentId,
            String submittedName,
            String phoneNumber,
            List<String> months,
            String transactionId,
            MultipartFile screenshot) throws IOException {

        Resident resident = resolveOwner(residentId);
        List<PaymentVerificationRequestMonth> allocations = buildValidatedMonthAllocations(resident, months);
        BigDecimal total = allocations.stream().map(PaymentVerificationRequestMonth::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (transactionId == null || transactionId.isBlank())
            throw new CustomException("Transaction ID is required", HttpStatus.BAD_REQUEST);

        String screenshotUrl = null, screenshotPublicId = null, screenshotFileName = null;
        if (screenshot != null && !screenshot.isEmpty()) {
            CloudinaryService.UploadResult uploaded = cloudinaryService.upload(screenshot, CLOUDINARY_FOLDER);
            screenshotUrl      = uploaded.secureUrl();
            screenshotPublicId = uploaded.publicId();
            screenshotFileName = screenshot.getOriginalFilename();
        }

        String monthsKey = allocations.stream()
                .map(PaymentVerificationRequestMonth::getPaymentMonth)
                .collect(Collectors.joining(","));

        PaymentVerificationRequest req = PaymentVerificationRequest.builder()
                .resident(resident)
                .submittedByResidentId(submittedByResidentId)
                .submittedName(submittedName != null ? submittedName : resident.getFullName())
                .flatNumber(resident.getFlatNumber())
                .phoneNumber(phoneNumber != null ? phoneNumber : resident.getPhone())
                .paymentAmount(total)
                .transactionId(transactionId.trim())
                .screenshotUrl(screenshotUrl)
                .screenshotPublicId(screenshotPublicId)
                .screenshotFileName(screenshotFileName)
                .paymentMonth(monthsKey)
                .paymentMethod("GPAY")
                .isMultiMonth(true)
                .status(PaymentVerificationRequest.RequestStatus.PENDING)
                .build();

        allocations.forEach(a -> a.setRequest(req));
        req.setMonthAllocations(allocations);

        PaymentVerificationRequest saved = verificationRepo.save(req);
        notificationService.sendPaymentVerificationRequestToAdmin(saved);
        return toDTO(saved);
    }

    @Transactional
    public PaymentVerificationRequestDTO submitMultiMonthCashRequest(
            Long residentId,
            Long submittedByResidentId,
            String submittedName,
            String phoneNumber,
            List<String> months,
            Long paidToAdminId) {

        Resident resident = resolveOwner(residentId);
        List<PaymentVerificationRequestMonth> allocations = buildValidatedMonthAllocations(resident, months);
        BigDecimal total = allocations.stream().map(PaymentVerificationRequestMonth::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (paidToAdminId == null)
            throw new CustomException("Please select the admin you paid cash to", HttpStatus.BAD_REQUEST);

        Admin paidToAdmin = adminRepo.findById(paidToAdminId)
                .orElseThrow(() -> new CustomException("Selected admin not found", HttpStatus.NOT_FOUND));

        String monthsKey = allocations.stream()
                .map(PaymentVerificationRequestMonth::getPaymentMonth)
                .collect(Collectors.joining(","));

        PaymentVerificationRequest req = PaymentVerificationRequest.builder()
                .resident(resident)
                .submittedByResidentId(submittedByResidentId)
                .submittedName(submittedName != null ? submittedName : resident.getFullName())
                .flatNumber(resident.getFlatNumber())
                .phoneNumber(phoneNumber != null ? phoneNumber : resident.getPhone())
                .paymentAmount(total)
                .transactionId(null)
                .screenshotUrl(null)
                .screenshotPublicId(null)
                .screenshotFileName(null)
                .paymentMonth(monthsKey)
                .paymentMethod("CASH")
                .paidToAdminId(paidToAdminId)
                .paidToAdminName(paidToAdmin.getName())
                .isMultiMonth(true)
                .status(PaymentVerificationRequest.RequestStatus.PENDING)
                .build();

        allocations.forEach(a -> a.setRequest(req));
        req.setMonthAllocations(allocations);

        PaymentVerificationRequest saved = verificationRepo.save(req);
        notificationService.sendCashPaymentRequestToAdmin(saved, paidToAdminId, paidToAdmin.getName());
        log.info("Multi-month CASH payment request {} submitted by resident {} to admin {}",
                saved.getId(), resident.getId(), paidToAdmin.getName());
        return toDTO(saved);
    }

    @Transactional
    public PaymentVerificationRequestDTO submitMultiMonthBankTransferRequest(
            Long residentId,
            Long submittedByResidentId,
            String submittedName,
            String phoneNumber,
            List<String> months,
            String referenceId,
            String bankName,
            MultipartFile screenshot) throws IOException {

        Resident resident = resolveOwner(residentId);
        List<PaymentVerificationRequestMonth> allocations = buildValidatedMonthAllocations(resident, months);
        BigDecimal total = allocations.stream().map(PaymentVerificationRequestMonth::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (referenceId == null || referenceId.isBlank())
            throw new CustomException("Reference / Transaction ID is required for bank transfer",
                    HttpStatus.BAD_REQUEST);

        String screenshotUrl = null, screenshotPublicId = null, screenshotFileName = null;
        if (screenshot != null && !screenshot.isEmpty()) {
            CloudinaryService.UploadResult uploaded = cloudinaryService.upload(screenshot, CLOUDINARY_FOLDER);
            screenshotUrl      = uploaded.secureUrl();
            screenshotPublicId = uploaded.publicId();
            screenshotFileName = screenshot.getOriginalFilename();
        }

        String monthsKey = allocations.stream()
                .map(PaymentVerificationRequestMonth::getPaymentMonth)
                .collect(Collectors.joining(","));

        PaymentVerificationRequest req = PaymentVerificationRequest.builder()
                .resident(resident)
                .submittedByResidentId(submittedByResidentId)
                .submittedName(submittedName != null ? submittedName : resident.getFullName())
                .flatNumber(resident.getFlatNumber())
                .phoneNumber(phoneNumber != null ? phoneNumber : resident.getPhone())
                .paymentAmount(total)
                .transactionId(referenceId.trim())
                .screenshotUrl(screenshotUrl)
                .screenshotPublicId(screenshotPublicId)
                .screenshotFileName(screenshotFileName)
                .paymentMonth(monthsKey)
                .paymentMethod("BANK_TRANSFER")
                .bankName(bankName != null && !bankName.isBlank() ? bankName.trim() : null)
                .isMultiMonth(true)
                .status(PaymentVerificationRequest.RequestStatus.PENDING)
                .build();

        allocations.forEach(a -> a.setRequest(req));
        req.setMonthAllocations(allocations);

        PaymentVerificationRequest saved = verificationRepo.save(req);
        notificationService.sendBankTransferVerificationRequestToAdmin(saved);
        log.info("Multi-month BANK_TRANSFER payment request {} submitted by resident {} ref={}",
                saved.getId(), resident.getId(), referenceId);
        return toDTO(saved);
    }

    @Transactional(readOnly = true)
    public List<PaymentVerificationRequestDTO> getAllRequests() {
        return verificationRepo.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
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

    @Transactional(readOnly = true)
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

        // ── Multi-Month Maintenance Payment ─────────────────────────────
        // A request with populated monthAllocations came from the new
        // submit-multi* endpoints. It settles 2+ billing months at once,
        // so it needs one Payment row PER month instead of the single row
        // the rest of this method creates. Every other request (the
        // ORIGINAL single-month flow — monthAllocations always empty)
        // falls straight through to the unchanged code below.
        if (req.getMonthAllocations() != null && !req.getMonthAllocations().isEmpty()) {
            return verifyMultiMonthRequest(req, resident, method, storedMethod);
        }

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

    /**
     * Multi-Month Maintenance Payment — verification path.
     *
     * Creates ONE Payment row PER month allocation instead of the single
     * row verifyRequest() creates for a legacy single-month request. Each
     * row carries its own paymentMonth/amount (so Maintenance Summary,
     * Paid/Unpaid Detail and Pending Dues allocate correctly to the actual
     * month), while paymentDate is "today" (the verification/collection
     * date) on every row — Financial Summary's collection ledger groups by
     * paymentDate, so the whole multi-month amount is correctly reported
     * as one collection in the month it was actually collected, never
     * split across the earlier billing months.
     *
     * Re-validates each month's remaining balance immediately before
     * creating its Payment row (same duplicate/overpayment protection as
     * validateRemainingBalanceAtVerification() for the single-month path) —
     * if any one month was already fully settled by another payment in the
     * meantime, the ENTIRE request is rejected-in-place with a clear
     * message identifying the conflicting month, and NO Payment rows are
     * created for any month (all-or-nothing, so the resident's other,
     * still-valid months are never left half-processed).
     *
     * transactionId must stay globally unique (payments.transaction_id has
     * a UNIQUE constraint), so each generated row gets the request's own
     * transaction/reference id suffixed with its billing month
     * (CASH requests have no transactionId — every row simply stores null,
     * which MySQL's unique index permits any number of times).
     */
    private PaymentVerificationRequestDTO verifyMultiMonthRequest(
            PaymentVerificationRequest req, Resident resident, String method, String storedMethod) {

        // Re-check every month BEFORE creating any Payment row (all-or-nothing).
        for (PaymentVerificationRequestMonth alloc : req.getMonthAllocations()) {
            validateRemainingBalanceAtVerification(resident, alloc.getPaymentMonth(), alloc.getAmount());
        }

        List<Payment> savedPayments = new ArrayList<>();
        for (PaymentVerificationRequestMonth alloc : req.getMonthAllocations()) {
            String baseTxn = req.getTransactionId(); // null for CASH — preserved as null per row
            String rowTxn  = baseTxn != null ? baseTxn + "-" + alloc.getPaymentMonth() : null;

            Payment payment = Payment.builder()
                    .resident(resident)
                    .amount(alloc.getAmount())
                    .lateFeeAmount(BigDecimal.ZERO)
                    .paymentStatus(Payment.PaymentStatus.PAID)
                    .verificationStatus(Payment.VerificationStatus.VERIFIED)
                    .paymentDate(LocalDate.now())
                    .paymentMethod(storedMethod)
                    .transactionId(rowTxn)
                    .submittedResidentName(req.getSubmittedName())
                    .paymentMonth(alloc.getPaymentMonth())
                    .paymentYear(alloc.getPaymentMonth().substring(0, 4))
                    .description("Verified from multi-month " + method.toLowerCase()
                            + " payment submission (months: " + req.getPaymentMonth() + ")")
                    .adminCreated(false)
                    .build();

            Payment savedPayment = paymentRepo.save(payment);
            generateReceipt(savedPayment);

            alloc.setPaymentId(savedPayment.getId());
            savedPayments.add(savedPayment);
        }

        req.setStatus(PaymentVerificationRequest.RequestStatus.VERIFIED);
        req.setPaymentId(savedPayments.get(0).getId());
        verificationRepo.save(req);

        for (Payment p : savedPayments) {
            notificationService.sendPaymentVerifiedByScreenshotNotification(p, resident);
        }

        log.info("Multi-month payment verification request {} verified → {} payment(s) created for resident {}",
                req.getId(), savedPayments.size(), resident.getId());
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
        // Cloudinary's secure_url is already a permanent, publicly-resolvable
        // HTTPS URL — no proxy endpoint or local-disk lookup needed.
        PaymentVerificationRequestDTO dto = PaymentVerificationRequestDTO.from(r, r.getScreenshotUrl());

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

        if (r.getMonthAllocations() != null && !r.getMonthAllocations().isEmpty()) {
            List<MonthAllocationDTO> allocDTOs = r.getMonthAllocations().stream()
                    .sorted(Comparator.comparing(PaymentVerificationRequestMonth::getPaymentMonth))
                    .map(a -> MonthAllocationDTO.from(a, monthLabelFor(a.getPaymentMonth())))
                    .collect(Collectors.toList());
            dto.setMonthAllocations(allocDTOs);
            dto.setMonthsDisplayLabel(allocDTOs.stream()
                    .map(MonthAllocationDTO::getMonthLabel)
                    .collect(Collectors.joining(" + ")));
        }
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