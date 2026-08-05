package com.resitrack.service;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.*;
import com.resitrack.dto.ReceiptResponseDTO;
import com.resitrack.entity.Payment;
import com.resitrack.entity.Receipt;
import com.resitrack.exception.CustomException;
import com.resitrack.repository.PaymentRepository;
import com.resitrack.repository.ReceiptRepository;
import com.resitrack.util.NumberToWordsUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReceiptService {

    private final ReceiptRepository receiptRepo;
    private final PaymentRepository paymentRepo;

    public List<ReceiptResponseDTO> getAllReceipts() {
        return toDedupedDTOs(receiptRepo.findAllByOrderByGeneratedAtDesc());
    }

    public List<ReceiptResponseDTO> getResidentReceipts(Long residentId) {
        return toDedupedDTOs(receiptRepo.findByResidentIdOrderByGeneratedAtDesc(residentId));
    }

    public ReceiptResponseDTO getById(Long id) {
        Receipt r = receiptRepo.findById(id)
                .orElseThrow(() -> new CustomException("Receipt not found", HttpStatus.NOT_FOUND));
        ReceiptResponseDTO dto = ReceiptResponseDTO.from(r);
        enrichWithBatchBreakdown(dto, r);
        return dto;
    }

    /**
     * Converts a list of Receipt rows to DTOs, enriched with each one's
     * multi-month breakdown (if any), and — for LIST views only — collapses
     * a multi-month batch down to a single representative row (the receipt
     * for the earliest billing month in that batch) instead of showing one
     * near-identical row per month. getById() above deliberately does NOT
     * dedupe: fetching a specific receipt by id always returns it, even if
     * it's a "hidden" sibling that a list view folds away.
     */
    private List<ReceiptResponseDTO> toDedupedDTOs(List<Receipt> receipts) {
        List<ReceiptResponseDTO> result = new ArrayList<>();
        for (Receipt r : receipts) {
            ReceiptResponseDTO dto = ReceiptResponseDTO.from(r);
            enrichWithBatchBreakdown(dto, r);

            if (dto.getMonthBreakdown() != null && !dto.getMonthBreakdown().isEmpty()) {
                String earliestMonth = dto.getMonthBreakdown().get(0).getPaymentMonth();
                if (!Objects.equals(dto.getPaymentMonth(), earliestMonth)) continue; // folded into the earliest month's row
            }
            result.add(dto);
        }
        return result;
    }

    /**
     * Populates monthBreakdown/batchTotalAmount when this receipt's Payment
     * is part of a multi-month batch (paymentBatchId shared with at least
     * one other PAID sibling — see Payment.paymentBatchId). Left null/empty
     * for an ordinary single-month receipt, so paidAmount/totalAmount above
     * remain the correct figures to show and nothing else about this DTO's
     * meaning changes.
     */
    private void enrichWithBatchBreakdown(ReceiptResponseDTO dto, Receipt r) {
        Payment payment = r.getPayment();
        if (payment == null || payment.getPaymentBatchId() == null) return;

        List<Payment> siblings = paymentRepo
                .findByPaymentBatchIdOrderByPaymentMonthAsc(payment.getPaymentBatchId())
                .stream()
                .filter(p -> p.getPaymentStatus() == Payment.PaymentStatus.PAID)
                .collect(Collectors.toList());

        if (siblings.size() < 2) return; // just this one month — not a multi-month batch

        List<ReceiptResponseDTO.MonthLine> lines = siblings.stream()
                .map(p -> ReceiptResponseDTO.MonthLine.builder()
                        .paymentMonth(p.getPaymentMonth())
                        .amount(p.getAmount())
                        .build())
                .collect(Collectors.toList());

        BigDecimal batchTotal = siblings.stream()
                .map(Payment::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        dto.setMonthBreakdown(lines);
        dto.setBatchTotalAmount(batchTotal);
    }

    /** "2026-04" -> "Apr 2026", matching the frontend's monthDisplay() convention. */
    private static String monthLabel(String paymentMonth) {
        try {
            YearMonth ym = YearMonth.parse(paymentMonth);
            return ym.format(DateTimeFormatter.ofPattern("MMM yyyy"));
        } catch (Exception e) {
            return paymentMonth != null ? paymentMonth : "";
        }
    }

    /**
     * Generate a PDF Receipt Voucher for the given ReceiptResponseDTO,
     * matching the attached Receipt Voucher reference format:
     *
     *   RR DHURYA
     *   Receipt Voucher
     *   No. : <receiptNumber>                 Dated :   <date>
     *   ┌─────────────────────────────┬─────────┐
     *   │ Particulars                 │  Amount │
     *   ├─────────────────────────────┼─────────┤
     *   │ Account :                   │         │
     *   │   <Flat/Villa> <Owner Name> │  amount │
     *   │   Agst Ref <ref no.>        │ amount Cr│  (only if a reference number is available)
     *   │ Through :                   │         │
     *   │   <payment mode>            │         │
     *   │ Amount (in words) :         │         │
     *   │   INR <...> Only            │ ₹ total │
     *   └─────────────────────────────┴─────────┘
     *                                    Authorised Signatory
     *
     * Uses OpenPDF (com.github.librepdf:openpdf) which is already on the classpath.
     */
    public byte[] generateReceiptPdf(ReceiptResponseDTO r) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            // Letter page, matching the reference format's page size.
            Document doc = new Document(PageSize.LETTER, 50f, 50f, 40f, 40f);
            PdfWriter.getInstance(doc, baos);
            doc.open();

            String orgName = r.getApartmentName() != null && !r.getApartmentName().isBlank()
                    ? r.getApartmentName() : "R R Dhurya Owners Welfare Association";

            BigDecimal paid  = r.getPaidAmount()    != null ? r.getPaidAmount()    : BigDecimal.ZERO;
            BigDecimal late  = r.getLateFeeAmount() != null ? r.getLateFeeAmount() : BigDecimal.ZERO;
            BigDecimal total = r.getTotalAmount()   != null ? r.getTotalAmount()   : paid.add(late);

            // flatLabel is the precomputed "Flat 58" / "Villa 12" string
            // (ReceiptResponseDTO.from()) — falls back to bare flatNumber
            // only if a caller ever builds the DTO some other way. Shown
            // once, in the header's top-right corner (addVoucherDocument),
            // instead of repeating on every "Account :" line below.
            String flatPart = r.getFlatLabel() != null ? r.getFlatLabel() : r.getFlatNumber();
            String accountLabel = r.getResidentName() != null ? r.getResidentName() : "";
            String dateStr = r.getPaymentDate() != null
                    ? r.getPaymentDate().format(DateTimeFormatter.ofPattern("d-MMM-yy")) : "—";

            // Multi-month payment (see Payment.paymentBatchId): one line per
            // billing month covered, and the total is the sum across all of
            // them — instead of the single-line, single-month layout below.
            // No single "Agst Ref" line in this case since each month's
            // underlying Payment row has its own distinct transactionId.
            List<VoucherLine> lines;
            String agstRef = r.getTransactionId();
            if (r.getMonthBreakdown() != null && !r.getMonthBreakdown().isEmpty()) {
                lines = r.getMonthBreakdown().stream()
                        .map(m -> new VoucherLine(
                                accountLabel + " — " + monthLabel(m.getPaymentMonth()),
                                m.getAmount(), null, false))
                        .collect(Collectors.toList());
                total = r.getBatchTotalAmount() != null ? r.getBatchTotalAmount() : total;
                agstRef = null;
            } else {
                lines = List.of(new VoucherLine(accountLabel, total, null, false));
            }

            addVoucherDocument(doc, orgName, "Receipt Voucher",
                    r.getReceiptNumber(), dateStr,
                    lines,
                    agstRef, total,
                    r.getPaymentMethod(), null,
                    NumberToWordsUtil.amountInWords(total), total,
                    false, flatPart);

            doc.close();
            return baos.toByteArray();

        } catch (Exception e) {
            throw new CustomException("Failed to generate receipt PDF: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /** One "Account :" line item on a voucher (label + amount). */
    record VoucherLine(String label, BigDecimal amount, String refNo, boolean sub) {}

    /**
     * Shared Tally-style voucher renderer used by both the Receipt Voucher
     * (this class) and the Payment Voucher (ExpenseService), so both PDFs
     * share identical structure/format.
     */
    static void addVoucherDocument(Document doc, String orgName, String title,
                                    String voucherNo, String dateStr,
                                    List<VoucherLine> accountLines,
                                    String agstRef, BigDecimal agstRefAmount,
                                    String through, String onAccountOf,
                                    String amountWords, BigDecimal total,
                                    boolean showReceiverSignature) throws DocumentException {
        addVoucherDocument(doc, orgName, title, voucherNo, dateStr, accountLines,
                agstRef, agstRefAmount, through, onAccountOf, amountWords, total,
                showReceiverSignature, null);
    }

    // flatLabel ("Flat 58" / "Villa 12") — Receipt Voucher only, shown once in
    // the top-right corner of the header instead of repeating on every
    // "Account :" line (see VoucherDocument.jsx for the matching on-screen
    // rendering). Null/blank for the Payment Voucher (expenses, no
    // resident), which keeps the original fully-centered header untouched.
    static void addVoucherDocument(Document doc, String orgName, String title,
                                    String voucherNo, String dateStr,
                                    List<VoucherLine> accountLines,
                                    String agstRef, BigDecimal agstRefAmount,
                                    String through, String onAccountOf,
                                    String amountWords, BigDecimal total,
                                    boolean showReceiverSignature,
                                    String flatLabel) throws DocumentException {

        Font orgFont    = new Font(Font.TIMES_ROMAN, 15f, Font.BOLD,   Color.BLACK);
        Font titleFont  = new Font(Font.TIMES_ROMAN, 13f, Font.BOLD,   Color.BLACK);
        Font normFont   = new Font(Font.TIMES_ROMAN, 11f, Font.NORMAL, Color.BLACK);
        Font totalFont  = new Font(Font.TIMES_ROMAN, 12f, Font.BOLD,   Color.BLACK);
        Font flatFont   = new Font(Font.TIMES_ROMAN, 10f, Font.BOLD,   Color.BLACK);

        if (flatLabel != null && !flatLabel.isBlank()) {
            PdfPTable headerRow = new PdfPTable(2);
            headerRow.setWidthPercentage(100f);
            headerRow.setWidths(new float[]{80f, 20f});

            PdfPCell orgCell = new PdfPCell();
            orgCell.setBorder(Rectangle.NO_BORDER);
            orgCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            Paragraph org = new Paragraph(orgName.toUpperCase(), orgFont);
            org.setAlignment(Element.ALIGN_CENTER);
            orgCell.addElement(org);
            Paragraph ttl = new Paragraph(title, titleFont);
            ttl.setAlignment(Element.ALIGN_CENTER);
            ttl.setSpacingBefore(2f);
            orgCell.addElement(ttl);
            headerRow.addCell(orgCell);

            PdfPCell flatCell = new PdfPCell(new Phrase(flatLabel, flatFont));
            flatCell.setBorder(Rectangle.BOX);
            flatCell.setBorderColor(Color.BLACK);
            flatCell.setBorderWidth(0.75f);
            flatCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            flatCell.setVerticalAlignment(Element.ALIGN_TOP);
            flatCell.setPadding(4f);
            headerRow.addCell(flatCell);

            headerRow.setSpacingAfter(10f);
            doc.add(headerRow);
        } else {
            Paragraph org = new Paragraph(orgName.toUpperCase(), orgFont);
            org.setAlignment(Element.ALIGN_CENTER);
            doc.add(org);

            Paragraph ttl = new Paragraph(title, titleFont);
            ttl.setAlignment(Element.ALIGN_CENTER);
            ttl.setSpacingBefore(2f);
            ttl.setSpacingAfter(10f);
            doc.add(ttl);
        }

        // No. / Dated row, boxed top+bottom
        PdfPTable noRow = new PdfPTable(2);
        noRow.setWidthPercentage(100f);
        noRow.setWidths(new float[]{50f, 50f});
        PdfPCell noCell = new PdfPCell(new Phrase("No.  :  " + (voucherNo != null ? voucherNo : "—"), normFont));
        noCell.setBorder(Rectangle.TOP | Rectangle.BOTTOM);
        noCell.setBorderColor(Color.BLACK);
        noCell.setPadding(4f);
        PdfPCell datedCell = new PdfPCell(new Phrase("Dated :   " + dateStr, normFont));
        datedCell.setBorder(Rectangle.TOP | Rectangle.BOTTOM);
        datedCell.setBorderColor(Color.BLACK);
        datedCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        datedCell.setPadding(4f);
        noRow.addCell(noCell);
        noRow.addCell(datedCell);
        doc.add(noRow);

        // Particulars / Amount table
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100f);
        table.setWidths(new float[]{75f, 25f});
        table.setSpacingBefore(0f);

        table.addCell(borderedCell("Particulars", normFont, Element.ALIGN_LEFT, Rectangle.BOTTOM));
        table.addCell(borderedCell("Amount", normFont, Element.ALIGN_RIGHT, Rectangle.BOTTOM | Rectangle.LEFT));

        table.addCell(sideCell("Account :", normFont));
        table.addCell(sideBorderCell(""));

        for (VoucherLine line : accountLines) {
            table.addCell(sideCell("    " + line.label(), normFont));
            table.addCell(sideBorderCell(formatIndianAmount(line.amount())));
        }
        if (agstRef != null && !agstRef.isBlank()) {
            table.addCell(sideCell("    Agst Ref  " + agstRef, normFont));
            table.addCell(sideBorderCell(formatIndianAmount(agstRefAmount) + "  Cr"));
        }

        table.addCell(sideCell("Through :", normFont));
        table.addCell(sideBorderCell(""));
        table.addCell(sideCell("    " + (through != null && !through.isBlank() ? through : "—"), normFont));
        table.addCell(sideBorderCell(""));

        if (onAccountOf != null) {
            table.addCell(sideCell("On Account of :", normFont));
            table.addCell(sideBorderCell(""));
            table.addCell(sideCell("    " + (onAccountOf.isBlank() ? "—" : onAccountOf), normFont));
            table.addCell(sideBorderCell(""));
        }

        // Amount-in-words + boxed total row
        PdfPCell wordsCell = new PdfPCell();
        wordsCell.addElement(new Phrase("Amount (in words) :", normFont));
        wordsCell.addElement(new Phrase("    " + amountWords, normFont));
        wordsCell.setBorder(Rectangle.TOP);
        wordsCell.setBorderColor(Color.BLACK);
        wordsCell.setPaddingTop(6f);
        wordsCell.setPaddingBottom(6f);
        wordsCell.setPaddingLeft(0f);
        table.addCell(wordsCell);

        PdfPCell totalCell = new PdfPCell(new Phrase("\u20B9 " + formatIndianAmount(total), totalFont));
        totalCell.setBorder(Rectangle.TOP | Rectangle.LEFT);
        totalCell.setBorderColor(Color.BLACK);
        totalCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totalCell.setVerticalAlignment(Element.ALIGN_BOTTOM);
        totalCell.setPaddingTop(6f);
        totalCell.setPaddingBottom(6f);
        table.addCell(totalCell);

        doc.add(table);

        // Signature row
        Paragraph spacer = new Paragraph(" ");
        spacer.setSpacingBefore(40f);
        doc.add(spacer);

        PdfPTable sigRow = new PdfPTable(2);
        sigRow.setWidthPercentage(100f);
        if (showReceiverSignature) {
            PdfPCell recv = new PdfPCell(new Phrase("Receiver's Signature:", normFont));
            recv.setBorder(Rectangle.NO_BORDER);
            sigRow.addCell(recv);
        } else {
            PdfPCell blank = new PdfPCell(new Phrase(""));
            blank.setBorder(Rectangle.NO_BORDER);
            sigRow.addCell(blank);
        }
        PdfPCell auth = new PdfPCell(new Phrase("Authorised Signatory", normFont));
        auth.setBorder(Rectangle.NO_BORDER);
        auth.setHorizontalAlignment(Element.ALIGN_RIGHT);
        sigRow.addCell(auth);
        doc.add(sigRow);
    }

    private static PdfPCell borderedCell(String text, Font font, int align, int border) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setBorder(border);
        c.setBorderColor(Color.BLACK);
        c.setHorizontalAlignment(align);
        c.setPadding(4f);
        return c;
    }

    private static PdfPCell sideCell(String text, Font font) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setBorder(Rectangle.NO_BORDER);
        c.setPaddingTop(2f);
        c.setPaddingBottom(2f);
        c.setPaddingLeft(4f);
        return c;
    }

    private static PdfPCell sideBorderCell(String text) {
        PdfPCell c = new PdfPCell(new Phrase(text, new Font(Font.TIMES_ROMAN, 11f, Font.NORMAL, Color.BLACK)));
        c.setBorder(Rectangle.LEFT);
        c.setBorderColor(Color.BLACK);
        c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        c.setPaddingTop(2f);
        c.setPaddingBottom(2f);
        c.setPaddingRight(4f);
        return c;
    }

    /** Indian digit grouping with 2 decimals, e.g. 280549.00 -> "2,80,549.00" */
    static String formatIndianAmount(BigDecimal amount) {
        BigDecimal value = (amount != null ? amount : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        boolean negative = value.signum() < 0;
        String plain = value.abs().toPlainString(); // e.g. "280549.00"
        String[] parts = plain.split("\\.");
        String intPart = parts[0];
        String decPart = parts.length > 1 ? parts[1] : "00";

        String lastThree = intPart.length() > 3 ? intPart.substring(intPart.length() - 3) : intPart;
        String remaining  = intPart.length() > 3 ? intPart.substring(0, intPart.length() - 3) : "";
        StringBuilder grouped = new StringBuilder();
        for (int i = remaining.length(); i > 0; i -= 2) {
            int start = Math.max(0, i - 2);
            grouped.insert(0, remaining.substring(start, i) + (start > 0 ? "," : ""));
        }
        String result = (grouped.length() > 0 ? grouped + "," : "") + lastThree + "." + decPart;
        return (negative ? "-" : "") + result;
    }

}