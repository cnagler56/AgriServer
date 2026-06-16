package com.home.Service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;



@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${app.mail.from:no-reply@just4ag.com}")
    private String fromAddress;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void send(String to, String subject, String body) {
        if (mailHost == null || mailHost.isBlank()) {
            System.out.println("""
                ────────────────────────────────────────────────────────────
                [EMAIL — not sent, no SMTP configured]
                  To:      %s
                  Subject: %s

                %s
                ────────────────────────────────────────────────────────────""".formatted(to, subject, body));
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromAddress);
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            mailSender.send(msg);
            System.out.println("[EMAIL] sent '" + subject + "' to " + to);
        } catch (Exception e) {
            System.err.println("[EMAIL] failed to send to " + to + ": "
                + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }
}
