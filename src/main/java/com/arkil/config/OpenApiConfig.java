package com.arkil.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3.0 documentation configuration.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Arkil Auth Server API",
                version = "1.0.0",
                description = """
                        Arkil is a dynamic, client-configurable authentication server built with
                        Java 25 and Spring Boot 4.x.
                        
                        ## Features
                        - **Client-Configurable Auth Modules**: Each OAuth2 client can enable/disable auth methods
                        - **Deny-by-Default Security**: All modules disabled until explicitly enabled
                        - **Layered Enforcement**: UI → Endpoint → Provider guards
                        - **Multi-Factor Authentication**: TOTP and Passkey support
                        - **Comprehensive Audit Logging**: All security events tracked
                        
                        ## Authentication
                        - Public endpoints: `/api/v1/meta/*`, `/login`, `/actuator/health`
                        - Admin endpoints: Require `arkil:admin` scope via OAuth2 Bearer token
                        """,
                contact = @Contact(
                        name = "Arkil Team",
                        email = "admin@arkil.io"
                ),
                license = @License(
                        name = "Apache 2.0",
                        url = "https://www.apache.org/licenses/LICENSE-2.0"
                )
        )
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "JWT token with arkil:admin scope for admin operations"
)
public class OpenApiConfig {
}
