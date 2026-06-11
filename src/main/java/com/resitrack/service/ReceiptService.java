package com.resitrack.service;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.*;
import com.resitrack.dto.ReceiptResponseDTO;
import com.resitrack.entity.Receipt;
import com.resitrack.exception.CustomException;
import com.resitrack.repository.ReceiptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReceiptService {

    private final ReceiptRepository receiptRepo;

    public List<ReceiptResponseDTO> getAllReceipts() {
        return receiptRepo.findAllByOrderByGeneratedAtDesc()
                .stream().map(ReceiptResponseDTO::from).collect(Collectors.toList());
    }

    public List<ReceiptResponseDTO> getResidentReceipts(Long residentId) {
        return receiptRepo.findByResidentIdOrderByGeneratedAtDesc(residentId)
                .stream().map(ReceiptResponseDTO::from).collect(Collectors.toList());
    }

    public ReceiptResponseDTO getById(Long id) {
        Receipt r = receiptRepo.findById(id)
                .orElseThrow(() -> new CustomException("Receipt not found", HttpStatus.NOT_FOUND));
        return ReceiptResponseDTO.from(r);
    }

    /**
     * Generate a PDF receipt for the given ReceiptResponseDTO.
     * Uses OpenPDF (com.github.librepdf:openpdf) which is already on the classpath.
     */
    public byte[] generateReceiptPdf(ReceiptResponseDTO r) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            // Page size: narrow receipt-style (A5 width, taller)
            Rectangle pageSize = new Rectangle(320f, 550f);
            Document doc = new Document(pageSize, 24f, 24f, 24f, 24f);
            PdfWriter.getInstance(doc, baos);
            doc.open();

            // ── Fonts ──────────────────────────────────────────────
            Font titleFont   = new Font(Font.HELVETICA, 10f, Font.BOLD,   new Color(2, 43, 58));
            Font headerFont  = new Font(Font.HELVETICA,  7f, Font.BOLD,   new Color(31, 122, 140));
            Font labelFont   = new Font(Font.HELVETICA,  7f, Font.NORMAL, new Color(107, 114, 128));
            Font valueFont   = new Font(Font.HELVETICA,  7f, Font.BOLD,   new Color(2, 43, 58));
            Font amountFont  = new Font(Font.HELVETICA, 11f, Font.BOLD,   new Color(2, 43, 58));
            Font footerFont  = new Font(Font.HELVETICA,  6f, Font.NORMAL, new Color(156, 163, 175));
            Font paidFont    = new Font(Font.HELVETICA,  9f, Font.BOLD,   new Color(22, 163, 74));

            // ── Header ─────────────────────────────────────────────
            Paragraph header = new Paragraph("PAYMENT RECEIPT", headerFont);
            header.setAlignment(Element.ALIGN_CENTER);
            doc.add(header);

            Paragraph aptName = new Paragraph(
                    r.getApartmentName() != null ? r.getApartmentName() : "R R Dhurya Owners\nWelfare Association",
                    titleFont);
            aptName.setAlignment(Element.ALIGN_CENTER);
            aptName.setSpacingBefore(3f);
            doc.add(aptName);

            if (r.getGeneratedAt() != null) {
                Paragraph genAt = new Paragraph(
                        r.getGeneratedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")),
                        footerFont);
                genAt.setAlignment(Element.ALIGN_CENTER);
                genAt.setSpacingBefore(2f);
                doc.add(genAt);
            }

            addDashedLine(doc);

            // ── Detail rows ────────────────────────────────────────
            PdfPTable details = new PdfPTable(2);
            details.setWidthPercentage(100f);
            details.setWidths(new float[]{45f, 55f});
            details.setSpacingBefore(4f);

            addDetailRow(details, "RECEIPT NO.",  r.getReceiptNumber(),   labelFont, valueFont);
            addDetailRow(details, "OWNER NAME",   r.getResidentName(),    labelFont, valueFont);
            addDetailRow(details, "FLAT / VILLA", r.getFlatNumber(),      labelFont, valueFont);
            if (r.getFlatType() != null)
                addDetailRow(details, "PROPERTY TYPE", r.getFlatType(),   labelFont, valueFont);
            if (r.getResidentPhone() != null)
                addDetailRow(details, "PHONE",     r.getResidentPhone(),  labelFont, valueFont);
            if (r.getPaymentDate() != null)
                addDetailRow(details, "PAYMENT DATE",
                        r.getPaymentDate().format(DateTimeFormatter.ofPattern("dd MMM yyyy")),
                        labelFont, valueFont);
            if (r.getPaymentMonth() != null)
                addDetailRow(details, "BILLING PERIOD", formatMonth(r.getPaymentMonth()), labelFont, valueFont);
            if (r.getPaymentMethod() != null)
                addDetailRow(details, "PAYMENT MODE", r.getPaymentMethod(), labelFont, valueFont);
            if (r.getTransactionId() != null)
                addDetailRow(details, "TRANSACTION ID", r.getTransactionId(), labelFont, valueFont);

            doc.add(details);

            addDashedLine(doc);

            // ── Amount breakdown ───────────────────────────────────
            PdfPTable amounts = new PdfPTable(2);
            amounts.setWidthPercentage(100f);
            amounts.setWidths(new float[]{55f, 45f});
            amounts.setSpacingBefore(4f);

            BigDecimal paid  = r.getPaidAmount()    != null ? r.getPaidAmount()    : BigDecimal.ZERO;
            BigDecimal late  = r.getLateFeeAmount() != null ? r.getLateFeeAmount() : BigDecimal.ZERO;
            BigDecimal total = r.getTotalAmount()   != null ? r.getTotalAmount()   : paid.add(late);

            addAmountRow(amounts, "Maintenance Amount", formatAmount(paid),  labelFont, valueFont);
            if (late.compareTo(BigDecimal.ZERO) > 0) {
                Font redFont = new Font(Font.HELVETICA, 7f, Font.BOLD, new Color(220, 38, 38));
                addAmountRow(amounts, "Late Fee", formatAmount(late), labelFont, redFont);
            }
            doc.add(amounts);

            // Total line
            PdfPTable totalTable = new PdfPTable(2);
            totalTable.setWidthPercentage(100f);
            totalTable.setWidths(new float[]{55f, 45f});
            totalTable.setSpacingBefore(2f);

            PdfPCell totalLabel = new PdfPCell(new Phrase("TOTAL PAID", amountFont));
            totalLabel.setBorder(Rectangle.TOP);
            totalLabel.setBorderColorTop(new Color(209, 213, 219));
            totalLabel.setPaddingTop(4f);
            totalLabel.setPaddingBottom(4f);
            totalLabel.setPaddingLeft(0f);
            totalLabel.setPaddingRight(0f);

            Font totalAmtFont = new Font(Font.HELVETICA, 13f, Font.BOLD, new Color(2, 43, 58));
            PdfPCell totalVal = new PdfPCell(new Phrase(formatAmount(total), totalAmtFont));
            totalVal.setBorder(Rectangle.TOP);
            totalVal.setBorderColorTop(new Color(209, 213, 219));
            totalVal.setPaddingTop(4f);
            totalVal.setPaddingBottom(4f);
            totalVal.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalVal.setPaddingLeft(0f);
            totalVal.setPaddingRight(0f);

            totalTable.addCell(totalLabel);
            totalTable.addCell(totalVal);
            doc.add(totalTable);

            addDashedLine(doc);

            // ── Status stamp ───────────────────────────────────────
            Paragraph status = new Paragraph("✓  VERIFIED & PAID", paidFont);
            status.setAlignment(Element.ALIGN_CENTER);
            status.setSpacingBefore(6f);
            doc.add(status);

            addDashedLine(doc);

            // ── Footer ─────────────────────────────────────────────
            String footerText = r.getReceiptFooter() != null
                    ? r.getReceiptFooter() : "Thank you for your payment.";
            Paragraph footer = new Paragraph(footerText, footerFont);
            footer.setAlignment(Element.ALIGN_CENTER);
            footer.setSpacingBefore(4f);
            doc.add(footer);

            doc.close();
            return baos.toByteArray();

        } catch (Exception e) {
            throw new CustomException("Failed to generate receipt PDF: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // ── Private helpers ────────────────────────────────────────────────

    private void addDetailRow(PdfPTable table, String label, String value,
                              Font labelFont, Font valueFont) {
        PdfPCell lCell = new PdfPCell(new Phrase(label, labelFont));
        lCell.setBorder(Rectangle.NO_BORDER);
        lCell.setPaddingBottom(3f);
        lCell.setPaddingLeft(0f);

        PdfPCell vCell = new PdfPCell(new Phrase(value != null ? value : "—", valueFont));
        vCell.setBorder(Rectangle.NO_BORDER);
        vCell.setPaddingBottom(3f);
        vCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        vCell.setPaddingRight(0f);

        table.addCell(lCell);
        table.addCell(vCell);
    }

    private void addAmountRow(PdfPTable table, String label, String value,
                              Font labelFont, Font valueFont) {
        PdfPCell lCell = new PdfPCell(new Phrase(label, labelFont));
        lCell.setBorder(Rectangle.NO_BORDER);
        lCell.setPaddingBottom(2f);
        lCell.setPaddingLeft(0f);

        PdfPCell vCell = new PdfPCell(new Phrase(value, valueFont));
        vCell.setBorder(Rectangle.NO_BORDER);
        vCell.setPaddingBottom(2f);
        vCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        vCell.setPaddingRight(0f);

        table.addCell(lCell);
        table.addCell(vCell);
    }

    private void addDashedLine(Document doc) throws DocumentException {
        Paragraph dash = new Paragraph("- - - - - - - - - - - - - - - - - - - - - - - - - - - - -",
                new Font(Font.COURIER, 6f, Font.NORMAL, new Color(209, 213, 219)));
        dash.setAlignment(Element.ALIGN_CENTER);
        dash.setSpacingBefore(4f);
        dash.setSpacingAfter(4f);
        doc.add(dash);
    }

    private String formatAmount(BigDecimal amount) {
        if (amount == null) return "₹0.00";
        return "₹" + String.format("%,.2f", amount);
    }

    private String formatMonth(String ym) {
        if (ym == null) return "—";
        try {
            String[] parts = ym.split("-");
            if (parts.length == 2) {
                int year  = Integer.parseInt(parts[0]);
                int month = Integer.parseInt(parts[1]);
                String[] months = {"January","February","March","April","May","June",
                        "July","August","September","October","November","December"};
                return months[month - 1] + " " + year;
            }
        } catch (Exception ignored) {}
        return ym;
    }
}