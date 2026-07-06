package com.resitrack.controller;

import com.resitrack.dto.ApiResponse;
import com.resitrack.dto.BatchDueDTO;
import com.resitrack.dto.BatchPaymentSubmitRequest;
import com.resitrack.entity.Resident;
import com.resitrack.service.BatchPaymentService;
import com.resitrack.service.ResidentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user/maintenance-batch-dues")
@PreAuthorize("hasRole('USER')")
@RequiredArgsConstructor
public class BatchPaymentUserController {

    private final BatchPaymentService batchPaymentService;
    private final ResidentService     residentService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<BatchDueDTO>>> getMyDues(Authentication auth) {
        Resident raw   = residentService.getByEmail(auth.getName());
        Resident owner = residentService.getEffectiveOwnerResident(raw);
        return ResponseEntity.ok(ApiResponse.success(batchPaymentService.getDuesForResident(owner.getId())));
    }

    /** Requirement 4: Owner or Family Member can pay the batch amount. */
    @PostMapping("/{batchPaymentId}/pay")
    public ResponseEntity<ApiResponse<BatchDueDTO>> pay(
            @PathVariable Long batchPaymentId,
            @RequestBody BatchPaymentSubmitRequest req,
            Authentication auth) {

        Resident raw   = residentService.getByEmail(auth.getName());
        Resident owner = residentService.getEffectiveOwnerResident(raw);

        BatchDueDTO result = batchPaymentService.submitPayment(
                owner.getId(), raw.getId(), batchPaymentId, req);

        return ResponseEntity.ok(ApiResponse.success(
                "Payment submitted. Admin will verify shortly.", result));
    }
}
