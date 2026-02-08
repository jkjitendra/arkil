package com.arkil.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Console email provider for development.
 * Logs emails instead of sending them.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "arkil.email.provider", havingValue = "console", matchIfMissing = true)
public class ConsoleEmailProvider implements EmailProvider {

    @Override
    public boolean send(EmailMessage message) {
        log.info("""
                
                ╔══════════════════════════════════════════════════════════════╗
                ║                    EMAIL (Console Mode)                       ║
                ╠══════════════════════════════════════════════════════════════╣
                ║ To:      {}
                ║ Subject: {}
                ╠══════════════════════════════════════════════════════════════╣
                {}
                ╚══════════════════════════════════════════════════════════════╝
                """,
                message.getTo(),
                message.getSubject(),
                message.getPlainText() != null ? message.getPlainText() : "(HTML only)");

        return true;
    }

    @Override
    public String getProviderName() {
        return "console";
    }
}
