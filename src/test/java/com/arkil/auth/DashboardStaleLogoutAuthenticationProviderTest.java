package com.arkil.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcLogoutAuthenticationToken;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardStaleLogoutAuthenticationProviderTest {

    private final JwtDecoder decoder = mock(JwtDecoder.class);
    private final RegisteredClientRepository clients = mock(RegisteredClientRepository.class);
    private final OAuth2AuthorizationService authorizations = mock(OAuth2AuthorizationService.class);
    private final DashboardStaleLogoutAuthenticationProvider provider =
            new DashboardStaleLogoutAuthenticationProvider(decoder, clients, authorizations);

    @BeforeEach
    void setUp() {
        when(clients.findByClientId("arkil-dashboard")).thenReturn(RegisteredClient.withId("dashboard-internal")
                .clientId("arkil-dashboard")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost:5173/callback")
                .postLogoutRedirectUri("http://localhost:5173/")
                .scope("openid")
                .build());
        when(decoder.decode("older-signed-token")).thenReturn(Jwt.withTokenValue("older-signed-token")
                .header("alg", "RS256")
                .issuer("http://localhost:8080")
                .subject("user-id")
                .audience(List.of("arkil-dashboard"))
                .claim("azp", "arkil-dashboard")
                .claim("auth_time", Instant.now().minusSeconds(60).getEpochSecond())
                .claim("sid", "S98eFd9xbyf_brzBGapLiGOiIc1U6Hdy2CSIj0rqxcA")
                .issuedAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(600))
                .build());
    }

    @Test
    void acceptsEarlierSignedDashboardIdTokenForRegisteredRedirect() {
        var authenticated = (OidcLogoutAuthenticationToken) provider.authenticate(request("http://localhost:5173/", "user-id"));

        assertThat(authenticated).isNotNull();
        assertThat(authenticated.isAuthenticated()).isTrue();
        assertThat(authenticated.getPostLogoutRedirectUri()).isEqualTo("http://localhost:5173/");
    }

    @Test
    void rejectsUnregisteredRedirectAndDifferentSession() {
        assertThat(provider.authenticate(request("https://attacker.example/", "user-id"))).isNull();
        assertThat(provider.authenticate(request("http://localhost:5173/", "another-user"))).isNull();
        var principal = UsernamePasswordAuthenticationToken.authenticated("user-id", null, List.of());
        assertThat(provider.authenticate(new OidcLogoutAuthenticationToken("older-signed-token", principal,
                "another-session", null, "http://localhost:5173/", null))).isNull();
    }

    private OidcLogoutAuthenticationToken request(String redirect, String principalName) {
        var principal = UsernamePasswordAuthenticationToken.authenticated(principalName, null, List.of());
        return new OidcLogoutAuthenticationToken("older-signed-token", principal, "session-id",
                null, redirect, null);
    }
}
