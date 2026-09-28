package com.arkil.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class DashboardLogoutIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void staleDashboardTokenSignsOutToRegisteredRedirect() throws Exception {
        when(jwtDecoder.decode("older-signed-token")).thenReturn(dashboardIdToken());

        mockMvc.perform(get("/connect/logout")
                        .queryParam("id_token_hint", "older-signed-token")
                        .queryParam("post_logout_redirect_uri", "http://localhost:5173/")
                        .session(new MockHttpSession(null, "session-id"))
                        .with(user("user-id")))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "http://localhost:5173/"));
    }

    @Test
    void staleDashboardTokenCannotRedirectOutsideRegisteredClient() throws Exception {
        when(jwtDecoder.decode("older-signed-token")).thenReturn(dashboardIdToken());

        mockMvc.perform(get("/connect/logout")
                        .queryParam("id_token_hint", "older-signed-token")
                        .queryParam("post_logout_redirect_uri", "https://attacker.example/")
                        .session(new MockHttpSession(null, "session-id"))
                        .with(user("user-id")))
                .andExpect(status().isBadRequest());
    }

    private Jwt dashboardIdToken() {
        return Jwt.withTokenValue("older-signed-token")
                .header("alg", "RS256")
                .issuer("http://localhost:8080")
                .subject("user-id")
                .audience(List.of("arkil-dashboard"))
                .claim("azp", "arkil-dashboard")
                .claim("auth_time", Instant.now().minusSeconds(60).getEpochSecond())
                .claim("sid", "S98eFd9xbyf_brzBGapLiGOiIc1U6Hdy2CSIj0rqxcA")
                .issuedAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(600))
                .build();
    }
}
