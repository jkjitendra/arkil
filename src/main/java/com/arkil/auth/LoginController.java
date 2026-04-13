package com.arkil.auth;

import com.arkil.client.AuthModule;
import com.arkil.credential.password.PasswordCredential;
import com.arkil.credential.password.PasswordCredentialRepository;
import com.arkil.email.EmailToken;
import com.arkil.email.EmailTokenService;
import com.arkil.policy.ClientContext;
import com.arkil.policy.ClientContextHolder;
import com.arkil.tenant.TenantContext;
import com.arkil.user.ArkilUser;
import com.arkil.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Controller for hosted authentication UI pages.
 * Renders Thymeleaf templates for login, signup, password reset,
 * magic link, and email verification flows.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class LoginController {

    private final ClientContextHolder clientContextHolder;
    private final EndUserRegistrationService endUserRegistrationService;
    private final EmailTokenService emailTokenService;
    private final UserRepository userRepository;
    private final PasswordCredentialRepository passwordCredentialRepository;
    private final PasswordEncoder passwordEncoder;

    // ─────────────────────────────────────────────────────────────────
    // Login
    // ─────────────────────────────────────────────────────────────────

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    // ─────────────────────────────────────────────────────────────────
    // Sign Up
    // ─────────────────────────────────────────────────────────────────

    @GetMapping("/signup")
    public String showSignup(Model model) {
        if (!isModuleEnabled(AuthModule.EMAIL_PASSWORD)) {
            model.addAttribute("error", "Registration is not available for this application.");
            return "signup";
        }
        return "signup";
    }

    @PostMapping("/signup")
    public String processSignup(
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam(required = false) String confirmPassword,
            @RequestParam(required = false) String client_id,
            Model model) {

        // Preserve client_id for template links
        if (client_id != null) {
            model.addAttribute("clientId", client_id);
        }

        // Validation
        if (password.length() < 8) {
            model.addAttribute("error", "Password must be at least 8 characters.");
            model.addAttribute("email", email);
            return "signup";
        }
        if (confirmPassword != null && !password.equals(confirmPassword)) {
            model.addAttribute("error", "Passwords do not match.");
            model.addAttribute("email", email);
            return "signup";
        }

        // Resolve client context
        String resolvedClientId = resolveClientId(client_id);
        if (resolvedClientId == null) {
            model.addAttribute("error", "Invalid application context. Cannot register.");
            model.addAttribute("email", email);
            return "signup";
        }

        try {
            endUserRegistrationService.registerEndUser(email, password, resolvedClientId);
            model.addAttribute("success", true);
            model.addAttribute("email", email);
            return "signup";
        } catch (EndUserRegistrationService.EndUserRegistrationException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("email", email);
            return "signup";
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Forgot Password
    // ─────────────────────────────────────────────────────────────────

    @GetMapping("/forgot-password")
    public String showForgotPassword() {
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String processForgotPassword(
            @RequestParam String email,
            Model model) {

        emailTokenService.sendPasswordResetEmail(email);

        // Always show success to prevent email enumeration
        model.addAttribute("sent", true);
        model.addAttribute("email", email);
        return "forgot-password";
    }

    // ─────────────────────────────────────────────────────────────────
    // Reset Password
    // ─────────────────────────────────────────────────────────────────

    @GetMapping("/reset-password")
    public String showResetPassword(@RequestParam String token, Model model) {
        model.addAttribute("token", token);
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String processResetPassword(
            @RequestParam String token,
            @RequestParam String newPassword,
            @RequestParam(required = false) String confirmPassword,
            Model model) {

        model.addAttribute("token", token);

        if (newPassword.length() < 8) {
            model.addAttribute("error", "Password must be at least 8 characters.");
            return "reset-password";
        }
        if (confirmPassword != null && !newPassword.equals(confirmPassword)) {
            model.addAttribute("error", "Passwords do not match.");
            return "reset-password";
        }

        Optional<EmailToken> tokenOpt = emailTokenService.verifyToken(token, EmailToken.TokenType.PASSWORD_RESET);
        if (tokenOpt.isEmpty()) {
            model.addAttribute("error", "Invalid or expired reset link. Please request a new one.");
            return "reset-password";
        }

        EmailToken emailToken = tokenOpt.get();
        ArkilUser user = userRepository.findById(emailToken.getUserId()).orElse(null);
        if (user == null) {
            model.addAttribute("error", "User not found.");
            return "reset-password";
        }

        // Update or create password credential
        Optional<PasswordCredential> credOpt = passwordCredentialRepository.findByUser_Id(user.getId());
        if (credOpt.isPresent()) {
            PasswordCredential cred = credOpt.get();
            cred.setPasswordHash(passwordEncoder.encode(newPassword));
            passwordCredentialRepository.save(cred);
        } else {
            passwordCredentialRepository.save(PasswordCredential.builder()
                    .user(user)
                    .passwordHash(passwordEncoder.encode(newPassword))
                    .algorithm("bcrypt")
                    .build());
        }

        log.info("Password reset completed for user: {}", user.getEmail());
        model.addAttribute("success", true);
        return "reset-password";
    }

    // ─────────────────────────────────────────────────────────────────
    // Magic Link
    // ─────────────────────────────────────────────────────────────────

    @GetMapping("/auth/magic-link")
    public String showMagicLinkForm() {
        return "magic-link";
    }

    @PostMapping("/auth/magic-link")
    public String sendMagicLink(
            @RequestParam String email,
            Model model) {

        emailTokenService.sendMagicLink(email);

        model.addAttribute("sent", true);
        model.addAttribute("email", email);
        return "magic-link";
    }

    @GetMapping("/auth/magic-link/verify")
    public String verifyMagicLink(
            @RequestParam String token,
            HttpServletRequest request,
            Model model) {

        Optional<EmailToken> tokenOpt = emailTokenService.verifyToken(token, EmailToken.TokenType.MAGIC_LINK);
        if (tokenOpt.isEmpty()) {
            model.addAttribute("error", "Invalid or expired magic link. Please request a new one.");
            return "magic-link";
        }

        EmailToken emailToken = tokenOpt.get();
        ArkilUser user = userRepository.findById(emailToken.getUserId()).orElse(null);
        if (user == null || !user.getEnabled()) {
            model.addAttribute("error", "Account not found or disabled.");
            return "magic-link";
        }

        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            model.addAttribute("error", "Verify your email before signing in with a magic link.");
            model.addAttribute("email", user.getEmail());
            model.addAttribute("verificationRequired", true);
            return "magic-link";
        }

        // Create authentication session
        Set<SimpleGrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName()))
                .collect(Collectors.toSet());

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(user.getId().toString(), null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);

        // Save to session
        HttpSession session = request.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                SecurityContextHolder.getContext());

        // Update last login
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        log.info("Magic link login successful for user: {}", user.getEmail());
        model.addAttribute("success", true);
        return "magic-link-success";
    }

    // ─────────────────────────────────────────────────────────────────
    // Email Verification
    // ─────────────────────────────────────────────────────────────────

    @GetMapping("/auth/verify-email")
    public String verifyEmail(@RequestParam String token, Model model) {
        boolean verified = emailTokenService.verifyEmail(token);

        if (verified) {
            model.addAttribute("success", true);
        } else {
            model.addAttribute("error", "Invalid or expired verification link.");
        }
        return "verify-email";
    }

    @GetMapping("/auth/social/{provider}")
    public String startSocialLogin(
            @PathVariable String provider,
            @RequestParam("return_to") String returnTo,
            HttpServletRequest request) {

        if (!Set.of("google", "github", "apple", "linkedin").contains(provider.toLowerCase())) {
            return "redirect:/login?error=oauth2";
        }

        String normalizedReturnTo = normalizeReturnTo(returnTo, request);
        if (normalizedReturnTo == null) {
            return "redirect:/login?error=oauth2";
        }

        request.getSession(true).setAttribute(AuthSessionAttributes.SOCIAL_LOGIN_RETURN_TO, normalizedReturnTo);
        return "redirect:/oauth2/authorization/" + provider.toLowerCase();
    }

    @PostMapping("/auth/resend-verification")
    public String resendVerification(
            @RequestParam String email,
            @RequestParam(required = false) String client_id,
            @RequestParam(defaultValue = "/login") String redirectTo) {

        if (StringUtils.hasText(email)) {
            resolveUserByEmail(email.trim()).ifPresent(user -> {
                if (!Boolean.TRUE.equals(user.getEmailVerified())) {
                    emailTokenService.sendVerificationEmail(user.getId());
                }
            });
        }

        String target = isAllowedHostedRedirect(redirectTo) ? redirectTo : "/login";
        UriComponentsBuilder redirect = UriComponentsBuilder.fromPath(target)
                .queryParam("verification", "sent");

        if (StringUtils.hasText(email)) {
            redirect.queryParam("email", email.trim());
        }
        if (StringUtils.hasText(client_id)) {
            redirect.queryParam("client_id", client_id);
        }

        return "redirect:" + redirect.build().encode().toUriString();
    }

    // ─────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────

    private boolean isModuleEnabled(AuthModule module) {
        if (!clientContextHolder.hasContext()) {
            return true; // Allow when no client context (direct access)
        }
        ClientContext ctx = clientContextHolder.getContext();
        return !ctx.isResolved() || ctx.isModuleEnabled(module);
    }

    private String resolveClientId(String paramClientId) {
        if (paramClientId != null && !paramClientId.isBlank()) {
            return paramClientId;
        }
        if (clientContextHolder.hasContext() && clientContextHolder.getContext().isResolved()) {
            return clientContextHolder.getContext().getClientId();
        }
        return null;
    }

    private Optional<ArkilUser> resolveUserByEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return Optional.empty();
        }

        if (TenantContext.isSet()) {
            return userRepository.findByTenantIdAndEmail(TenantContext.getTenantId(), email);
        }

        return userRepository.findByEmail(email);
    }

    private boolean isAllowedHostedRedirect(String redirectTo) {
        return "/login".equals(redirectTo) || "/auth/magic-link".equals(redirectTo);
    }

    private String normalizeReturnTo(String returnTo, HttpServletRequest request) {
        if (!StringUtils.hasText(returnTo)) {
            return null;
        }

        try {
            URI uri = URI.create(returnTo);
            String path = uri.getPath();
            if (!"/oauth2/authorize".equals(path)) {
                return null;
            }

            if (uri.isAbsolute()) {
                boolean sameOrigin = request.getScheme().equalsIgnoreCase(uri.getScheme())
                        && request.getServerName().equalsIgnoreCase(uri.getHost())
                        && normalizePort(request.getScheme(), request.getServerPort())
                        == normalizePort(uri.getScheme(), uri.getPort());
                return sameOrigin ? uri.toString() : null;
            }

            return ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path(path)
                    .query(uri.getQuery())
                    .build()
                    .toUriString();
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private int normalizePort(String scheme, int port) {
        if (port > 0) {
            return port;
        }
        return "https".equalsIgnoreCase(scheme) ? 443 : 80;
    }
}
