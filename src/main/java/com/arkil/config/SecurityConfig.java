package com.arkil.config;

import com.arkil.policy.EmailNotVerifiedAuthenticationException;
import com.arkil.policy.InvalidTotpAuthenticationException;
import com.arkil.policy.ClientContextFilter;
import com.arkil.policy.PolicyAwareAuthenticationProvider;
import com.arkil.policy.PolicyEnforcementFilter;
import com.arkil.policy.TotpRequiredAuthenticationException;
import com.arkil.security.LoginRateLimitFilter;
import com.arkil.security.ProjectCorsConfigurationSource;
import com.arkil.tenant.TenantContextFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * SecurityFilterChain #2: Application endpoints.
 * Handles login UI, config API, health checks, social login, etc.
 */
@Configuration
public class SecurityConfig {

    private final LoginRateLimitFilter loginRateLimitFilter;
    private final ClientContextFilter clientContextFilter;
    private final PolicyEnforcementFilter policyEnforcementFilter;
    private final TenantContextFilter tenantContextFilter;
    private final PolicyAwareAuthenticationProvider policyAwareAuthenticationProvider;
    private final ProjectCorsConfigurationSource corsConfigurationSource;
    private final DynamicClientRegistrationRepository dynamicClientRegistrationRepository;
    private final OAuth2UserService<OAuth2UserRequest, OAuth2User> oauth2UserService;
    private final OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService;
    private final AuthenticationSuccessHandler oauth2SuccessHandler;
    private final AuthenticationFailureHandler oauth2FailureHandler;

    public SecurityConfig(LoginRateLimitFilter loginRateLimitFilter,
                          ClientContextFilter clientContextFilter,
                          PolicyEnforcementFilter policyEnforcementFilter,
                          TenantContextFilter tenantContextFilter,
                          PolicyAwareAuthenticationProvider policyAwareAuthenticationProvider,
                          ProjectCorsConfigurationSource corsConfigurationSource,
                          DynamicClientRegistrationRepository dynamicClientRegistrationRepository,
                          OAuth2UserService<OAuth2UserRequest, OAuth2User> oauth2UserService,
                          OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService,
                          AuthenticationSuccessHandler oauth2SuccessHandler,
                          AuthenticationFailureHandler oauth2FailureHandler) {
        this.loginRateLimitFilter = loginRateLimitFilter;
        this.clientContextFilter = clientContextFilter;
        this.policyEnforcementFilter = policyEnforcementFilter;
        this.tenantContextFilter = tenantContextFilter;
        this.policyAwareAuthenticationProvider = policyAwareAuthenticationProvider;
        this.corsConfigurationSource = corsConfigurationSource;
        this.dynamicClientRegistrationRepository = dynamicClientRegistrationRepository;
        this.oauth2UserService = oauth2UserService;
        this.oidcUserService = oidcUserService;
        this.oauth2SuccessHandler = oauth2SuccessHandler;
        this.oauth2FailureHandler = oauth2FailureHandler;
    }

