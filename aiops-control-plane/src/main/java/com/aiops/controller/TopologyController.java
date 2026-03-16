package com.aiops.controller;

import com.aiops.dto.ApiResponse;
import com.aiops.dto.TopologyDataDTO;
import com.aiops.dto.TopologyNodeDTO;
import com.aiops.service.topology.TopologyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Topology Controller - Service topology and dependency management
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/topology")
@RequiredArgsConstructor
public class TopologyController {

    private final TopologyService topologyService;

    /**
     * Get service topology with dependencies
     */
    @GetMapping("/{serviceId}")
    public ApiResponse<TopologyDataDTO> getTopology(
            @PathVariable String serviceId,
            @RequestParam String tenantId,
            @RequestParam(defaultValue = "2") int depth,
            @RequestParam(defaultValue = "both") String direction) {

        log.debug("Getting topology for service: {}, depth: {}, direction: {}",
                serviceId, depth, direction);

        TopologyDataDTO topology = topologyService.getTopology(tenantId, serviceId, depth, direction);
        return ApiResponse.success(topology);
    }

    /**
     * Get downstream services (dependencies)
     */
    @GetMapping("/{serviceId}/downstream")
    public ApiResponse<List<String>> getDownstreamServices(
            @PathVariable String serviceId,
            @RequestParam String tenantId) {

        List<String> services = topologyService.getDownstreamServices(tenantId, serviceId);
        return ApiResponse.success(services);
    }

    /**
     * Get upstream services (callers)
     */
    @GetMapping("/{serviceId}/upstream")
    public ApiResponse<List<String>> getUpstreamServices(
            @PathVariable String serviceId,
            @RequestParam String tenantId) {

        List<String> services = topologyService.getUpstreamServices(tenantId, serviceId);
        return ApiResponse.success(services);
    }

    /**
     * Register service dependency
     */
    @PostMapping("/dependencies")
    public ApiResponse<Void> addDependency(
            @RequestParam String tenantId,
            @RequestBody DependencyRequest request) {

        topologyService.addDependency(tenantId, request.getSourceService(), request.getTargetService());
        log.info("Added dependency: {} -> {}", request.getSourceService(), request.getTargetService());
        return ApiResponse.success();
    }

    /**
     * Remove service dependency
     */
    @DeleteMapping("/dependencies")
    public ApiResponse<Void> removeDependency(
            @RequestParam String tenantId,
            @RequestBody DependencyRequest request) {

        topologyService.removeDependency(tenantId, request.getSourceService(), request.getTargetService());
        log.info("Removed dependency: {} -> {}", request.getSourceService(), request.getTargetService());
        return ApiResponse.success();
    }

    /**
     * Update service health status
     */
    @PutMapping("/{serviceId}/health")
    public ApiResponse<Void> updateHealth(
            @PathVariable String serviceId,
            @RequestParam String tenantId,
            @RequestBody Map<String, String> body) {

        String health = body.getOrDefault("health", "unknown");
        topologyService.updateServiceHealth(tenantId, serviceId, health);
        log.info("Updated service health: {} -> {}", serviceId, health);
        return ApiResponse.success();
    }

    /**
     * Get topology impact analysis for an incident
     */
    @GetMapping("/{serviceId}/impact")
    public ApiResponse<Map<String, Object>> getImpactAnalysis(
            @PathVariable String serviceId,
            @RequestParam String tenantId,
            @RequestParam(defaultValue = "2") int depth) {

        TopologyDataDTO topology = topologyService.getTopology(tenantId, serviceId, depth, "both");
        Map<String, Object> impact = Map.of(
                "serviceId", serviceId,
                "blastRadius", topology.getImpactAnalysis().getBlastRadius(),
                "directDependencies", topology.getImpactAnalysis().getDirectDependencies(),
                "dependentServices", topology.getImpactAnalysis().getDependentServices(),
                "riskLevel", topology.getImpactAnalysis().getRiskLevel(),
                "unhealthyDependencies", topology.getImpactAnalysis().getUnhealthyDependencies()
        );
        return ApiResponse.success(impact);
    }

    // Request DTO
    public static class DependencyRequest {
        private String sourceService;
        private String targetService;

        public String getSourceService() { return sourceService; }
        public void setSourceService(String sourceService) { this.sourceService = sourceService; }
        public String getTargetService() { return targetService; }
        public void setTargetService(String targetService) { this.targetService = targetService; }
    }
}
