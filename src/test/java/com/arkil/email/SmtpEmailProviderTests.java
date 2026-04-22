package com.arkil.email;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SmtpEmailProviderTests {

    @Test
    void sendsEmailThroughJavaMailSender() {
        RecordingMailSender mailSender = new RecordingMailSender();

        SmtpEmailProvider provider = new SmtpEmailProvider(mailSender);
        ReflectionTestUtils.setField(provider, "fromAddress", "noreply@arkil.io");

        boolean sent = provider.send(EmailMessage.builder()
                .to("user@example.com")
                .subject("Test")
                .plainText("Hello")
                .htmlContent("<p>Hello</p>")
                .build());

        assertTrue(sent);
        assertTrue(mailSender.sent);
    }

    private static final class RecordingMailSender implements JavaMailSender {
        private final MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        private boolean sent;

        @Override
        public MimeMessage createMimeMessage() {
            return mimeMessage;
        }

        @Override
        public MimeMessage createMimeMessage(java.io.InputStream contentStream) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void send(MimeMessage mimeMessage) {
            this.sent = true;
        }

        @Override
        public void send(MimeMessage... mimeMessages) {
            this.sent = true;
        }

        @Override
        public void send(org.springframework.mail.SimpleMailMessage simpleMessage) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void send(org.springframework.mail.SimpleMailMessage... simpleMessages) {
            throw new UnsupportedOperationException();
        }
    }
}
