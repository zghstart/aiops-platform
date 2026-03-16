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
@Table(name = "tenants")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tenant {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "code", nullable = false, unique = true, length = 32)
    private String code;

    @Column(name = "status", nullable = false, length = 16)
    @Builder.Default
    private String status = "active";

    @Column(name = "api_key", length = 128)
    private String apiKey;

    @Column(name = "ai_quota_daily")
    @Builder.Default
    private Integer aiQuotaDaily = 1000;

    @Column(name = "ai_quota_used")
    @Builder.Default
    private Integer aiQuotaUsed = 0;

    @ElementCollection
    @CollectionTable(name = "tenant_admins", joinColumns = @JoinColumn(name = "tenant_id"))
    @Column(name = "admin_email")
    private Set<String> adminEmails;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
