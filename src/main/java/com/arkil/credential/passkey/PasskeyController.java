package com.arkil.credential.passkey;

import com.arkil.client.AuthModule;
import com.arkil.policy.ClientContextHolder;
import com.arkil.user.ArkilUser;
import com.arkil.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * WebAuthn/Passkey REST API for hosted authentication.
 */
@RestController
@RequestMapping("/webauthn")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "WebAuthn", description = "Passkey/WebAuthn registration and authentication")
public class PasskeyController {

    private final ClientContextHolder clientContextHolder;
    private final PasskeyService passkeyService;
    private final UserRepository userRepository;

    @PostMapping("/authenticate/options")
    @Operation(summary = "Get authentication options", description = "Get WebAuthn authentication challenge")
    public ResponseEntity<Map<String, Object>> getAuthenticationOptions(
            @RequestParam(required = false) UUID userId) {

        checkModuleEnabled();
        return ResponseEntity.ok(passkeyService.issueAuthenticationOptions(userId).options());
    }

    @PostMapping("/authenticate")
    @Operation(summary = "Complete authentication", description = "Verify WebAuthn assertion")
    public ResponseEntity<Map<String, Object>> completeAuthentication(
            @RequestBody Map<String, Object> assertion,
            HttpServletRequest request) {

        checkModuleEnabled();

        try {
            PasskeyService.AuthenticationResult result = passkeyService.completeAuthentication(assertion);
            ArkilUser user = result.user();
            if (!Boolean.TRUE.equals(user.getEnabled())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is disabled.");
            }

            createAuthenticatedSession(user, request);

            String redirectUrl = resolveRedirectUrl(request);
            log.info("Passkey login successful for user {}", user.getEmail());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Passkey authentication succeeded.",
                    "redirectUrl", redirectUrl,
                    "user", Map.of(
                            "id", user.getId().toString(),
                            "email", user.getEmail(),
                            "displayName", user.getDisplayName() != null ? user.getDisplayName() : user.getUsername()
                    )
            ));
        } catch (PasskeyValidationException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private void checkModuleEnabled() {
        if (clientContextHolder.hasContext() &&
                !clientContextHolder.getContext().isModuleEnabled(AuthModule.PASSKEY)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Passkey authentication is not enabled for this client");
        }
    }

    private void createAuthenticatedSession(ArkilUser user, HttpServletRequest request) {
        Set<SimpleGrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName()))
                .collect(Collectors.toSet());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                user.getId().toString(),
                null,
                authorities
        ));
        SecurityContextHolder.setContext(context);

        HttpSession session = request.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
    }

    private String resolveRedirectUrl(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object savedRequest = session.getAttribute("SPRING_SECURITY_SAVED_REQUEST");
            if (savedRequest instanceof SavedRequest requestToResume) {
                return requestToResume.getRedirectUrl();
            }
            Object socialReturnTo = session.getAttribute(com.arkil.auth.AuthSessionAttributes.SOCIAL_LOGIN_RETURN_TO);
            if (socialReturnTo instanceof String returnTo && !returnTo.isBlank()) {
                session.removeAttribute(com.arkil.auth.AuthSessionAttributes.SOCIAL_LOGIN_RETURN_TO);
                return returnTo;
            }
        }

        return "/";
    }
}
