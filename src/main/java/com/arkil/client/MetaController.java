package com.arkil.client;

import com.arkil.client.dto.AuthModuleDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * Meta API for discovering available authentication modules.
 * Public endpoint (no authentication required).
 */
@RestController
@RequestMapping("/api/v1/meta")
@Tag(name = "Meta", description = "Authentication module discovery")
public class MetaController {

    @GetMapping("/auth-modules")
    @Operation(summary = "List available auth modules", description = "Returns all authentication modules that can be enabled for clients")
    public List<AuthModuleDto> listAuthModules() {
        return Arrays.stream(AuthModule.values())
                .map(AuthModuleDto::from)
                .toList();
    }
}
