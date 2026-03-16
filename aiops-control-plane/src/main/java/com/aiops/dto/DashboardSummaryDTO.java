package com.aiops.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummaryDTO {
    private long activeAlerts;
    private Map<String, Long> alertBySeverity;
    private long resolvedToday;
    private double averageMTTR;
    private String systemHealth;
    private Instant timestamp;
}
