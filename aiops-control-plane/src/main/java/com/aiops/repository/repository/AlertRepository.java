package com.aiops.repository.repository;

import com.aiops.repository.entity.Alert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface AlertRepository extends JpaRepository<Alert, String> {

    Optional<Alert> findByAlertId(String alertId);

    @Query("SELECT a FROM Alert a WHERE a.tenantId = :tenantId AND a.status = :status ORDER BY a.startsAt DESC")
    List<Alert> findByTenantIdAndStatus(@Param("tenantId") String tenantId, @Param("status") String status);

    @Query("SELECT a FROM Alert a WHERE a.tenantId = :tenantId " +
           "AND (:status IS NULL OR a.status = :status) " +
           "AND (:severity IS NULL OR a.severity = :severity) " +
           "AND (:serviceId IS NULL OR a.serviceId = :serviceId) " +
           "ORDER BY a.startsAt DESC")
    Page<Alert> findAlerts(@Param("tenantId") String tenantId,
                          @Param("status") String status,
                          @Param("severity") String severity,
                          @Param("serviceId") String serviceId,
                          Pageable pageable);

    @Query("SELECT a FROM Alert a WHERE a.tenantId = :tenantId AND a.startsAt >= :since ORDER BY a.startsAt DESC")
    List<Alert> findRecentByTenant(@Param("tenantId") String tenantId, @Param("since") Instant since);

    @Modifying
    @Query("UPDATE Alert a SET a.aiStatus = :status WHERE a.alertId = :alertId")
    int updateAiStatus(@Param("alertId") String alertId, @Param("status") String status);

    @Modifying
    @Query("UPDATE Alert a SET a.status = 'silenced', a.silencedBy = :ruleId, a.silenceReason = :reason WHERE a.alertId = :alertId")
    int silenceAlert(@Param("alertId") String alertId, @Param("ruleId") String ruleId, @Param("reason") String reason);

    @Query("SELECT COUNT(a) FROM Alert a WHERE a.tenantId = :tenantId AND a.status = 'active'")
    long countActiveByTenant(@Param("tenantId") String tenantId);

    @Query("SELECT a FROM Alert a WHERE a.tenantId = :tenantId AND a.serviceId = :serviceId " +
           "AND a.startsAt BETWEEN :start AND :end ORDER BY a.startsAt DESC")
    List<Alert> findByServiceAndTimeRange(@Param("tenantId") String tenantId,
                                          @Param("serviceId") String serviceId,
                                          @Param("start") Instant start,
                                          @Param("end") Instant end);
}
