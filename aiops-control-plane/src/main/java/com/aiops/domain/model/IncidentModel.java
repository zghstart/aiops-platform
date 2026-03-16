package com.aiops.domain.model;

import com.aiops.repository.entity.Incident;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentModel {

    private String id;
    private String tenantId;
    private String clusterKey;
    private String serviceId;
    private String status;
    private String severity;
    private String title;
    private String description;
    private String rootCause;
    private Double confidence;
    private List<String> recommendations;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant resolvedAt;

    public static IncidentModel fromEntity(Incident incident) {
        if (incident == null) return null;
        return IncidentModel.builder()
                .id(incident.getId())
                .tenantId(incident.getTenantId())
                .clusterKey(incident.getClusterKey())
                .serviceId(incident.getServiceId())
                .status(incident.getStatus())
                .severity(incident.getSeverity())
                .title(incident.getTitle())
                .description(incident.getDescription())
                .rootCause(incident.getRootCause())
                .confidence(incident.getConfidence())
                .createdAt(incident.getCreatedAt())
                .updatedAt(incident.getUpdatedAt())
                .resolvedAt(incident.getResolvedAt())
                .build();
    }
}
