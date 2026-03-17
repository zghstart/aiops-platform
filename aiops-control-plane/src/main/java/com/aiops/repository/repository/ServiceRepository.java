package com.aiops.repository.repository;

import com.aiops.repository.entity.ServiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceRepository extends JpaRepository<ServiceEntity, String> {

    Optional<ServiceEntity> findByTenantIdAndServiceId(String tenantId, String serviceId);

    List<ServiceEntity> findByTenantIdAndStatus(String tenantId, String status);

    @Query(value = "SELECT s.* FROM service_entities s WHERE s.tenant_id = :tenantId AND s.id IN " +
           "(SELECT d.target_service_id FROM service_dependencies d WHERE d.source_service_id = :serviceId AND d.tenant_id = :tenantId)", nativeQuery = true)
    List<ServiceEntity> findDownstreamServices(@Param("tenantId") String tenantId, @Param("serviceId") String serviceId);

    @Query(value = "SELECT s.* FROM service_entities s WHERE s.tenant_id = :tenantId AND s.id IN " +
           "(SELECT d.source_service_id FROM service_dependencies d WHERE d.target_service_id = :serviceId AND d.tenant_id = :tenantId)", nativeQuery = true)
    List<ServiceEntity> findUpstreamServices(@Param("tenantId") String tenantId, @Param("serviceId") String serviceId);

    @Modifying
    @Query("UPDATE ServiceEntity s SET s.health = :health WHERE s.tenantId = :tenantId AND s.serviceId = :serviceId")
    void updateHealth(@Param("tenantId") String tenantId, @Param("serviceId") String serviceId, @Param("health") String health);

    @Modifying
    @Query(value = "INSERT INTO service_dependencies (tenant_id, source_service_id, target_service_id) " +
                   "VALUES (:tenantId, :sourceServiceId, :targetServiceId) " +
                   "ON CONFLICT DO NOTHING", nativeQuery = true)
    void addDependency(@Param("tenantId") String tenantId,
                       @Param("sourceServiceId") String sourceServiceId,
                       @Param("targetServiceId") String targetServiceId);

    @Modifying
    @Query(value = "DELETE FROM service_dependencies WHERE tenant_id = :tenantId " +
                   "AND source_service_id = :sourceServiceId AND target_service_id = :targetServiceId", nativeQuery = true)
    void removeDependency(@Param("tenantId") String tenantId,
                          @Param("sourceServiceId") String sourceServiceId,
                          @Param("targetServiceId") String targetServiceId);
}
