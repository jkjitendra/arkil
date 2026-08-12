package com.arkil.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;

/**
 * Email service for sending transactional emails.
 * Uses templated emails with Thymeleaf.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final EmailProvider emailProvider;
    private final TemplateEngine templateEngine;

    @Value("${arkil.email.from:noreply@arkil.io}")
    private String fromAddress;

    @Value("${arkil.email.base-url}")
    private String baseUrl;

    /**
     * Send email verification email.
     */
    public void sendVerificationEmail(String to, String token, String username) {
        String verifyUrl = baseUrl + "/auth/verify-email?token=" + token;

        Context context = new Context();
        context.setVariable("username", username);
        context.setVariable("verifyUrl", verifyUrl);
        context.setVariable("expiryHours", 24);

        String html = templateEngine.process("email/verification", context);
        String plainText = String.format("""
                Hi %s,
                
                Please verify your email by clicking the link below:
                %s
                
                This link expires in 24 hours.
                
                If you didn't create an account, please ignore this email.
                
                - Arkil Team
                """, username, verifyUrl);

        EmailMessage message = EmailMessage.builder()
                .to(to)
                .subject("Verify your email - Arkil")
                .htmlContent(html)
                .plainText(plainText)
                .build();

        send(message);
    }

    /**
     * Send password reset email.
     */
    public void sendPasswordResetEmail(String to, String token, String username) {
        String resetUrl = baseUrl + "/reset-password?token=" + token;

        Context context = new Context();
        context.setVariable("username", username);
        context.setVariable("resetUrl", resetUrl);
        context.setVariable("expiryMinutes", 30);

        String html = templateEngine.process("email/password-reset", context);
        String plainText = String.format("""
                Hi %s,
                
                You requested a password reset. Click the link below to reset your password:
                %s
                
                This link expires in 30 minutes.
                
                If you didn't request this, please ignore this email.
                
                - Arkil Team
                """, username, resetUrl);

        EmailMessage message = EmailMessage.builder()
                .to(to)
                .subject("Reset your password - Arkil")
                .htmlContent(html)
                .plainText(plainText)
                .build();

        send(message);
    }

    /**
     * Send magic link email for passwordless login.
     */
    public void sendMagicLinkEmail(String to, String token, String username) {
        String magicUrl = baseUrl + "/auth/magic-link/verify?token=" + token;

        Context context = new Context();
        context.setVariable("username", username);
        context.setVariable("magicUrl", magicUrl);
        context.setVariable("expiryMinutes", 15);

        String html = templateEngine.process("email/magic-link", context);
        String plainText = String.format("""
                Hi %s,
                
                Click the link below to sign in:
                %s
                
                This link expires in 15 minutes.
                
                If you didn't request this, please ignore this email.
                
                - Arkil Team
                """, username, magicUrl);

        EmailMessage message = EmailMessage.builder()
                .to(to)
                .subject("Sign in to Arkil")
                .htmlContent(html)
                .plainText(plainText)
                .build();

        send(message);
    }

    /**
     * Send login alert for new device.
     */
    public void sendLoginAlertEmail(String to, String username, String device, String location, String ip) {
        Context context = new Context();
        context.setVariable("username", username);
        context.setVariable("device", device);
        context.setVariable("location", location);
        context.setVariable("ip", ip);

        String html = templateEngine.process("email/login-alert", context);
        String plainText = String.format("""
                Hi %s,
                
                We noticed a new sign-in to your account:
                
                Device: %s
                Location: %s
                IP: %s
                
                If this wasn't you, please secure your account immediately.
                
                - Arkil Team
                """, username, device, location, ip);

        EmailMessage message = EmailMessage.builder()
                .to(to)
                .subject("New sign-in to your account - Arkil")
                .htmlContent(html)
                .plainText(plainText)
                .build();

        send(message);
    }

    private void send(EmailMessage message) {
        log.debug("Sending email to {} via {}", message.getTo(), emailProvider.getProviderName());
        boolean success = emailProvider.send(message);
        if (!success) {
            log.error("Failed to send email to {}", message.getTo());
        }
    }
}
