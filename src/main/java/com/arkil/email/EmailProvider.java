package com.arkil.email;

/**
 * Email provider abstraction.
 * Implementations: SendGrid, SMTP, Resend, etc.
 */
public interface EmailProvider {

    /**
     * Send an email message.
     *
     * @param message The email message to send
     * @return true if sent successfully
     */
    boolean send(EmailMessage message);

    /**
     * Get the provider name for logging/debugging.
     */
    String getProviderName();
}
