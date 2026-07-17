package com.resitrack.controller;

import com.resitrack.dto.*;
import com.resitrack.entity.Expense;
import com.resitrack.entity.ExpenseCategoryEntity;
import com.resitrack.service.ExpenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/expenses")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;

    @Value("${app.upload.dir:./uploads}")
    private String uploadDir;

    @GetMapping("/categories")
    public ResponseEntity<List<String>> getCategories() {
        return ResponseEntity.ok(expenseService.getCategories());
    }

    @GetMapping("/categories/all")
    public ResponseEntity<List<ExpenseCategoryEntity>> getAllCategoryEntities() {
        return ResponseEntity.ok(expenseService.getCategoryEntities());
    }

    @PostMapping("/categories")
    public ResponseEntity<ApiResponse<ExpenseCategoryEntity>> createCategory(
            @RequestBody Map<String, String> body) {
        String name = body.getOrDefault("name", "").trim();
        return ResponseEntity.ok(
                ApiResponse.success("Category created", expenseService.createCategory(name)));
    }

    @PutMapping("/categories/{id}")
    public ResponseEntity<ApiResponse<ExpenseCategoryEntity>> updateCategory(
            @PathVariable Long id, @RequestBody Map<String, String> body) {
        String name = body.getOrDefault("name", "").trim();
        return ResponseEntity.ok(
                ApiResponse.success("Category updated", expenseService.updateCategory(id, name)));
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable Long id) {
        expenseService.deleteCategory(id);
        return ResponseEntity.ok(ApiResponse.success("Category deleted", null));
    }

    @GetMapping
    public ResponseEntity<List<Expense>> getAll(
            @RequestParam(required = false) String category) {
        return ResponseEntity.ok(expenseService.getAll(category));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Expense>> create(@RequestBody ExpenseRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Created", expenseService.create(req)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Expense>> update(
            @PathVariable Long id, @RequestBody ExpenseRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Updated", expenseService.update(id, req)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        expenseService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Deleted", null));
    }

    @PostMapping("/{id}/receipt")
    public ResponseEntity<ApiResponse<Expense>> uploadReceipt(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(ApiResponse.success("Uploaded",
                expenseService.uploadReceipt(id, file, uploadDir)));
    }

    @GetMapping("/{id}/voucher")
    public ResponseEntity<byte[]> downloadPaymentVoucher(@PathVariable Long id) {
        Expense expense = expenseService.getById(id);
        byte[] pdf = expenseService.generatePaymentVoucherPdf(expense);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"payment-voucher-" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}