package com.arkil.client.dto;

import com.arkil.client.AuthModule;
import com.arkil.client.ClientAuthPolicy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Set;

/**
 * DTO for client auth policy response.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClientPolicyDto {
    private String clientId;
    private Set<AuthModule> enabledModules;
    private String moduleConfig;
    private String mfaPolicy;
    private String themeConfig;
    private Long version;
    private Instant updatedAt;
    private String updatedBy;

    public static ClientPolicyDto from(ClientAuthPolicy policy) {
        return ClientPolicyDto.builder()
                .clientId(policy.getClientId())
                .enabledModules(policy.getEnabledModules())
                .moduleConfig(policy.getModuleConfig())
                .mfaPolicy(policy.getMfaPolicy())
                .themeConfig(policy.getThemeConfig())
                .version(policy.getVersion())
                .updatedAt(policy.getUpdatedAt())
                .updatedBy(policy.getUpdatedBy())
                .build();
    }
}
