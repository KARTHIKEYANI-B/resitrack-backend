package com.resitrack.controller;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.*;
import com.resitrack.dto.ApiResponse;
import com.resitrack.service.FinancialReportService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.awt.Color;
import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class FinancialReportController {

    private final FinancialReportService reportService;

    private static final String APARTMENT_NAME = "R R Dhurya Owners Welfare Association";

    @GetMapping("/admin/financial-report/collection")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCollection(
            @RequestParam(defaultValue = "0") int year,
            @RequestParam(defaultValue = "4") int startMonth,
            @RequestParam(defaultValue = "3") int endMonth) {
        if (year == 0) year = currentFinancialYearStart();
        return ResponseEntity.ok(ApiResponse.success(
                reportService.getCollectionMatrix(year, startMonth, endMonth)));
    }

    @GetMapping("/admin/financial-report/collection/detail")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMonthlyDetail(
            @RequestParam(defaultValue = "0") int year,
            @RequestParam(defaultValue = "0") int month) {
        if (year  == 0) year  = LocalDate.now().getYear();
        if (month == 0) month = LocalDate.now().getMonthValue();
        return ResponseEntity.ok(ApiResponse.success(
                reportService.getMonthlyDetail(year, month)));
    }

    @GetMapping("/admin/financial-report/expenses")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getExpenses(
            @RequestParam(defaultValue = "0") int year,
            @RequestParam(defaultValue = "1") int startMonth,
            @RequestParam(defaultValue = "12") int endMonth) {
        if (year == 0) year = LocalDate.now().getYear();
        return ResponseEntity.ok(ApiResponse.success(
                reportService.getExpenseReport(year, startMonth, endMonth)));
    }

    @GetMapping("/admin/financial-report/summary")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSummary(
            @RequestParam(defaultValue = "0") int year) {
        if (year == 0) year = LocalDate.now().getYear();
        return ResponseEntity.ok(ApiResponse.success(
                reportService.getFinancialSummary(year)));
    }

    @GetMapping("/admin/financial-report/collection/export/pdf")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> exportCollectionPdf(
            @RequestParam(defaultValue = "0") int year,
            @RequestParam(defaultValue = "4") int startMonth,
            @RequestParam(defaultValue = "3") int endMonth) throws IOException {
        if (year == 0) year = currentFinancialYearStart();
        Map<String, Object> data = reportService.getCollectionMatrix(year, startMonth, endMonth);
        byte[] pdf = buildCollectionPdf(data, year);
        return pdfResponse(pdf, "collection-report-" + year + ".pdf");
    }

    @GetMapping("/admin/financial-report/expenses/export/pdf")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> exportExpensesPdf(
            @RequestParam(defaultValue = "0") int year,
            @RequestParam(defaultValue = "1") int startMonth,
            @RequestParam(defaultValue = "12") int endMonth) throws IOException {
        if (year == 0) year = LocalDate.now().getYear();
        Map<String, Object> data = reportService.getExpenseReport(year, startMonth, endMonth);
        byte[] pdf = buildExpensePdf(data);
        return pdfResponse(pdf, "expense-report-" + year + ".pdf");
    }

    @GetMapping("/admin/financial-report/collection/export/excel")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> exportCollectionExcel(
            @RequestParam(defaultValue = "0") int year,
            @RequestParam(defaultValue = "4") int startMonth,
            @RequestParam(defaultValue = "3") int endMonth) throws IOException {
        if (year == 0) year = currentFinancialYearStart();
        Map<String, Object> data = reportService.getCollectionMatrix(year, startMonth, endMonth);
        byte[] xlsx = buildCollectionExcel(data);
        return xlsxResponse(xlsx, "collection-report-" + year + ".xlsx");
    }

    @GetMapping("/admin/financial-report/expenses/export/excel")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> exportExpensesExcel(
            @RequestParam(defaultValue = "0") int year,
            @RequestParam(defaultValue = "1") int startMonth,
            @RequestParam(defaultValue = "12") int endMonth) throws IOException {
        if (year == 0) year = LocalDate.now().getYear();
        Map<String, Object> data = reportService.getExpenseReport(year, startMonth, endMonth);
        byte[] xlsx = buildExpenseExcel(data);
        return xlsxResponse(xlsx, "expense-report-" + year + ".xlsx");
    }

    @GetMapping("/user/financial-report/monthly-summary")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUserMonthlySummary(
            @RequestParam(defaultValue = "0") int year,
            @RequestParam(defaultValue = "0") int month) {
        if (year  == 0) year  = LocalDate.now().getYear();
        if (month == 0) month = LocalDate.now().getMonthValue();
        return ResponseEntity.ok(ApiResponse.success(
                reportService.getMonthlySummary(year, month)));
    }

    @GetMapping("/user/financial-report/collection")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUserCollection(
            @RequestParam(defaultValue = "0") int year,
            @RequestParam(defaultValue = "1") int startMonth,
            @RequestParam(defaultValue = "12") int endMonth) {
        if (year == 0) year = LocalDate.now().getYear();
        return ResponseEntity.ok(ApiResponse.success(
                reportService.getCollectionMatrix(year, startMonth, endMonth)));
    }

    @GetMapping("/user/financial-report/expenses")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUserExpenses(
            @RequestParam(defaultValue = "0") int year,
            @RequestParam(defaultValue = "1") int startMonth,
            @RequestParam(defaultValue = "12") int endMonth) {
        if (year == 0) year = LocalDate.now().getYear();
        return ResponseEntity.ok(ApiResponse.success(
                reportService.getExpenseReport(year, startMonth, endMonth)));
    }

    @GetMapping("/user/financial-report/collection/export/pdf")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<byte[]> exportUserCollectionPdf(
            @RequestParam(defaultValue = "0") int year,
            @RequestParam(defaultValue = "1") int startMonth,
            @RequestParam(defaultValue = "12") int endMonth) throws IOException {
        if (year == 0) year = LocalDate.now().getYear();
        Map<String, Object> data = reportService.getCollectionMatrix(year, startMonth, endMonth);
        byte[] pdf = buildCollectionPdf(data, year);
        return pdfResponse(pdf, "collection-report-" + year + ".pdf");
    }

    @GetMapping("/user/financial-report/expenses/export/pdf")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<byte[]> exportUserExpensesPdf(
            @RequestParam(defaultValue = "0") int year,
            @RequestParam(defaultValue = "1") int startMonth,
            @RequestParam(defaultValue = "12") int endMonth) throws IOException {
        if (year == 0) year = LocalDate.now().getYear();
        Map<String, Object> data = reportService.getExpenseReport(year, startMonth, endMonth);
        byte[] pdf = buildExpensePdf(data);
        return pdfResponse(pdf, "expense-report-" + year + ".pdf");
    }

    @GetMapping("/user/financial-report/collection/export/excel")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<byte[]> exportUserCollectionExcel(
            @RequestParam(defaultValue = "0") int year,
            @RequestParam(defaultValue = "1") int startMonth,
            @RequestParam(defaultValue = "12") int endMonth) throws IOException {
        if (year == 0) year = LocalDate.now().getYear();
        Map<String, Object> data = reportService.getCollectionMatrix(year, startMonth, endMonth);
        byte[] xlsx = buildCollectionExcel(data);
        return xlsxResponse(xlsx, "collection-report-" + year + ".xlsx");
    }

    @GetMapping("/user/financial-report/expenses/export/excel")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<byte[]> exportUserExpensesExcel(
            @RequestParam(defaultValue = "0") int year,
            @RequestParam(defaultValue = "1") int startMonth,
            @RequestParam(defaultValue = "12") int endMonth) throws IOException {
        if (year == 0) year = LocalDate.now().getYear();
        Map<String, Object> data = reportService.getExpenseReport(year, startMonth, endMonth);
        byte[] xlsx = buildExpenseExcel(data);
        return xlsxResponse(xlsx, "expense-report-" + year + ".xlsx");
    }

    @SuppressWarnings("unchecked")
    private byte[] buildCollectionPdf(Map<String, Object> data, int year) throws IOException {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A3.rotate(), 30, 30, 50, 30);
        PdfWriter.getInstance(doc, baos);
        doc.open();

        Font titleFont   = new Font(Font.HELVETICA, 14, Font.BOLD);
        Font subtitleFont= new Font(Font.HELVETICA, 10, Font.BOLD);
        Font normalFont  = new Font(Font.HELVETICA, 8,  Font.NORMAL);
        Font boldFont    = new Font(Font.HELVETICA, 8,  Font.BOLD);
        Font smallFont   = new Font(Font.HELVETICA, 7,  Font.NORMAL);

        // Header
        Paragraph title = new Paragraph(APARTMENT_NAME, titleFont);
        title.setAlignment(Element.ALIGN_CENTER); doc.add(title);
        Object periodLabelObj = data.get("periodLabel");
        String periodLabel = periodLabelObj != null ? periodLabelObj.toString() : String.valueOf(year);
        Paragraph sub = new Paragraph("COLLECTION STATEMENT — " + periodLabel, subtitleFont);
        sub.setAlignment(Element.ALIGN_CENTER); doc.add(sub);
        Paragraph gen = new Paragraph("Generated: " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MMM-yyyy")), normalFont);
        gen.setAlignment(Element.ALIGN_CENTER); doc.add(gen);
        doc.add(new Paragraph(" "));

        List<String> monthLabels = (List<String>) data.get("monthLabels");
        List<String> monthKeys   = (List<String>) data.get("monthKeys");
        List<Map<String, Object>> rows = (List<Map<String, Object>>) data.get("rows");
        Map<String, Object> colTotals  = (Map<String, Object>) data.get("columnTotals");

        int cols = 5 + (monthLabels != null ? monthLabels.size() : 0);
        PdfPTable table = new PdfPTable(cols);
        table.setWidthPercentage(100);
        table.setSpacingBefore(8f);

        Color headerBg = new Color(30, 30, 30);
        addCell(table, "Flat No",   boldFont, headerBg, Element.ALIGN_CENTER);
        addCell(table, "Owner Name",boldFont, headerBg, Element.ALIGN_CENTER);
        addCell(table, "Sq.Ft",     boldFont, headerBg, Element.ALIGN_CENTER);
        addCell(table, "Type",      boldFont, headerBg, Element.ALIGN_CENTER);
        addCell(table, "Maint.Val", boldFont, headerBg, Element.ALIGN_CENTER);
        if (monthLabels != null) {
            for (String m : monthLabels) addCell(table, m, boldFont, headerBg, Element.ALIGN_CENTER);
        }
        addCell(table, "TOTAL", boldFont, headerBg, Element.ALIGN_CENTER);

        boolean alt = false;
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                Color bg = alt ? new Color(245, 245, 245) : Color.WHITE;
                addCell(table, str(row.get("flatNo")),       normalFont, bg, Element.ALIGN_CENTER);
                addCell(table, str(row.get("ownerName")),    normalFont, bg, Element.ALIGN_LEFT);
                addCell(table, str(row.get("sqFt")),         normalFont, bg, Element.ALIGN_RIGHT);
                addCell(table, str(row.get("propertyType")), normalFont, bg, Element.ALIGN_CENTER);
                addCell(table, fmt(row.get("maintValue")),   normalFont, bg, Element.ALIGN_RIGHT);

                Map<String, Object> months = (Map<String, Object>) row.get("months");
                if (monthKeys != null && months != null) {
                    for (String key : monthKeys) {
                        Object amt = months.get(key);
                        String val = amt != null && !amt.toString().equals("0") ? fmt(amt) : "—";
                        addCell(table, val, normalFont, bg, Element.ALIGN_RIGHT);
                    }
                }
                addCell(table, fmt(row.get("total")), boldFont, bg, Element.ALIGN_RIGHT);
                alt = !alt;
            }
        }

        Color totalBg = new Color(220, 220, 220);
        addCell(table, "TOTAL", boldFont, totalBg, Element.ALIGN_CENTER);
        addCell(table, "", boldFont, totalBg, Element.ALIGN_CENTER);
        addCell(table, "", boldFont, totalBg, Element.ALIGN_CENTER);
        addCell(table, "", boldFont, totalBg, Element.ALIGN_CENTER);
        addCell(table, "", boldFont, totalBg, Element.ALIGN_CENTER);
        if (monthKeys != null && colTotals != null) {
            for (String key : monthKeys)
                addCell(table, fmt(colTotals.get(key)), boldFont, totalBg, Element.ALIGN_RIGHT);
        }
        addCell(table, fmt(data.get("grandTotal")), boldFont, totalBg, Element.ALIGN_RIGHT);

        doc.add(table);

        doc.add(new Paragraph(" "));
        PdfPTable summary = new PdfPTable(2);
        summary.setWidthPercentage(40);
        summary.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Color sumBg = new Color(245, 245, 245);
        addCell(summary, "Opening Balance",   boldFont, sumBg, Element.ALIGN_LEFT);
        addCell(summary, "₹ " + fmt(data.get("openingBalance")), normalFont, sumBg, Element.ALIGN_RIGHT);
        addCell(summary, "+ Total Collected", boldFont, sumBg, Element.ALIGN_LEFT);
        addCell(summary, "₹ " + fmt(data.get("totalCollected")), normalFont, sumBg, Element.ALIGN_RIGHT);
        addCell(summary, "- Total Expenses",  boldFont, sumBg, Element.ALIGN_LEFT);
        addCell(summary, "₹ " + fmt(data.get("totalExpenses")), normalFont, sumBg, Element.ALIGN_RIGHT);
        addCell(summary, "Closing Balance",   boldFont, new Color(220,220,220), Element.ALIGN_LEFT);
        addCell(summary, "₹ " + fmt(data.get("closingBalance")), boldFont, new Color(220,220,220), Element.ALIGN_RIGHT);
        addCell(summary, "Pending Dues",      boldFont, sumBg, Element.ALIGN_LEFT);
        addCell(summary, "₹ " + fmt(data.get("pendingDues")), normalFont, sumBg, Element.ALIGN_RIGHT);
        doc.add(summary);

        doc.close();
        return baos.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private byte[] buildExpensePdf(Map<String, Object> data) throws IOException {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 30, 30, 50, 30);
        PdfWriter.getInstance(doc, baos);
        doc.open();

        Font titleFont  = new Font(Font.HELVETICA, 13, Font.BOLD);
        Font boldFont   = new Font(Font.HELVETICA,  9, Font.BOLD);
        Font normalFont = new Font(Font.HELVETICA,  8, Font.NORMAL);

        Paragraph title = new Paragraph(APARTMENT_NAME, titleFont);
        title.setAlignment(Element.ALIGN_CENTER); doc.add(title);
        Paragraph sub = new Paragraph("EXPENSE STATEMENT — " + str(data.get("periodLabel")), boldFont);
        sub.setAlignment(Element.ALIGN_CENTER); doc.add(sub);
        Paragraph gen = new Paragraph("Generated: " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MMM-yyyy")), normalFont);
        gen.setAlignment(Element.ALIGN_CENTER); doc.add(gen);
        doc.add(new Paragraph(" "));

        PdfPTable table = new PdfPTable(6);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{0.5f, 1.5f, 3f, 2f, 1.5f, 1.5f});
        Color headerBg = new Color(30, 30, 30);
        for (String h : new String[]{"#","Date","Description","Category","Method","Amount"})
            addCell(table, h, boldFont, headerBg, Element.ALIGN_CENTER);

        List<Map<String, Object>> records = (List<Map<String, Object>>) data.get("records");
        boolean alt = false;
        if (records != null) {
            for (Map<String, Object> rec : records) {
                Color bg = alt ? new Color(245,245,245) : Color.WHITE;
                addCell(table, str(rec.get("slNo")),         normalFont, bg, Element.ALIGN_CENTER);
                addCell(table, fmtDate(str(rec.get("date"))),normalFont, bg, Element.ALIGN_CENTER);
                addCell(table, str(rec.get("description")),  normalFont, bg, Element.ALIGN_LEFT);
                addCell(table, str(rec.get("category")),     normalFont, bg, Element.ALIGN_LEFT);
                addCell(table, str(rec.get("paymentMethod")),normalFont, bg, Element.ALIGN_CENTER);
                addCell(table, "₹ " + fmt(rec.get("amount")),normalFont, bg, Element.ALIGN_RIGHT);
                alt = !alt;
            }
        }

        Color totalBg = new Color(220,220,220);
        PdfPCell totalLabel = new PdfPCell(new Phrase("TOTAL EXPENSES", boldFont));
        totalLabel.setColspan(5); totalLabel.setPadding(6);
        totalLabel.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totalLabel.setBackgroundColor(totalBg); totalLabel.setBorderColor(Color.GRAY);
        table.addCell(totalLabel);
        addCell(table, "₹ " + fmt(data.get("totalExpenses")), boldFont, totalBg, Element.ALIGN_RIGHT);
        doc.add(table);

        doc.add(new Paragraph(" "));
        PdfPTable summary = new PdfPTable(2);
        summary.setWidthPercentage(45);
        summary.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Color sumBg = new Color(245,245,245);
        addCell(summary, "Opening Balance",  boldFont, sumBg, Element.ALIGN_LEFT);
        addCell(summary, "₹ " + fmt(data.get("openingBalance")), normalFont, sumBg, Element.ALIGN_RIGHT);
        addCell(summary, "+ Total Income",   boldFont, sumBg, Element.ALIGN_LEFT);
        addCell(summary, "₹ " + fmt(data.get("totalIncome")), normalFont, sumBg, Element.ALIGN_RIGHT);
        addCell(summary, "- Total Expenses", boldFont, sumBg, Element.ALIGN_LEFT);
        addCell(summary, "₹ " + fmt(data.get("totalExpenses")), normalFont, sumBg, Element.ALIGN_RIGHT);
        addCell(summary, "Bank Balance",     boldFont, totalBg, Element.ALIGN_LEFT);
        addCell(summary, "₹ " + fmt(data.get("bankBalance")), boldFont, totalBg, Element.ALIGN_RIGHT);
        doc.add(summary);

        doc.close();
        return baos.toByteArray();
    }

    private void addCell(PdfPTable table, String text, Font font, Color bg, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", font));
        cell.setBackgroundColor(bg); cell.setPadding(5);
        cell.setHorizontalAlignment(align); cell.setBorderColor(Color.LIGHT_GRAY);
        table.addCell(cell);
    }


    @SuppressWarnings("unchecked")
    private byte[] buildCollectionExcel(Map<String, Object> data) throws IOException {

        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            XSSFSheet sheet = wb.createSheet("Collection Summary");

            XSSFCellStyle headerStyle = createStyle(wb, true, new XSSFColor(new byte[]{30,30,30}, null), IndexedColors.WHITE.getIndex());
            XSSFCellStyle dataStyle   = createStyle(wb, false, null, IndexedColors.BLACK.getIndex());
            XSSFCellStyle altStyle    = createStyle(wb, false,
                    new XSSFColor(new byte[]{(byte)245,(byte)245,(byte)245}, null),
                    IndexedColors.BLACK.getIndex());
            XSSFCellStyle totalStyle  = createStyle(wb, true,
                    new XSSFColor(new byte[]{(byte)220,(byte)220,(byte)220}, null),
                    IndexedColors.BLACK.getIndex());
            XSSFCellStyle titleStyle  = createStyle(wb, true, null, IndexedColors.BLACK.getIndex());

            int rowIdx = 0;

            Row t1 = sheet.createRow(rowIdx++);
            Cell c1 = t1.createCell(0); c1.setCellValue(APARTMENT_NAME); c1.setCellStyle(titleStyle);
            Row t2 = sheet.createRow(rowIdx++);
            Object periodLabelObj = data.get("periodLabel");
            String periodLabel = periodLabelObj != null ? periodLabelObj.toString() : String.valueOf(data.get("year"));
            Cell c2 = t2.createCell(0); c2.setCellValue("COLLECTION STATEMENT — " + periodLabel); c2.setCellStyle(titleStyle);
            Row t3 = sheet.createRow(rowIdx++);
            Cell c3 = t3.createCell(0); c3.setCellValue("Generated: " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MMM-yyyy"))); c3.setCellStyle(dataStyle);
            rowIdx++; // blank

            List<String> monthLabels = (List<String>) data.get("monthLabels");
            List<String> monthKeys   = (List<String>) data.get("monthKeys");
            List<Map<String, Object>> rows = (List<Map<String, Object>>) data.get("rows");
            Map<String, Object> colTotals  = (Map<String, Object>) data.get("columnTotals");

            Row header = sheet.createRow(rowIdx++);
            int col = 0;
            for (String h : new String[]{"Flat No","Owner Name","Sq.Ft","Type","Maint. Value"}) {
                Cell hc = header.createCell(col++); hc.setCellValue(h); hc.setCellStyle(headerStyle);
            }
            if (monthLabels != null) {
                for (String m : monthLabels) {
                    Cell hc = header.createCell(col++); hc.setCellValue(m); hc.setCellStyle(headerStyle);
                }
            }
            Cell totalHdr = header.createCell(col++); totalHdr.setCellValue("TOTAL"); totalHdr.setCellStyle(headerStyle);

            boolean alt = false;
            if (rows != null) {
                for (Map<String, Object> row : rows) {
                    Row r = sheet.createRow(rowIdx++);
                    XSSFCellStyle s = alt ? altStyle : dataStyle;
                    int c = 0;
                    setCellStr(r, c++, str(row.get("flatNo")), s);
                    setCellStr(r, c++, str(row.get("ownerName")), s);
                    setCellNum(r, c++, toDouble(row.get("sqFt")), s);
                    setCellStr(r, c++, str(row.get("propertyType")), s);
                    setCellNum(r, c++, toDouble(row.get("maintValue")), s);

                    Map<String, Object> months = (Map<String, Object>) row.get("months");
                    if (monthKeys != null && months != null) {
                        for (String key : monthKeys)
                            setCellNum(r, c++, toDouble(months.get(key)), s);
                    }
                    setCellNum(r, c++, toDouble(row.get("total")), totalStyle);
                    alt = !alt;
                }
            }

            Row totalRow = sheet.createRow(rowIdx++);
            int tc = 0;
            setCellStr(totalRow, tc++, "TOTAL", totalStyle);
            setCellStr(totalRow, tc++, "", totalStyle);
            setCellStr(totalRow, tc++, "", totalStyle);
            setCellStr(totalRow, tc++, "", totalStyle);
            setCellStr(totalRow, tc++, "", totalStyle);
            if (monthKeys != null && colTotals != null) {
                for (String key : monthKeys)
                    setCellNum(totalRow, tc++, toDouble(colTotals.get(key)), totalStyle);
            }
            setCellNum(totalRow, tc++, toDouble(data.get("grandTotal")), totalStyle);

            rowIdx++; // blank

            String[][] summary = {
                {"Opening Balance", "₹ " + fmt(data.get("openingBalance"))},
                {"+ Total Collected","₹ " + fmt(data.get("totalCollected"))},
                {"- Total Expenses", "₹ " + fmt(data.get("totalExpenses"))},
                {"Closing Balance",  "₹ " + fmt(data.get("closingBalance"))},
                {"Pending Dues",     "₹ " + fmt(data.get("pendingDues"))},
            };
            for (String[] sr : summary) {
                Row sr_row = sheet.createRow(rowIdx++);
                setCellStr(sr_row, 0, sr[0], totalStyle);
                setCellStr(sr_row, 1, sr[1], dataStyle);
            }

            for (int i = 0; i < Math.min(6, col); i++) sheet.autoSizeColumn(i);

            wb.write(baos);
            return baos.toByteArray();
        }
    }

    @SuppressWarnings("unchecked")
    private byte[] buildExpenseExcel(Map<String, Object> data) throws IOException {

        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            XSSFSheet sheet = wb.createSheet("Expense Statement");

            XSSFCellStyle headerStyle = createStyle(wb, true, new XSSFColor(new byte[]{30,30,30}, null), IndexedColors.WHITE.getIndex());
            XSSFCellStyle dataStyle   = createStyle(wb, false, null, IndexedColors.BLACK.getIndex());
            XSSFCellStyle altStyle    = createStyle(wb, false,
                    new XSSFColor(new byte[]{(byte)245,(byte)245,(byte)245}, null), IndexedColors.BLACK.getIndex());
            XSSFCellStyle totalStyle  = createStyle(wb, true,
                    new XSSFColor(new byte[]{(byte)220,(byte)220,(byte)220}, null), IndexedColors.BLACK.getIndex());
            XSSFCellStyle titleStyle  = createStyle(wb, true, null, IndexedColors.BLACK.getIndex());

            int rowIdx = 0;

            setCellStr(sheet.createRow(rowIdx++), 0, APARTMENT_NAME, titleStyle);
            setCellStr(sheet.createRow(rowIdx++), 0, "EXPENSE STATEMENT — " + data.get("periodLabel"), titleStyle);
            setCellStr(sheet.createRow(rowIdx++), 0, "Generated: " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MMM-yyyy")), dataStyle);
            rowIdx++;

            Row header = sheet.createRow(rowIdx++);
            String[] headers = {"#","Date","Description","Category","Method","Amount","Running Total"};
            for (int i = 0; i < headers.length; i++) {
                Cell hc = header.createCell(i); hc.setCellValue(headers[i]); hc.setCellStyle(headerStyle);
            }

            List<Map<String, Object>> records = (List<Map<String, Object>>) data.get("records");
            boolean alt = false;
            if (records != null) {
                for (Map<String, Object> rec : records) {
                    Row r = sheet.createRow(rowIdx++);
                    XSSFCellStyle s = alt ? altStyle : dataStyle;
                    setCellNum(r, 0, toDouble(rec.get("slNo")), s);
                    setCellStr(r, 1, fmtDate(str(rec.get("date"))), s);
                    setCellStr(r, 2, str(rec.get("description")), s);
                    setCellStr(r, 3, str(rec.get("category")), s);
                    setCellStr(r, 4, str(rec.get("paymentMethod")), s);
                    setCellNum(r, 5, toDouble(rec.get("amount")), s);
                    setCellNum(r, 6, toDouble(rec.get("runningTotal")), s);
                    alt = !alt;
                }
            }

            Row tot = sheet.createRow(rowIdx++);
            setCellStr(tot, 4, "TOTAL", totalStyle);
            setCellNum(tot, 5, toDouble(data.get("totalExpenses")), totalStyle);

            rowIdx++;

            String[][] summary = {
                {"Opening Balance", "₹ " + fmt(data.get("openingBalance"))},
                {"+ Total Income",  "₹ " + fmt(data.get("totalIncome"))},
                {"- Total Expenses","₹ " + fmt(data.get("totalExpenses"))},
                {"Bank Balance",    "₹ " + fmt(data.get("bankBalance"))},
            };
            for (String[] sr : summary) {
                Row sr_row = sheet.createRow(rowIdx++);
                setCellStr(sr_row, 0, sr[0], totalStyle);
                setCellStr(sr_row, 1, sr[1], dataStyle);
            }

            for (int i = 0; i < 7; i++) sheet.autoSizeColumn(i);

            wb.write(baos);
            return baos.toByteArray();
        }
    }

    private XSSFCellStyle createStyle(XSSFWorkbook wb, boolean bold, XSSFColor bg, short fgColor) {
        XSSFCellStyle s = wb.createCellStyle();
        XSSFFont f = wb.createFont(); f.setBold(bold); f.setColor(fgColor); s.setFont(f);
        if (bg != null) s.setFillForegroundColor(bg);
        if (bg != null) s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setBorderBottom(BorderStyle.THIN); s.setBorderTop(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);   s.setBorderRight(BorderStyle.THIN);
        s.setWrapText(false);
        return s;
    }
    private void setCellStr(Row r, int col, String val, XSSFCellStyle s) {
        Cell c = r.createCell(col); c.setCellValue(val != null ? val : ""); c.setCellStyle(s);
    }
    private void setCellNum(Row r, int col, double val, XSSFCellStyle s) {
        Cell c = r.createCell(col); c.setCellValue(val); c.setCellStyle(s);
    }

    // Current Financial Year start year (Apr → Mar convention), mirroring
    // ResidentPaymentSummaryService.getCurrentFinancialYearStart(). Used
    // only as the fallback when a Collection Statement request omits
    // `year` entirely — the frontend always sends an explicit year, so this
    // path is a defensive default, not part of the normal request flow.
    private int currentFinancialYearStart() {
        LocalDate now = LocalDate.now();
        return now.getMonthValue() >= 4 ? now.getYear() : now.getYear() - 1;
    }

    private String fmt(Object v) {
        if (v == null) return "0.00";
        if (v instanceof Number n) return String.format("%,.2f", n.doubleValue());
        return v.toString();
    }
    private String str(Object v) { return v == null ? "" : v.toString(); }
    private double toDouble(Object v) {
        if (v == null) return 0.0;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (Exception e) { return 0.0; }
    }
    private String fmtDate(String iso) {
        try {
            return LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("dd-MMM-yyyy"));
        } catch (Exception e) { return iso; }
    }

    private ResponseEntity<byte[]> pdfResponse(byte[] data, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .body(data);
    }
    private ResponseEntity<byte[]> xlsxResponse(byte[] data, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .body(data);
    }
}