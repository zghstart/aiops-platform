package com.aiops.domain.model;

import com.aiops.repository.entity.Alert;
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
public class AlertModel {

    private String id;
    private String tenantId;
    private String incidentId;
    private String source;
    private String severity;
    private String title;
    private String description;
    private String serviceId;
    private Map<String, String> labels;
    private String status;
    private String aiStatus;
    private String silencedBy;
    private Instant startsAt;
    private Instant createdAt;

    public static AlertModel fromEntity(Alert alert) {
        if (alert == null) return null;
        return AlertModel.builder()
                .id(alert.getAlertId())
                .tenantId(alert.getTenantId())
                .incidentId(alert.getIncidentId())
                .source(alert.getSource())
                .severity(alert.getSeverity())
                .title(alert.getTitle())
                .description(alert.getDescription())
                .serviceId(alert.getServiceId())
                .labels(alert.getLabels())
                .status(alert.getStatus())
                .aiStatus(alert.getAiStatus())
                .silencedBy(alert.getSilencedBy())
                .startsAt(alert.getStartsAt())
                .createdAt(alert.getCreatedAt())
                .build();
    }
}
