package com.resitrack.controller;

import com.resitrack.dto.TestEmailRequest;
import com.resitrack.dto.TestEmailResponse;
import com.resitrack.exception.EmailSendException;
import com.resitrack.service.EmailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TEMPORARY controller — SMTP integration verification only.
 *
 * POST /api/test/send-email
 * Body:   { "email": "recipient@gmail.com" }
 * Success: 200 { "success": true,  "message": "Test email sent successfully" }
 * Failure: 500 { "success": false, "message": "Failed to send email" }
 *
 * Scope guardrails:
 *  - Does NOT use, call, or modify AuthService, JwtTokenProvider,
 *    SecurityConfig's auth rules (beyond permitting this one new path),
 *    UserDetailsServiceImpl, or any login/registration flow.
 *  - No OTP or password-reset-token entities/tables are involved.
 *  - Delete this controller (and EmailService/EmailServiceImpl if unused
 *    elsewhere) once Forgot Password is implemented on top of EmailService,
 *    or keep it around as a standing SMTP health-check — either is fine,
 *    it has zero coupling to anything else.
 */
@Slf4j
@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class EmailTestController {

    private final EmailService emailService;

    @PostMapping("/send-email")
    public ResponseEntity<TestEmailResponse> sendTestEmail(@Valid @RequestBody TestEmailRequest req) {
        try {
            emailService.sendTestEmail(req.getEmail());
            return ResponseEntity.ok(
                    TestEmailResponse.success("Test email sent successfully"));

        } catch (EmailSendException ex) {
            // Real reason already logged in EmailServiceImpl; response body
            // stays the fixed, non-leaky message from the requirements.
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(TestEmailResponse.failure("Failed to send email"));

        } catch (Exception ex) {
            // Safety net for anything unexpected (e.g. mail sender bean
            // misconfiguration) so this test endpoint never 500s with a
            // generic unhandled-exception body instead of the agreed shape.
            log.error("Email send failure: to={}, reason={}", req.getEmail(), ex.getMessage(), ex);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(TestEmailResponse.failure("Failed to send email"));
        }
    }
}