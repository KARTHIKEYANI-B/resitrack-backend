package com.resitrack.service;

import com.resitrack.exception.EmailSendException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * TEMPORARY — SMTP verification implementation.
 *
 * Sends a fixed plain-text test email through Spring Mail, backed by the
 * Brevo SMTP relay configured in application.properties (host/port/username/
 * password all come from the BREVO_SMTP_* env vars on Render; the "From"
 * address comes from MAIL_FROM / app.mail.from).
 *
 * This class intentionally does NOT touch AuthService, SecurityConfig's
 * authentication logic, JwtTokenProvider, or any resident/admin/security
 * repository — it only depends on Spring's JavaMailSender.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String mailFrom;

    private static final String TEST_SUBJECT = "RR Dhurya SMTP Test";

    private static final String TEST_BODY =
            "Hello,\n\n" +
            "This is a test email sent from RR Dhurya using Brevo SMTP.\n" +
            "If you received this email, the SMTP integration is working successfully.\n\n" +
            "Regards,\n" +
            "RR Dhurya Team";

    @Override
    public void sendTestEmail(String toEmail) {
        log.info("Email send attempt: to={}, from={}, subject=\"{}\"",
                toEmail, mailFrom, TEST_SUBJECT);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(toEmail);
        message.setSubject(TEST_SUBJECT);
        message.setText(TEST_BODY);

        try {
            mailSender.send(message);
            log.info("Email send success: to={}", toEmail);
        } catch (MailException ex) {
            // Log the real SMTP failure reason for debugging (auth failure,
            // connection timeout, sender not verified, etc.) but never leak
            // it into the API response — the controller returns the fixed
            // "Failed to send email" message regardless of cause.
            log.error("Email send failure: to={}, reason={}", toEmail, ex.getMessage(), ex);
            throw new EmailSendException("Failed to send email", ex);
        }
    }
}