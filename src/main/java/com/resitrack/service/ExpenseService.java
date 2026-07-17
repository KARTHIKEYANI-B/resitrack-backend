package com.resitrack.service;

import com.resitrack.dto.ExpenseRequest;
import com.resitrack.entity.Expense;
import com.resitrack.entity.ExpenseCategoryEntity;
import com.resitrack.exception.CustomException;
import com.resitrack.repository.ExpenseCategoryRepository;
import com.resitrack.repository.ExpenseRepository;
import com.resitrack.util.NumberToWordsUtil;
import com.lowagie.text.Document;
import com.lowagie.text.PageSize;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository         expenseRepo;
    private final ExpenseCategoryRepository categoryRepo;

    public List<String> getCategories() {
        return categoryRepo.findAllByOrderByNameAsc()
                .stream()
                .map(ExpenseCategoryEntity::getName)
                .toList();
    }

    public List<ExpenseCategoryEntity> getCategoryEntities() {
        return categoryRepo.findAllByOrderByNameAsc();
    }

    @Transactional
    public ExpenseCategoryEntity createCategory(String name) {
        String trimmed = validateCategoryName(name);
        if (categoryRepo.existsByNameIgnoreCase(trimmed))
            throw new CustomException(
                    "Category already exists: " + trimmed, HttpStatus.CONFLICT);
        return categoryRepo.save(ExpenseCategoryEntity.builder()
                .name(trimmed)
                .builtIn(false)
                .build());
    }

    @Transactional
    public ExpenseCategoryEntity updateCategory(Long id, String newName) {
        String trimmed = validateCategoryName(newName);
        ExpenseCategoryEntity cat = categoryRepo.findById(id)
                .orElseThrow(() -> new CustomException("Category not found", HttpStatus.NOT_FOUND));

        // Duplicate check — ignore the same record
        categoryRepo.findByNameIgnoreCase(trimmed).ifPresent(existing -> {
            if (!existing.getId().equals(id))
                throw new CustomException(
                        "Another category with that name already exists: " + trimmed,
                        HttpStatus.CONFLICT);
        });

        String oldName = cat.getName();
        cat.setName(trimmed);
        ExpenseCategoryEntity saved = categoryRepo.save(cat);

        // Update all expenses that reference the old name
        if (!oldName.equals(trimmed)) {
            expenseRepo.updateCategoryName(oldName, trimmed);
        }

        return saved;
    }

    @Transactional
    public void deleteCategory(Long id) {
        ExpenseCategoryEntity cat = categoryRepo.findById(id)
                .orElseThrow(() -> new CustomException("Category not found", HttpStatus.NOT_FOUND));
        categoryRepo.delete(cat);
    }

    private String validateCategoryName(String name) {
        if (name == null || name.isBlank())
            throw new CustomException("Category name must not be empty", HttpStatus.BAD_REQUEST);
        String trimmed = name.trim();
        if (trimmed.length() > 200)
            throw new CustomException("Category name too long (max 200 chars)", HttpStatus.BAD_REQUEST);
        return trimmed;
    }

    private void validateCategoryExists(String category) {
        if (category == null || category.isBlank())
            throw new CustomException("Expense category is required", HttpStatus.BAD_REQUEST);
        if (!categoryRepo.existsByNameIgnoreCase(category.trim()))
            throw new CustomException(
                    "Invalid expense category: \"" + category +
                    "\". Call GET /admin/expenses/categories for the allowed list.",
                    HttpStatus.BAD_REQUEST);
    }

    public List<Expense> getAll(String category) {
        if (category != null && !category.isBlank())
            return expenseRepo.findByCategory(category);
        return expenseRepo.findAll();
    }

    public Expense create(ExpenseRequest req) {
        validateCategoryExists(req.getCategory());
        Expense e = Expense.builder()
                .expenseName(req.getExpenseName())
                .category(req.getCategory().trim())
                .amount(req.getAmount() != null ? BigDecimal.valueOf(req.getAmount()) : BigDecimal.ZERO)
                .expenseDate(req.getExpenseDate())
                .paymentMethod(req.getPaymentMethod())
                .vendorStatus(req.getVendorStatus() != null
                        ? Expense.VendorStatus.valueOf(req.getVendorStatus())
                        : Expense.VendorStatus.PENDING)
                .description(req.getDescription())
                .build();
        return expenseRepo.save(e);
    }

    public Expense update(Long id, ExpenseRequest req) {
        Expense e = expenseRepo.findById(id)
                .orElseThrow(() -> new CustomException("Expense not found", HttpStatus.NOT_FOUND));

        validateCategoryExists(req.getCategory());

        e.setExpenseName(req.getExpenseName());
        e.setCategory(req.getCategory().trim());
        e.setAmount(req.getAmount() != null ? BigDecimal.valueOf(req.getAmount()) : e.getAmount());
        e.setExpenseDate(req.getExpenseDate());
        e.setPaymentMethod(req.getPaymentMethod());

        if (req.getVendorStatus() != null)
            e.setVendorStatus(Expense.VendorStatus.valueOf(req.getVendorStatus()));

        e.setDescription(req.getDescription());
        return expenseRepo.save(e);
    }

    public void delete(Long id) {
        if (!expenseRepo.existsById(id))
            throw new CustomException("Expense not found", HttpStatus.NOT_FOUND);
        expenseRepo.deleteById(id);
    }

    public Expense getById(Long id) {
        return expenseRepo.findById(id)
                .orElseThrow(() -> new CustomException("Expense not found", HttpStatus.NOT_FOUND));
    }

    /**
     * Generate a PDF Payment Voucher for the given expense, matching the
     * attached Payment Voucher reference format:
     *
     *   RR DHURYA
     *   Payment Voucher
     *   No. : <id>                            <date>  Dated :
     *   ┌─────────────────────────────┬─────────┐
     *   │ Particulars                 │  Amount │
     *   ├─────────────────────────────┼─────────┤
     *   │ Account :                   │         │
     *   │   <expense name>            │  amount │
     *   │ Through :                   │         │
     *   │   <paid through>            │         │
     *   │ On Account of :             │         │
     *   │   <description>             │         │
     *   │ Amount (in words) :         │         │
     *   │   INR <...> Only            │ ₹ total │
     *   └─────────────────────────────┴─────────┘
     *   Receiver's Signature:              Authorised Signatory
     *
     * Reuses the same voucher renderer as the Receipt Voucher
     * (ReceiptService.addVoucherDocument) so both PDFs share an identical
     * structure/format.
     */
    public byte[] generatePaymentVoucherPdf(Expense e) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            Document doc = new Document(PageSize.LETTER, 50f, 50f, 40f, 40f);
            PdfWriter.getInstance(doc, baos);
            doc.open();

            String dateStr = e.getExpenseDate() != null
                    ? e.getExpenseDate().format(DateTimeFormatter.ofPattern("d-MMM-yy")) : "—";
            BigDecimal amount = e.getAmount() != null ? e.getAmount() : BigDecimal.ZERO;

            ReceiptService.addVoucherDocument(doc,
                    "R R Dhurya Owners Welfare Association", "Payment Voucher",
                    String.valueOf(e.getId()), dateStr,
                    List.of(new ReceiptService.VoucherLine(e.getExpenseName(), amount, null, false)),
                    null, null,
                    e.getPaymentMethod(), e.getDescription() != null ? e.getDescription() : "",
                    NumberToWordsUtil.amountInWords(amount), amount,
                    true);

            doc.close();
            return baos.toByteArray();

        } catch (Exception ex) {
            throw new CustomException("Failed to generate payment voucher PDF: " + ex.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public Expense uploadReceipt(Long id, MultipartFile file, String uploadDir) throws IOException {
        Expense e = expenseRepo.findById(id)
                .orElseThrow(() -> new CustomException("Expense not found", HttpStatus.NOT_FOUND));

        String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path uploadPath = Paths.get(uploadDir);
        Files.createDirectories(uploadPath);
        Files.copy(file.getInputStream(), uploadPath.resolve(filename),
                StandardCopyOption.REPLACE_EXISTING);

        e.setReceiptFilePath(uploadPath.resolve(filename).toString());
        e.setReceiptFileName(file.getOriginalFilename());
        e.setReceiptFileType(file.getContentType());
        return expenseRepo.save(e);
    }
}