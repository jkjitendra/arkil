package com.arkil.config;

import com.arkil.client.AuthModule;
import com.arkil.client.ClientAuthPolicy;
import com.arkil.client.ClientAuthPolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Bootstrap demo policy for the demo-client.
 */
@Component
@Order(20) // After DemoDataBootstrap
@RequiredArgsConstructor
@Slf4j
public class DemoPolicyBootstrap implements ApplicationRunner {

    private final ClientAuthPolicyRepository policyRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (policyRepository.count() > 0) {
            log.info("Client policies already exist, skipping bootstrap");
            return;
        }

        log.info("Creating demo client policy...");

        ClientAuthPolicy policy = ClientAuthPolicy.builder()
                .registeredClientInternalId("demo-client-internal")
                .clientId("demo-client")
                .enabledModules(Set.of(
                        AuthModule.EMAIL_PASSWORD,
                        AuthModule.OAUTH2_GOOGLE,
                        AuthModule.OAUTH2_GITHUB
                ))
                .mfaPolicy("{\"required\": false, \"factors\": [\"TOTP\"], \"stepUpTriggers\": []}")
                .updatedBy("system")
                .build();

        policyRepository.save(policy);
        log.info("Demo client policy created with modules: {}", policy.getEnabledModules());
    }
}
