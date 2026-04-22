package com.arkil.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "arkil.email.provider", havingValue = "smtp")
public class SmtpEmailProvider implements EmailProvider {

    private final JavaMailSender mailSender;

    @Value("${arkil.email.from:noreply@arkil.io}")
    private String fromAddress;

    @Override
    public boolean send(EmailMessage message) {
        try {
            var mimeMessage = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(message.getTo());
            helper.setSubject(message.getSubject());
            helper.setText(
                    message.getPlainText() != null ? message.getPlainText() : "",
                    message.getHtmlContent() != null ? message.getHtmlContent() : ""
            );
            mailSender.send(mimeMessage);
            return true;
        } catch (Exception ex) {
            log.error("SMTP email send failed for {}", message.getTo(), ex);
            return false;
        }
    }

    @Override
    public String getProviderName() {
        return "smtp";
    }
}
