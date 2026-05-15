package com.profitsaathi.notification.email;

import lombok.Builder;

import java.util.List;
import java.util.Map;

/**
 * In-process equivalent of the old Kafka {@code EmailEvent}. Carry only data
 * (no Spring deps) so it stays trivially testable and serialisable.
 */
@Builder
public record EmailRequest(
        List<String> to,
        List<String> cc,
        String subject,
        String templateName,
        Map<String, Object> variables,
        String attachmentPath
) {}
