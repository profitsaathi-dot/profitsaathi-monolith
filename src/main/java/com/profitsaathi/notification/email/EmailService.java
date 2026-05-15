package com.profitsaathi.notification.email;

import com.profitsaathi.notification.log.EmailLog;
import com.profitsaathi.notification.log.EmailLogRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.File;
import java.util.*;

/**
 * In-process replacement for the Kafka {@code email-topic} consumer.
 *
 * <p>Callers (OTP, welcome mail, order updates) inject this directly and call
 * {@link #send(EmailRequest)}. The {@code @Async} executor returns the caller's
 * thread instantly so user-facing requests don't block on SMTP. Transient
 * failures are retried in-process with exponential backoff (5s → 10s →
 * exhausted) — the same semantics the old Kafka {@code @RetryableTopic} gave us.
 *
 * <p>Status semantics in {@code email_log}:
 * <ul>
 *   <li>SUCCESS — SMTP accepted the message</li>
 *   <li>FAILED — one delivery attempt failed (retry pending)</li>
 *   <li>FAILED_PERMANENT — written by {@link #recover(MailException, EmailRequest)}
 *       after all retries exhausted (replaces the old Kafka DLT row)</li>
 * </ul>
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final EmailLogRepository emailLogRepository;
    private final TemplateEngine templateEngine;
    private final String fromMail;

    public EmailService(JavaMailSender mailSender,
                        EmailLogRepository emailLogRepository,
                        TemplateEngine templateEngine,
                        @Value("${spring.mail.from}") String fromMail) {
        this.mailSender = mailSender;
        this.emailLogRepository = emailLogRepository;
        this.templateEngine = templateEngine;
        this.fromMail = fromMail;
    }

    @Async("notificationExecutor")
    @Retryable(
            retryFor = { MailException.class, MessagingException.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 5000, multiplier = 2.0)
    )
    public void send(EmailRequest req) {
        Set<String> validTo = filterValidEmails(req.to());
        Set<String> validCc = filterValidEmails(req.cc());
        validCc.removeAll(validTo);

        if (validTo.isEmpty()) {
            log.warn("Dropping email — no valid recipients: subject={}", req.subject());
            return;
        }

        try {
            Context context = new Context();
            if (req.variables() != null) context.setVariables(req.variables());
            String html = templateEngine.process("email/" + req.templateName(), context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromMail);
            helper.setTo(validTo.toArray(new String[0]));
            if (!validCc.isEmpty()) {
                helper.setCc(validCc.toArray(new String[0]));
            }
            helper.setSubject(req.subject());
            helper.setText(html, true);

            if (req.attachmentPath() != null) {
                File file = new File(req.attachmentPath());
                if (file.exists()) {
                    helper.addAttachment(file.getName(), new FileSystemResource(file));
                } else {
                    log.warn("Attachment not found: {}", req.attachmentPath());
                }
            }

            mailSender.send(message);
            emailLogRepository.save(new EmailLog(String.join(",", validTo),
                    req.subject(), "SUCCESS", null));
            log.info("Email sent to {}", validTo);
        } catch (MailException | MessagingException e) {
            // Persist a FAILED row for retry visibility, then re-throw so
            // @Retryable schedules the next attempt.
            emailLogRepository.save(new EmailLog(String.join(",", validTo),
                    req.subject(), "FAILED", e.getMessage()));
            log.warn("Email send failed (will retry): to={}, err={}", validTo, e.getMessage());
            if (e instanceof MailException me) throw me;
            throw new RuntimeException(e);
        }
    }

    @Recover
    public void recover(MailException e, EmailRequest req) {
        Set<String> validTo = filterValidEmails(req.to());
        emailLogRepository.save(new EmailLog(String.join(",", validTo),
                req.subject(), "FAILED_PERMANENT", e.getMessage()));
        log.error("Email send permanently failed after retries: to={}, err={}", validTo, e.getMessage());
    }

    private Set<String> filterValidEmails(List<String> emails) {
        Set<String> valid = new LinkedHashSet<>();
        if (emails == null) return valid;
        for (String e : emails) {
            if (isValidEmail(e)) valid.add(e.trim());
            else if (e != null && !e.isBlank()) log.warn("Invalid email skipped: {}", e);
        }
        return valid;
    }

    private boolean isValidEmail(String e) {
        return e != null && !e.trim().isEmpty() && e.contains("@") && e.contains(".");
    }
}
