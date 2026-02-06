package com.arkil.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Enables async processing for audit logging.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
