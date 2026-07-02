package com.resitrack.service;

public interface EmailService {

    /**
     * Sends the fixed "ResiTrack SMTP Test" email to {@code toEmail} using
     * {@code app.mail.from} (MAIL_FROM) as the sender.
     *
     * @param toEmail recipient address (already validated by the controller)
     * @throws com.resitrack.exception.EmailSendException if the SMTP send fails for any reason
     */
    void sendTestEmail(String toEmail);
}