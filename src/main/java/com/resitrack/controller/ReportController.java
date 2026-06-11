package com.resitrack.controller;

import com.lowagie.text.*;
//import com.lowagie.text.Row;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.*;
import com.resitrack.dto.ApiResponse;
import com.resitrack.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/admin/reports")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getReport(
            @RequestParam(defaultValue = "monthly")  String period,
            @RequestParam(defaultValue = "0")         int    year,
            @RequestParam(defaultValue = "0")         int    month,
            @RequestParam(defaultValue = "0")         int    quarter) {

        if (year   == 0) year     = LocalDate.now().getYear();
        if (month  == 0) month    = LocalDate.now().getMonthValue();
        if (quarter == 0) quarter = (month - 1) / 3 + 1;

        Map<String, Object> report = switch (period.toLowerCase()) {
            case "quarterly" -> reportService.getQuarterlyReport(year, quarter);
            case "yearly"    -> reportService.getYearlyReport(year);
            default          -> reportService.getMonthlyReport(year, month);
        };

        return ResponseEntity.ok(ApiResponse.success(report));
    }

    @GetMapping("/export/pdf")
    public ResponseEntity<byte[]> exportPdf(
            @RequestParam(defaultValue = "monthly") String period,
            @RequestParam(defaultValue = "0") int year,
            @RequestParam(defaultValue = "0") int month) throws IOException {

        if (year  == 0) year  = LocalDate.now().getYear();
        if (month == 0) month = LocalDate.now().getMonthValue();

        Map<String, Object> data = reportService.getMonthlyReport(year, month);
        byte[] pdf = generatePdf(data, period, year, month);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=report-" + year + "-" + month + ".pdf")
                .body(pdf);
    }

    @GetMapping("/export/excel")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam(defaultValue = "monthly") String period,
            @RequestParam(defaultValue = "0") int year,
            @RequestParam(defaultValue = "0") int month) throws IOException {

        if (year  == 0) year  = LocalDate.now().getYear();
        if (month == 0) month = LocalDate.now().getMonthValue();

        Map<String, Object> data = reportService.getMonthlyReport(year, month);
        byte[] excel = generateExcel(data, year, month);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=report-" + year + "-" + month + ".xlsx")
                .body(excel);
    }

    private byte[] generatePdf(Map<String, Object> data, String period, int year, int month)
            throws IOException {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 40, 40, 60, 40);
        PdfWriter.getInstance(doc, baos);
        doc.open();

        Font titleFont  = new Font(Font.HELVETICA, 18, Font.BOLD);
        Font headFont   = new Font(Font.HELVETICA, 12, Font.BOLD);
        Font normalFont = new Font(Font.HELVETICA, 10, Font.NORMAL);
        Font subFont    = new Font(Font.HELVETICA, 9,  Font.ITALIC);
  
        Paragraph title = new Paragraph("ResiTrack Financial Report", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        doc.add(title);

        doc.add(new Paragraph("R R Dhurya Owners Welfare Association", subFont) {{
            setAlignment(Element.ALIGN_CENTER);
        }});
        doc.add(new Paragraph("Period: " + period.toUpperCase() + " — " + year + "/" + month, subFont) {{
            setAlignment(Element.ALIGN_CENTER);
        }});
        doc.add(new Paragraph(" "));

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingBefore(10f);

        addPdfRow(table, "Total Collected",  "₹" + fmt(data.get("totalCollected")), headFont, normalFont);
        addPdfRow(table, "Total Expenses",   "₹" + fmt(data.get("totalExpenses")),  headFont, normalFont);
        addPdfRow(table, "Net Balance",      "₹" + fmt(data.get("balance")),        headFont, normalFont);
        addPdfRow(table, "Pending Dues",     "₹" + fmt(data.get("pendingDues")),    headFont, normalFont);
        addPdfRow(table, "Collection Rate",  fmt(data.get("collectionRate")) + "%", headFont, normalFont);
        addPdfRow(table, "Total Flats",      String.valueOf(data.get("totalFlats")), headFont, normalFont);
        addPdfRow(table, "Flats Paid",       String.valueOf(data.get("paidFlats")), headFont, normalFont);
        addPdfRow(table, "Flats Pending",    String.valueOf(data.get("pendingFlats")), headFont, normalFont);

        doc.add(table);
        doc.add(new Paragraph(" "));
        doc.add(new Paragraph("Generated on: " + LocalDate.now(), subFont));

        doc.close();
        return baos.toByteArray();
    }

    private void addPdfRow(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell c1 = new PdfPCell(new Phrase(label, labelFont));
        PdfPCell c2 = new PdfPCell(new Phrase(value, valueFont));
        c1.setPadding(8);
        c2.setPadding(8);
        c2.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(c1);
        table.addCell(c2);
    }

    private byte[] generateExcel(Map<String, Object> data, int year, int month) throws IOException {

        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            Sheet sheet = wb.createSheet("Financial Report");

            Row header = sheet.createRow(0);
            String[] cols = {"Metric", "Value"};
            for (int i = 0; i < cols.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(cols[i]);
                CellStyle style = wb.createCellStyle();
                org.apache.poi.ss.usermodel.Font f = wb.createFont();
                f.setBold(true);
                style.setFont(f);
                cell.setCellStyle(style);
            }

            Object[][] rows = {
                {"Total Collected",  "₹" + fmt(data.get("totalCollected"))},
                {"Total Expenses",   "₹" + fmt(data.get("totalExpenses"))},
                {"Net Balance",      "₹" + fmt(data.get("balance"))},
                {"Pending Dues",     "₹" + fmt(data.get("pendingDues"))},
                {"Collection Rate",  fmt(data.get("collectionRate")) + "%"},
                {"Total Flats",      String.valueOf(data.get("totalFlats"))},
                {"Flats Paid",       String.valueOf(data.get("paidFlats"))},
                {"Flats Pending",    String.valueOf(data.get("pendingFlats"))},
                {"Report Period",    year + "/" + month},
                {"Generated On",    LocalDate.now().toString()},
            };

            for (int i = 0; i < rows.length; i++) {
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue((String) rows[i][0]);
                row.createCell(1).setCellValue((String) rows[i][1]);
            }

            sheet.autoSizeColumn(0);
            sheet.autoSizeColumn(1);
            wb.write(baos);
            return baos.toByteArray();
        }
    }

    private String fmt(Object val) {
        if (val == null) return "0";
        if (val instanceof Double d) return String.format("%.2f", d);
        if (val instanceof Number n) return String.valueOf(n.longValue());
        return val.toString();
    }
}