    @Bean
    @Order(2)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                // Enable per-project CORS
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/login", "/signup", "/forgot-password", "/reset-password").permitAll()
                        .requestMatchers("/auth/magic-link", "/auth/magic-link/verify", "/auth/verify-email").permitAll()
                        .requestMatchers("/auth/social/**", "/auth/resend-verification").permitAll()
                        .requestMatchers("/webauthn/authenticate", "/webauthn/authenticate/options").permitAll()
                        .requestMatchers("/error", "/actuator/health", "/h2-console/**").permitAll()
                        .requestMatchers("/api/v1/meta/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/api-docs/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/webjars/**", "/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                        // Browser/DevTools specific paths - ignore to avoid OAuth flow interference
                        .requestMatchers("/.well-known/appspecific/**").permitAll()
                        // Auth API endpoints (public)
                        .requestMatchers("/api/v1/auth/register").permitAll()
                        .requestMatchers("/api/v1/auth/forgot-password", "/api/v1/auth/reset-password").permitAll()
                        .requestMatchers("/api/v1/auth/verify-email", "/api/v1/auth/magic-link").permitAll()
                        // Session endpoints (public — auth is via cookies/tokens, not Spring Security session)
                        .requestMatchers("/api/v1/sessions").permitAll()
                        .requestMatchers("/api/v1/sessions/refresh").permitAll()
                        .requestMatchers("/api/v1/sessions/current").permitAll()
                        // Public project config endpoint (for client-side SDK init)
                        .requestMatchers("/api/v1/public/**").permitAll()
                        // OAuth2 social login callback
                        .requestMatchers("/login/oauth2/code/**").permitAll()
                        // Admin APIs require scope
                        .requestMatchers("/api/v1/clients/**").hasAuthority("SCOPE_arkil:admin")
                        .requestMatchers("/api/v1/admin/**").hasAuthority("SCOPE_arkil:admin")
                        .requestMatchers("/oauth2/authorization/**").permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .failureHandler((request, response, exception) -> {
                            UriComponentsBuilder redirect = UriComponentsBuilder.fromPath("/login");
                            String clientId = request.getParameter("client_id");
                            if (clientId != null && !clientId.isBlank()) {
                                redirect.queryParam("client_id", clientId);
                            }

                            if (exception instanceof EmailNotVerifiedAuthenticationException unverified) {
                                redirect.queryParam("error", "unverified");
                                redirect.queryParam("email", unverified.getEmail());
                            } else if (exception instanceof TotpRequiredAuthenticationException mfaRequired) {
                                redirect.queryParam("error", "mfa_required");
                                redirect.queryParam("username", mfaRequired.getIdentifier());
                            } else if (exception instanceof InvalidTotpAuthenticationException invalidTotp) {
                                redirect.queryParam("error", "invalid_totp");
                                redirect.queryParam("username", invalidTotp.getIdentifier());
                            } else if (exception instanceof DisabledException) {
                                redirect.queryParam("error", "disabled");
                            } else {
                                redirect.queryParam("error", "credentials");
                            }

                            response.sendRedirect(redirect.build().encode().toUriString());
                        })
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutSuccessUrl("http://localhost:5173/")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                )
                // OAuth2 social login (Google, GitHub, etc.) with dynamic per-project credentials
                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/login")
                        .clientRegistrationRepository(dynamicClientRegistrationRepository)
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(oauth2UserService)
                                .oidcUserService(oidcUserService)
                        )
                        .successHandler(oauth2SuccessHandler)
                        .failureHandler(oauth2FailureHandler)
                )
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/h2-console/**")
                        // Ignore CSRF for stateless API endpoints (they'll use tokens)
                        .ignoringRequestMatchers("/api/v1/**")
                        .ignoringRequestMatchers("/webauthn/**")
                )
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin())
                        // Content Security Policy - DISABLED for dev (re-enable with proper config for production)
                        // .contentSecurityPolicy(csp -> csp
                        //         .policyDirectives("default-src 'self'; " +
                        //                 "script-src 'self' 'unsafe-inline'; " +
                        //                 "style-src 'self' 'unsafe-inline'; " +
                        //                 "img-src 'self' data: https:; " +
                        //                 "font-src 'self'; " +
                        //                 "connect-src 'self' http://localhost:5173 http://localhost:8080; " +
                        //                 "frame-ancestors 'self' http://localhost:5173; " +
                        //                 "form-action 'self' http://localhost:8080"))
                        // XSS Protection
                        .xssProtection(xss -> xss.headerValue(org.springframework.security.web.header.writers.XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
                        // HTTPS Strict Transport Security (production)
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000))
                        // Cache control for sensitive pages
                        .cacheControl(cache -> {})
                        // Permissions Policy
                        .permissionsPolicy(permissions -> permissions
                                .policy("camera=(), microphone=(), geolocation=(), payment=()"))
                )
                // Policy-aware authentication provider
                .authenticationProvider(policyAwareAuthenticationProvider)
                // Resource server: accept JWT Bearer tokens for API endpoints
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))
                // Filter chain order: ClientContext -> RateLimit -> PolicyEnforcement -> TenantContext -> Authentication
                .addFilterBefore(clientContextFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(loginRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(policyEnforcementFilter, ClientContextFilter.class)
                .addFilterAfter(tenantContextFilter, PolicyEnforcementFilter.class);

        return http.build();
    }
}
