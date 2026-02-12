package com.arkil.config;

import com.arkil.policy.ClientContextFilter;
import com.arkil.policy.PolicyAwareAuthenticationProvider;
import com.arkil.policy.PolicyEnforcementFilter;
import com.arkil.security.LoginRateLimitFilter;
import com.arkil.security.ProjectCorsConfigurationSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * SecurityFilterChain #2: Application endpoints.
 * Handles login UI, config API, health checks, etc.
 */
@Configuration
public class SecurityConfig {

    private final LoginRateLimitFilter loginRateLimitFilter;
    private final ClientContextFilter clientContextFilter;
    private final PolicyEnforcementFilter policyEnforcementFilter;
    private final PolicyAwareAuthenticationProvider policyAwareAuthenticationProvider;
    private final ProjectCorsConfigurationSource corsConfigurationSource;

    public SecurityConfig(LoginRateLimitFilter loginRateLimitFilter,
                          ClientContextFilter clientContextFilter,
                          PolicyEnforcementFilter policyEnforcementFilter,
                          PolicyAwareAuthenticationProvider policyAwareAuthenticationProvider,
                          ProjectCorsConfigurationSource corsConfigurationSource) {
        this.loginRateLimitFilter = loginRateLimitFilter;
        this.clientContextFilter = clientContextFilter;
        this.policyEnforcementFilter = policyEnforcementFilter;
        this.policyAwareAuthenticationProvider = policyAwareAuthenticationProvider;
        this.corsConfigurationSource = corsConfigurationSource;
    }

    @Bean
    @Order(2)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                // Enable per-project CORS
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/login", "/error", "/actuator/health", "/h2-console/**").permitAll()
                        .requestMatchers("/api/v1/meta/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/api-docs/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/webjars/**", "/css/**", "/js/**", "/images/**").permitAll()
                        // Browser/DevTools specific paths - ignore to avoid OAuth flow interference
                        .requestMatchers("/.well-known/appspecific/**").permitAll()
                        // Auth API endpoints (public)
                        .requestMatchers("/api/v1/auth/register").permitAll()
                        .requestMatchers("/api/v1/auth/forgot-password", "/api/v1/auth/reset-password").permitAll()
                        .requestMatchers("/api/v1/auth/verify-email", "/api/v1/auth/magic-link").permitAll()
                        // Session creation endpoint (public)
                        .requestMatchers("/api/v1/sessions").permitAll()
                        // Admin APIs require scope
                        .requestMatchers("/api/v1/clients/**").hasAuthority("SCOPE_arkil:admin")
                        .requestMatchers("/api/v1/admin/**").hasAuthority("SCOPE_arkil:admin")
                        .requestMatchers("/oauth2/authorization/**").permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .permitAll()
                )
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/h2-console/**")
                        // Ignore CSRF for stateless API endpoints (they'll use tokens)
                        .ignoringRequestMatchers("/api/v1/**")
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
                // Filter chain order: ClientContext -> RateLimit -> PolicyEnforcement -> Authentication
                .addFilterBefore(clientContextFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(loginRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(policyEnforcementFilter, ClientContextFilter.class);

        return http.build();
    }
}
