package com.aiops.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertWebhookRequest {

    private String alertId;

    @NotBlank(message = "source is required")
    private String source;

    @NotBlank(message = "severity is required")
    private String severity;

    @NotBlank(message = "title is required")
    private String title;

    private String description;

    @NotBlank(message = "serviceId is required")
    private String serviceId;

    private Map<String, String> labels;

    @NotNull(message = "startsAt is required")
    private Instant startsAt;

    private Map<String, Object> payload;
}
