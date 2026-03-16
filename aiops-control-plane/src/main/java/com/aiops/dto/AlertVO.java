package com.aiops.dto;

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
public class AlertVO {
    private String alertId;
    private String incidentId;
    private String source;
    private String severity;
    private String title;
    private String serviceId;
    private String status;
    private String aiStatus;
    private Instant startsAt;
    private Instant createdAt;
}
