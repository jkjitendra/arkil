package com.arkil.email;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Email message representation.
 */
@Data
@Builder
public class EmailMessage {
    private String to;
    private String subject;
    private String templateId;
    private Map<String, Object> templateData;
    private String plainText;
    private String htmlContent;
}
