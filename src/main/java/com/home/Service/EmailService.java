package com.home.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Sends transactional email (password-reset links, etc.). Tries, in order:
 *
 *   1. Brevo HTTP API (https, port 443) when BREVO_API_KEY is set — this is the
 *      production path, because PaaS hosts like Railway block outbound SMTP
 *      (25/465/587/2525), so JavaMailSender connections just time out.
 *   2. SMTP via JavaMailSender when spring.mail.host is set (local dev).
 *   3. Otherwise, log the message to the console so flows still work with no
 *      mail provider configured.
 */
@Service
public class EmailService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${app.mail.from:no-reply@just4ag.com}")
    private String fromAddress;

    @Value("${app.mail.from-name:Just4Ag}")
    private String fromName;

    @Value("${BREVO_API_KEY:}")
    private String brevoApiKey;

    public EmailService(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSenderProvider = mailSenderProvider;
    }

    public void send(String to, String subject, String body) {
        // 1. Preferred in prod: Brevo HTTP API (not blocked by SMTP egress rules).
        if (brevoApiKey != null && !brevoApiKey.isBlank()) {
            if (sendViaBrevoApi(to, subject, body)) return;
            // fall through to SMTP/console if the API call failed
        }

        // 2. SMTP, if configured and the mail bean is available.
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailHost != null && !mailHost.isBlank() && mailSender != null) {
            try {
                SimpleMailMessage msg = new SimpleMailMessage();
                msg.setFrom(fromAddress);
                msg.setTo(to);
                msg.setSubject(subject);
                msg.setText(body);
                mailSender.send(msg);
                System.out.println("[EMAIL] sent '" + subject + "' to " + to + " (smtp)");
                return;
            } catch (Exception e) {
                System.err.println("[EMAIL] SMTP send to " + to + " failed: "
                    + e.getClass().getSimpleName() + " - " + e.getMessage());
                // fall through to console so the message isn't silently lost
            }
        }

        // 3. No provider (or all failed): log it.
        System.out.println("""
            ────────────────────────────────────────────────────────────
            [EMAIL — not sent, no working mail provider]
              To:      %s
              Subject: %s

            %s
            ────────────────────────────────────────────────────────────""".formatted(to, subject, body));
    }

    /** Posts the message to Brevo's transactional email API. Returns true on success. */
    private boolean sendViaBrevoApi(String to, String subject, String body) {
        try {
            ObjectNode payload = mapper.createObjectNode();
            ObjectNode sender = payload.putObject("sender");
            sender.put("email", fromAddress);
            if (fromName != null && !fromName.isBlank()) sender.put("name", fromName);
            payload.putArray("to").addObject().put("email", to);
            payload.put("subject", subject);
            payload.put("textContent", body);

            HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.brevo.com/v3/smtp/email"))
                .timeout(Duration.ofSeconds(15))
                .header("api-key", brevoApiKey)
                .header("content-type", "application/json")
                .header("accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 == 2) {
                System.out.println("[EMAIL] sent '" + subject + "' to " + to + " (brevo api)");
                return true;
            }
            System.err.println("[EMAIL] Brevo API rejected send to " + to
                + " (" + resp.statusCode() + "): " + resp.body());
            return false;
        } catch (Exception e) {
            System.err.println("[EMAIL] Brevo API error sending to " + to + ": "
                + e.getClass().getSimpleName() + " - " + e.getMessage());
            return false;
        }
    }
}
