package com.arkil.client.dto;

import com.arkil.client.AuthModule;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * DTO for updating client auth policy.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdatePolicyRequest {

    @NotNull(message = "enabledModules is required")
    private Set<AuthModule> enabledModules;

    private String moduleConfig;

    private String mfaPolicy;

    private String themeConfig;
}
