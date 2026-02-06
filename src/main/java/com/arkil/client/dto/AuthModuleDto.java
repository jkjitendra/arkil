package com.arkil.client.dto;

import com.arkil.client.AuthModule;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for returning auth module metadata.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthModuleDto {
    private String name;
    private String displayName;
    private String description;

    public static AuthModuleDto from(AuthModule module) {
        return AuthModuleDto.builder()
                .name(module.name())
                .displayName(module.getDisplayName())
                .description(module.getDescription())
                .build();
    }
}
