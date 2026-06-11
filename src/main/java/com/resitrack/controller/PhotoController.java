package com.resitrack.controller;

import com.resitrack.dto.ApiResponse;
import com.resitrack.dto.ResidentDTO;
import com.resitrack.entity.Resident;
import com.resitrack.service.ResidentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/user/profile")
@PreAuthorize("hasRole('USER')")
@RequiredArgsConstructor
public class PhotoController {

    private final ResidentService residentService;

    @PostMapping(value = "/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ResidentDTO>> uploadPhoto(
            @RequestPart("photo") MultipartFile photo,
            Authentication auth) {
        Resident r = residentService.getByEmail(auth.getName());
        ResidentDTO updated = residentService.updateProfilePhoto(r.getId(), photo);
        return ResponseEntity.ok(ApiResponse.success("Profile photo updated", updated));
    }

    @DeleteMapping("/photo")
    public ResponseEntity<ApiResponse<ResidentDTO>> removePhoto(Authentication auth) {
        Resident r = residentService.getByEmail(auth.getName());
        ResidentDTO updated = residentService.removeProfilePhoto(r.getId());
        return ResponseEntity.ok(ApiResponse.success("Profile photo removed", updated));
    }
}