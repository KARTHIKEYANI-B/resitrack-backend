package com.resitrack.controller;

import com.resitrack.dto.ApiResponse;
import com.resitrack.entity.AppSettings;
import com.resitrack.service.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AppSettings>> getSettings() {
        return ResponseEntity.ok(ApiResponse.success(settingsService.getSettings()));
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AppSettings>> updateSettings(@RequestBody AppSettings settings) {
        return ResponseEntity.ok(ApiResponse.success("Settings saved", settingsService.updateSettings(settings)));
    }
}
