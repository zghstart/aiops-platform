package com.aiops.repository.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Set;

@Entity
@Table(name = "service_entities", indexes = {
    @Index(name = "idx_service_tenant", columnList = "tenant_id"),
    @Index(name = "idx_service_name", columnList = "name")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "service_id", nullable = false, length = 128, unique = true)
    private String serviceId;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "display_name", length = 256)
    private String displayName;

    @Column(name = "type", length = 64)
    private String type;

    @Column(name = "health", length = 32)
    @Builder.Default
    private String health = "unknown";

    @Column(name = "owner", length = 64)
    private String owner;

    @Column(name = "status", length = 16)
    @Builder.Default
    private String status = "active";

    @ElementCollection
    @CollectionTable(name = "service_tags", joinColumns = @JoinColumn(name = "service_id"))
    @Column(name = "tag")
    private Set<String> tags;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
