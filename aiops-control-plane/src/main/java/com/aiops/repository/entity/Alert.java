package com.aiops.repository.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "alerts", indexes = {
    @Index(name = "idx_alert_tenant", columnList = "tenant_id"),
    @Index(name = "idx_alert_incident", columnList = "incident_id"),
    @Index(name = "idx_alert_status", columnList = "status"),
    @Index(name = "idx_alert_time", columnList = "starts_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Alert {

    @Id
    @Column(name = "alert_id", length = 64)
    private String alertId;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "incident_id", length = 64)
    private String incidentId;

    @Column(name = "source", nullable = false, length = 64)
    private String source;

    @Column(name = "severity", nullable = false, length = 16)
    private String severity;

    @Column(name = "title", nullable = false, length = 512)
    private String title;

    @Column(name = "description", length = 4000)
    private String description;

    @Column(name = "service_id", nullable = false, length = 128)
    private String serviceId;

    @Column(name = "instance", length = 64)
    private String instance;

    @ElementCollection
    @CollectionTable(name = "alert_labels", joinColumns = @JoinColumn(name = "alert_id"))
    @MapKeyColumn(name = "label_key")
    @Column(name = "label_value")
    private Map<String, String> labels;

    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private String status = "active";

    @Column(name = "ai_status", length = 32)
    private String aiStatus;

    @Column(name = "silenced_by", length = 64)
    private String silencedBy;

    @Column(name = "silence_reason", length = 256)
    private String silenceReason;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "payload", columnDefinition = "LONGTEXT")
    private String payload;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
