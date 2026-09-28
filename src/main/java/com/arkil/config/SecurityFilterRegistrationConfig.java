package com.arkil.config;

import com.arkil.policy.ClientContextFilter;
import com.arkil.policy.PolicyEnforcementFilter;
import com.arkil.security.LoginRateLimitFilter;
import com.arkil.tenant.TenantContextFilter;
import jakarta.servlet.Filter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * These filters are placed explicitly in the Spring Security chain. Prevent
 * Spring Boot from also running them as ordinary servlet filters, which would
 * execute tenant resolution before bearer authentication.
 */
@Configuration
public class SecurityFilterRegistrationConfig {

    @Bean
    FilterRegistrationBean<ClientContextFilter> clientContextFilterRegistration(ClientContextFilter filter) {
        return disabled(filter);
    }

    @Bean
    FilterRegistrationBean<PolicyEnforcementFilter> policyEnforcementFilterRegistration(PolicyEnforcementFilter filter) {
        return disabled(filter);
    }

    @Bean
    FilterRegistrationBean<LoginRateLimitFilter> loginRateLimitFilterRegistration(LoginRateLimitFilter filter) {
        return disabled(filter);
    }

    @Bean
    FilterRegistrationBean<TenantContextFilter> tenantContextFilterRegistration(TenantContextFilter filter) {
        return disabled(filter);
    }

    private <T extends Filter> FilterRegistrationBean<T> disabled(T filter) {
        FilterRegistrationBean<T> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
