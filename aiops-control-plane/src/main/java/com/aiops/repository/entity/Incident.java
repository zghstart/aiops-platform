package com.aiops.repository.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "incidents", indexes = {
    @Index(name = "idx_incident_tenant", columnList = "tenant_id"),
    @Index(name = "idx_incident_status", columnList = "status"),
    @Index(name = "idx_incident_service", columnList = "service_id"),
    @Index(name = "idx_incident_cluster", columnList = "cluster_key")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Incident {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "cluster_key", length = 128)
    private String clusterKey;

    @Column(name = "service_id", nullable = false, length = 128)
    private String serviceId;

    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private String status = "analyzing";

    @Column(name = "severity", length = 16)
    private String severity;

    @Column(name = "title", length = 512)
    private String title;

    @Column(name = "description", length = 4000)
    private String description;

    @Column(name = "root_cause", length = 4000)
    private String rootCause;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "recommendations", columnDefinition = "LONGTEXT")
    private String recommendations;

    @Column(name = "assigned_to", length = 64)
    private String assignedTo;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private Instant updatedAt;
}
