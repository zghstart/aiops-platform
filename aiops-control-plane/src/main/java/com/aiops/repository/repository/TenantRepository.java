package com.aiops.repository.repository;

import com.aiops.repository.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, String> {

    Optional<Tenant> findByCode(String code);

    Optional<Tenant> findByApiKey(String apiKey);

    @Modifying
    @Query("UPDATE Tenant t SET t.aiQuotaUsed = t.aiQuotaUsed + 1 WHERE t.id = :tenantId")
    int incrementQuotaUsed(@Param("tenantId") String tenantId);

    @Query("SELECT (t.aiQuotaUsed < t.aiQuotaDaily) FROM Tenant t WHERE t.id = :tenantId")
    boolean hasQuotaRemaining(@Param("tenantId") String tenantId);
}
