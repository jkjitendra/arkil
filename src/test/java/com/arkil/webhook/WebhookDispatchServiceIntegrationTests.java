package com.arkil.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class WebhookDispatchServiceIntegrationTests {

    @Autowired private WebhookDispatchService webhookDispatchService;
    @Autowired private ObjectMapper objectMapper;

    @Test
    @DisplayName("Webhook dispatch builds the stable envelope and computes the signature input")
    void dispatchBuildsStableEnvelopeAndSignature() throws Exception {
        String deliveryId = UUID.randomUUID().toString();
        Instant timestamp = Instant.parse("2026-04-15T18:00:00Z");

        Map<String, Object> envelope = webhookDispatchService.buildEnvelope(
                deliveryId,
                "session.created",
                timestamp,
                Map.of(
                        "event", "session.created",
                        "project", Map.of(
                                "id", UUID.randomUUID().toString(),
                                "clientId", "proj_demo-app",
                                "slug", "demo-app"
                        ),
                        "actor", Map.of(
                                "id", "user-1",
                                "type", "user"
                        ),
                        "subject", Map.of(
                                "id", "user-1",
                                "email", "demo@example.com"
                        ),
                        "data", Map.of(
                                "authMethod", "password"
                        )
                )
        );

        String body = objectMapper.writeValueAsString(envelope);
        String signature = webhookDispatchService.computeSignature("whsec_test_secret",
                String.valueOf(timestamp.getEpochSecond()), body);

        assertThat(envelope).containsEntry("id", deliveryId);
        assertThat(envelope).containsEntry("event", "session.created");
        assertThat(envelope).containsEntry("timestamp", "2026-04-15T18:00:00Z");
        assertThat(envelope).containsKeys("project", "actor", "subject", "data");
        assertThat(signature).matches("^[0-9a-f]{64}$");

        String recomputed = webhookDispatchService.computeSignature("whsec_test_secret",
                String.valueOf(timestamp.getEpochSecond()), body);
        assertThat(recomputed).isEqualTo(signature);
    }
}
