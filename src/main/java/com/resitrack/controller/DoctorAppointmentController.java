package com.resitrack.controller;

import com.resitrack.dto.ApiResponse;
import com.resitrack.dto.DoctorAppointmentDTO;
import com.resitrack.entity.Resident;
import com.resitrack.service.DoctorAppointmentService;
import com.resitrack.service.ResidentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Personal Management — Medical: Doctor Appointments. Ownership is always
 * resolved from the JWT-backed {@link Authentication} principal, never from
 * a client-supplied id, so a resident can only ever read or write their own
 * appointments.
 */
@RestController
@RequestMapping("/user/medical/appointments")
@PreAuthorize("hasRole('USER')")
@RequiredArgsConstructor
public class DoctorAppointmentController {

    private final DoctorAppointmentService appointmentService;
    private final ResidentService          residentService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<DoctorAppointmentDTO.Response>>> getMine(Authentication auth) {
        Resident r = residentService.getByEmail(auth.getName());
        return ResponseEntity.ok(ApiResponse.success(appointmentService.getMine(r.getId())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DoctorAppointmentDTO.Response>> getOne(
            @PathVariable Long id, Authentication auth) {
        Resident r = residentService.getByEmail(auth.getName());
        return ResponseEntity.ok(ApiResponse.success(appointmentService.getById(id, r.getId())));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DoctorAppointmentDTO.Response>> add(
            @Valid @RequestBody DoctorAppointmentDTO.Request req, Authentication auth) {
        Resident r = residentService.getByEmail(auth.getName());
        DoctorAppointmentDTO.Response created = appointmentService.add(r.getId(), req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Appointment added", created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<DoctorAppointmentDTO.Response>> update(
            @PathVariable Long id, @Valid @RequestBody DoctorAppointmentDTO.Request req, Authentication auth) {
        Resident r = residentService.getByEmail(auth.getName());
        return ResponseEntity.ok(ApiResponse.success("Appointment updated",
                appointmentService.update(id, r.getId(), req)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> remove(@PathVariable Long id, Authentication auth) {
        Resident r = residentService.getByEmail(auth.getName());
        appointmentService.remove(id, r.getId());
        return ResponseEntity.ok(ApiResponse.success("Appointment removed", null));
    }
}
