package com.aiops.repository.repository;

import com.aiops.repository.entity.Incident;
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
public interface IncidentRepository extends JpaRepository<Incident, String> {

    Optional<Incident> findByIdAndTenantId(String id, String tenantId);

    @Query("SELECT i FROM Incident i WHERE i.tenantId = :tenantId AND i.status IN ('analyzing', 'open') AND i.createdAt >= :since ORDER BY i.createdAt DESC")
    List<Incident> findActiveByTenant(@Param("tenantId") String tenantId, @Param("since") Instant since);

    @Query("SELECT i FROM Incident i WHERE i.tenantId = :tenantId " +
           "AND (:status IS NULL OR i.status = :status) " +
           "AND (:serviceId IS NULL OR i.serviceId = :serviceId) " +
           "ORDER BY i.createdAt DESC")
    Page<Incident> findIncidents(@Param("tenantId") String tenantId,
                                @Param("status") String status,
                                @Param("serviceId") String serviceId,
                                Pageable pageable);

    @Query("SELECT i FROM Incident i WHERE i.tenantId = :tenantId " +
           "AND i.clusterKey = :clusterKey " +
           "AND i.status IN ('analyzing', 'open') " +
           "AND i.createdAt >= :since")
    Optional<Incident> findActiveByClusterKey(@Param("tenantId") String tenantId,
                                              @Param("clusterKey") String clusterKey,
                                              @Param("since") Instant since);

    @Modifying
    @Query("UPDATE Incident i SET i.status = :status WHERE i.id = :id")
    int updateStatus(@Param("id") String id, @Param("status") String status);

    @Modifying
    @Query("UPDATE Incident i SET i.rootCause = :rootCause, i.confidence = :confidence, i.recommendations = :recommendations WHERE i.id = :id")
    int updateAnalysisResult(@Param("id") String id,
                            @Param("rootCause") String rootCause,
                            @Param("confidence") Double confidence,
                            @Param("recommendations") String recommendations);

    @Query("SELECT COUNT(i) FROM Incident i WHERE i.tenantId = :tenantId AND i.status = 'resolved' AND i.resolvedAt >= :since")
    long countResolvedSince(@Param("tenantId") String tenantId, @Param("since") Instant since);

    @Query("SELECT AVG(TIMESTAMPDIFF(SECOND, i.createdAt, i.resolvedAt)) FROM Incident i " +
           "WHERE i.tenantId = :tenantId AND i.status = 'resolved' AND i.resolvedAt >= :since")
    Double calculateAverageMTTR(@Param("tenantId") String tenantId, @Param("since") Instant since);
}
