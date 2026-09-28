package com.arkil.auth;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcLogoutAuthenticationToken;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * The authorization service retains the latest ID token for an authorization.
 * A browser can still hold an earlier signed ID token after token renewal.
 * Accept that earlier token for dashboard logout after validating its signature,
 * issuer, expiry, audience, current principal, and registered redirect URI.
 */
public final class DashboardStaleLogoutAuthenticationProvider implements AuthenticationProvider {

    private static final String DASHBOARD_CLIENT_ID = "arkil-dashboard";
    private static final OAuth2TokenType ID_TOKEN_TYPE = new OAuth2TokenType("id_token");

    private final JwtDecoder jwtDecoder;
    private final RegisteredClientRepository registeredClientRepository;
    private final OAuth2AuthorizationService authorizationService;

    public DashboardStaleLogoutAuthenticationProvider(
            JwtDecoder jwtDecoder,
            RegisteredClientRepository registeredClientRepository,
            OAuth2AuthorizationService authorizationService) {
        this.jwtDecoder = jwtDecoder;
        this.registeredClientRepository = registeredClientRepository;
        this.authorizationService = authorizationService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        OidcLogoutAuthenticationToken request = (OidcLogoutAuthenticationToken) authentication;
        String hint = request.getIdTokenHint();
        if (hint == null || hint.isBlank()
                || authorizationService.findByToken(hint, ID_TOKEN_TYPE) != null) {
            return null; // Let Spring Authorization Server handle current tokens.
        }

        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(hint);
        } catch (JwtException invalidToken) {
            return null;
        }
        if (jwt.getAudience() == null || !jwt.getAudience().contains(DASHBOARD_CLIENT_ID)
                || !StringUtils.hasText(jwt.getSubject())
                || jwt.getIssuedAt() == null || jwt.getExpiresAt() == null
                || jwt.getClaim("auth_time") == null
                || !StringUtils.hasText(jwt.getClaimAsString("sid"))
                || (request.getClientId() != null
                        && !DASHBOARD_CLIENT_ID.equals(request.getClientId()))
                || (jwt.getClaimAsString("azp") != null
                        && !DASHBOARD_CLIENT_ID.equals(jwt.getClaimAsString("azp")))) {
            return null;
        }

        RegisteredClient client = registeredClientRepository.findByClientId(DASHBOARD_CLIENT_ID);
        if (client == null || (request.getPostLogoutRedirectUri() != null
                && !client.getPostLogoutRedirectUris().contains(request.getPostLogoutRedirectUri()))) {
            return null;
        }

        Authentication principal = (Authentication) request.getPrincipal();
        if (request.isPrincipalAuthenticated()) {
            if (!jwt.getSubject().equals(principal.getName())) {
                return null;
            }
            if (StringUtils.hasText(request.getSessionId())
                    && !jwt.getClaimAsString("sid").equals(hashSessionId(request.getSessionId()))) {
                return null;
            }
        }

        OidcIdToken idToken = new OidcIdToken(jwt.getTokenValue(), jwt.getIssuedAt(),
                jwt.getExpiresAt(), jwt.getClaims());
        return new OidcLogoutAuthenticationToken(idToken, principal, request.getSessionId(),
                request.getClientId(), request.getPostLogoutRedirectUri(), request.getState());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return OidcLogoutAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private String hashSessionId(String sessionId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(sessionId.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
