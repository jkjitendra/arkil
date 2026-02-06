package com.arkil.config;

import com.arkil.security.LoginRateLimitFilter;
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

    public SecurityConfig(LoginRateLimitFilter loginRateLimitFilter) {
        this.loginRateLimitFilter = loginRateLimitFilter;
    }

    @Bean
    @Order(2)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/login", "/error", "/actuator/health", "/h2-console/**").permitAll()
                        .requestMatchers("/api/v1/meta/**").permitAll()
                        .requestMatchers("/api/v1/clients/**").hasAuthority("SCOPE_arkil:admin")
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .permitAll()
                )
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/h2-console/**")
                )
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin())
                )
                .addFilterBefore(loginRateLimitFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
