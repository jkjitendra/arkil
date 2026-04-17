package com.arkil.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Dispatches webhook events to configured endpoints.
 * Features:
 * - HMAC-SHA256 payload signing
 * - Async processing to avoid blocking auth flows
 * - Retry with exponential backoff (up to 3 attempts)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookDispatchService {

    private final WebhookRepository webhookRepository;
    private final ObjectMapper objectMapper;

    private static final int MAX_RETRIES = 3;
    private static final int[] RETRY_DELAYS_MS = {1000, 2000, 4000};
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    /**
     * Dispatch an event to all enabled webhooks for a project that subscribe to the event type.
     *
     * @param projectId The project that generated the event
     * @param eventType The event type (e.g., "user.created", "session.created")
     * @param payload   The event payload data
     */
    @Async
    public void dispatchEvent(UUID projectId, String eventType, Map<String, Object> payload) {
        List<Webhook> webhooks = webhookRepository.findByProjectIdAndEnabledTrue(projectId);

        for (Webhook webhook : webhooks) {
            if (webhook.subscribesTo(eventType)) {
                deliverToWebhook(webhook, eventType, payload);
            }
        }
    }

    /**
     * Send a test/ping event to a specific webhook.
     */
    public boolean sendPing(Webhook webhook) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", "webhook.test");
        payload.put("data", Map.of("message", "This is a test ping from Arkil"));

        return deliverToWebhook(webhook, "webhook.test", payload);
    }

    /**
     * Deliver a payload to a single webhook with retry logic.
     */
    private boolean deliverToWebhook(Webhook webhook, String eventType, Map<String, Object> payload) {
        String deliveryId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        String timestamp = String.valueOf(now.getEpochSecond());

        try {
            Map<String, Object> envelope = buildEnvelope(deliveryId, eventType, now, payload);

            String body = objectMapper.writeValueAsString(envelope);
            String signature = computeSignature(webhook.getSecret(), timestamp, body);

            for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
                try {
                    int statusCode = sendHttpRequest(webhook.getUrl(), body, signature, eventType, deliveryId, timestamp);

                    if (statusCode >= 200 && statusCode < 300) {
                        log.info("Webhook delivered: id={}, event={}, project={}, url={}, status={}",
                                deliveryId, eventType, webhook.getProjectId(), webhook.getUrl(), statusCode);
                        return true;
                    }

                    log.warn("Webhook delivery failed: id={}, event={}, project={}, url={}, status={}, attempt={}/{}",
                            deliveryId, eventType, webhook.getProjectId(), webhook.getUrl(), statusCode, attempt + 1, MAX_RETRIES);
                } catch (Exception e) {
                    log.warn("Webhook delivery error: id={}, event={}, project={}, url={}, attempt={}/{}, error={}",
                            deliveryId, eventType, webhook.getProjectId(), webhook.getUrl(), attempt + 1, MAX_RETRIES, e.getMessage());
                }

                // Wait before retry (except on last attempt)
                if (attempt < MAX_RETRIES - 1) {
                    try {
                        Thread.sleep(RETRY_DELAYS_MS[attempt]);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
            }

            log.error("Webhook delivery exhausted retries: id={}, event={}, project={}, url={}",
                    deliveryId, eventType, webhook.getProjectId(), webhook.getUrl());
            return false;

        } catch (Exception e) {
            log.error("Webhook dispatch error: webhook={}, event={}, error={}",
                    webhook.getId(), eventType, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Send the HTTP POST request to the webhook URL.
     */
    private int sendHttpRequest(String url, String body, String signature,
                                String eventType, String deliveryId, String timestamp) throws Exception {
        try (HttpClient client = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build()) {

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Arkil-Webhooks/1.0")
                    .header("X-Arkil-Signature", "sha256=" + signature)
                    .header("X-Arkil-Event", eventType)
                    .header("X-Arkil-Delivery", deliveryId)
                    .header("X-Arkil-Timestamp", timestamp)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode();
        }
    }

    /**
     * Compute HMAC-SHA256 signature.
     * Signature input: "{timestamp}.{body}" to prevent replay attacks.
     */
    String computeSignature(String secret, String timestamp, String body) {
        try {
            String signatureInput = timestamp + "." + body;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(signatureInput.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute webhook signature", e);
        }
    }

    Map<String, Object> buildEnvelope(String deliveryId, String eventType, Instant timestamp, Map<String, Object> payload) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("id", deliveryId);
        envelope.put("event", eventType);
        envelope.put("timestamp", timestamp.toString());
        envelope.putAll(payload);
        return envelope;
    }
}
