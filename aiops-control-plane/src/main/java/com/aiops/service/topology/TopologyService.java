package com.aiops.service.topology;

import com.aiops.dto.TopologyDataDTO;
import com.aiops.dto.TopologyNodeDTO;
import com.aiops.dto.TopologyEdgeDTO;
import com.aiops.dto.TopologyImpactDTO;
import com.aiops.infrastructure.prometheus.PrometheusClient;
import com.aiops.repository.entity.ServiceEntity;
import com.aiops.repository.repository.ServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Topology Service - Service dependency graph and impact analysis
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TopologyService {

    private final ServiceRepository serviceRepository;
    private final PrometheusClient prometheusClient;
    private final StringRedisTemplate redisTemplate;

    // Cache topology data for 5 minutes
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    /**
     * Get service topology with dependencies
     */
    public TopologyDataDTO getTopology(String tenantId, String serviceId, int depth, String direction) {
        log.info("Getting topology for service: {}, depth: {}, direction: {}", serviceId, depth, direction);

        // Check cache
        String cacheKey = String.format("topology:%s:%s:%d:%s", tenantId, serviceId, depth, direction);
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.debug("Topology cache hit for {}", serviceId);
            // Note: In production, use JSON deserialization
        }

        // Get root service
        ServiceEntity rootService = serviceRepository.findByTenantIdAndServiceId(tenantId, serviceId)
                .orElse(createDefaultService(tenantId, serviceId));

        // Build topology graph
        Set<String> processedServices = new HashSet<>();
        List<TopologyNodeDTO> nodes = new ArrayList<>();
        List<TopologyEdgeDTO> edges = new ArrayList<>();

        // Add root node
        nodes.add(createServiceNode(rootService, true));
        processedServices.add(serviceId);

        // Build dependencies based on direction
        if (direction.equals("downstream") || direction.equals("both")) {
            buildDownstreamTopology(tenantId, serviceId, depth, 1, nodes, edges, processedServices);
        }

        if (direction.equals("upstream") || direction.equals("both")) {
            buildUpstreamTopology(tenantId, serviceId, depth, 1, nodes, edges, processedServices);
        }

        // Add infrastructure nodes
        addInfrastructureNodes(nodes, edges, serviceId);

        // Calculate impact analysis
        TopologyImpactDTO impact = calculateImpact(serviceId, nodes, edges);

        // Enrich with metrics
        enrichWithMetrics(nodes);

        TopologyDataDTO result = TopologyDataDTO.builder()
                .serviceId(serviceId)
                .nodes(nodes)
                .edges(edges)
                .depth(depth)
                .direction(direction)
                .impactAnalysis(impact)
                .metadata(Map.of(
                        "totalNodes", nodes.size(),
                        "totalEdges", edges.size(),
                        "timestamp", Instant.now().toString()
                ))
                .cacheHit(false)
                .build();

        // Cache result (serialize to JSON in production)
        // redisTemplate.opsForValue().set(cacheKey, serialize(result), CACHE_TTL);

        return result;
    }

    /**
     * Build downstream dependencies recursively
     */
    private void buildDownstreamTopology(String tenantId, String parentId, int maxDepth, int currentDepth,
                                         List<TopologyNodeDTO> nodes, List<TopologyEdgeDTO> edges,
                                         Set<String> processed) {
        if (currentDepth > maxDepth) return;

        List<ServiceEntity> dependencies = serviceRepository.findDownstreamServices(tenantId, parentId);

        for (ServiceEntity dep : dependencies) {
            if (processed.contains(dep.getServiceId())) continue;

            nodes.add(createServiceNode(dep, false));
            edges.add(TopologyEdgeDTO.builder()
                    .source(parentId)
                    .target(dep.getServiceId())
                    .type("depends")
                    .build());

            processed.add(dep.getServiceId());

            // Recurse
            buildDownstreamTopology(tenantId, dep.getServiceId(), maxDepth, currentDepth + 1,
                    nodes, edges, processed);
        }
    }

    /**
     * Build upstream dependencies recursively
     */
    private void buildUpstreamTopology(String tenantId, String serviceId, int maxDepth, int currentDepth,
                                       List<TopologyNodeDTO> nodes, List<TopologyEdgeDTO> edges,
                                       Set<String> processed) {
        if (currentDepth > maxDepth) return;

        List<ServiceEntity> callers = serviceRepository.findUpstreamServices(tenantId, serviceId);

        for (ServiceEntity caller : callers) {
            if (processed.contains(caller.getServiceId())) continue;

            nodes.add(createServiceNode(caller, false));
            edges.add(TopologyEdgeDTO.builder()
                    .source(caller.getServiceId())
                    .target(serviceId)
                    .type("calls")
                    .build());

            processed.add(caller.getServiceId());

            // Recurse
            buildUpstreamTopology(tenantId, caller.getServiceId(), maxDepth, currentDepth + 1,
                    nodes, edges, processed);
        }
    }

    /**
     * Add infrastructure nodes (databases, caches, etc.)
     */
    private void addInfrastructureNodes(List<TopologyNodeDTO> nodes, List<TopologyEdgeDTO> edges, String serviceId) {
        // Check if already has infra nodes
        boolean hasDb = nodes.stream().anyMatch(n -> n.getId().startsWith(serviceId + "-db"));
        boolean hasCache = nodes.stream().anyMatch(n -> n.getId().startsWith(serviceId + "-cache"));

        if (!hasDb) {
            String dbId = serviceId + "-db";
            nodes.add(TopologyNodeDTO.builder()
                    .id(dbId)
                    .name(dbId)
                    .type("database")
                    .health("healthy")
                    .infraType("mysql")
                    .build());
            edges.add(TopologyEdgeDTO.builder()
                    .source(serviceId)
                    .target(dbId)
                    .type("uses_database")
                    .build());
        }

        if (!hasCache) {
            String cacheId = serviceId + "-cache";
            nodes.add(TopologyNodeDTO.builder()
                    .id(cacheId)
                    .name(cacheId)
                    .type("cache")
                    .health("healthy")
                    .infraType("redis")
                    .build());
            edges.add(TopologyEdgeDTO.builder()
                    .source(serviceId)
                    .target(cacheId)
                    .type("uses_cache")
                    .build());
        }
    }

    /**
     * Calculate impact analysis for the root service
     */
    private TopologyImpactDTO calculateImpact(String rootServiceId,
                                               List<TopologyNodeDTO> nodes,
                                               List<TopologyEdgeDTO> edges) {
        // Find direct dependencies (services this one depends on)
        List<String> directDeps = edges.stream()
                .filter(e -> e.getSource().equals(rootServiceId))
                .map(TopologyEdgeDTO::getTarget)
                .collect(Collectors.toList());

        // Find dependent services (services that depend on this one)
        List<String> dependentServices = edges.stream()
                .filter(e -> e.getTarget().equals(rootServiceId))
                .map(TopologyEdgeDTO::getSource)
                .collect(Collectors.toList());

        // Calculate blast radius (all connected services)
        Set<String> allConnected = new HashSet<>();
        allConnected.add(rootServiceId);
        allConnected.addAll(directDeps);
        allConnected.addAll(dependentServices);

        // Find unhealthy dependencies
        List<String> unhealthyDeps = nodes.stream()
                .filter(n -> "error".equals(n.getHealth()) || "critical".equals(n.getHealth()))
                .map(TopologyNodeDTO::getId)
                .collect(Collectors.toList());

        // Determine risk level
        String riskLevel;
        if (unhealthyDeps.size() > 0 && dependentServices.size() > 5) {
            riskLevel = "high";
        } else if (unhealthyDeps.size() > 0 || directDeps.size() > 3) {
            riskLevel = "medium";
        } else {
            riskLevel = "low";
        }

        return TopologyImpactDTO.builder()
                .directDependencies(directDeps)
                .dependentServices(dependentServices)
                .blastRadius(allConnected.size())
                .unhealthyDependencies(unhealthyDeps)
                .riskLevel(riskLevel)
                .build();
    }

    /**
     * Enrich nodes with current metrics from Prometheus
     */
    private void enrichWithMetrics(List<TopologyNodeDTO> nodes) {
        for (TopologyNodeDTO node : nodes) {
            if ("service".equals(node.getType())) {
                try {
                    // Fetch metrics from Prometheus
                    Map<String, Double> metrics = prometheusClient.getServiceMetrics(node.getId());
                    node.setLatencyP99(metrics.getOrDefault("latency_p99", 0.0));
                    node.setErrorRate(metrics.getOrDefault("error_rate", 0.0));
                    node.setQps(metrics.getOrDefault("qps", 0.0));
                    node.setAvailability(metrics.getOrDefault("availability", 99.9));

                    // Update health based on metrics
                    node.setHealth(determineHealth(metrics));
                } catch (Exception e) {
                    log.warn("Failed to enrich metrics for {}", node.getId(), e);
                }
            }
        }
    }

    /**
     * Determine health status from metrics
     */
    private String determineHealth(Map<String, Double> metrics) {
        double errorRate = metrics.getOrDefault("error_rate", 0.0);
        double availability = metrics.getOrDefault("availability", 99.9);

        if (errorRate > 0.1 || availability < 95.0) {
            return "critical";
        } else if (errorRate > 0.05 || availability < 99.0) {
            return "error";
        } else if (errorRate > 0.01 || availability < 99.9) {
            return "warning";
        }
        return "healthy";
    }

    /**
     * Create a service node DTO
     */
    private TopologyNodeDTO createServiceNode(ServiceEntity service, boolean isRoot) {
        return TopologyNodeDTO.builder()
                .id(service.getServiceId())
                .name(service.getName())
                .type(service.getType())
                .health(service.getHealth())
                .isRoot(isRoot)
                .build();
    }

    /**
     * Create default service for unknown services
     */
    private ServiceEntity createDefaultService(String tenantId, String serviceId) {
        return ServiceEntity.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(tenantId)
                .serviceId(serviceId)
                .name(serviceId)
                .type("service")
                .health("unknown")
                .build();
    }

    /**
     * Get downstream services list
     */
    public List<String> getDownstreamServices(String tenantId, String serviceId) {
        return serviceRepository.findDownstreamServices(tenantId, serviceId)
                .stream()
                .map(ServiceEntity::getServiceId)
                .collect(Collectors.toList());
    }

    /**
     * Get upstream services list
     */
    public List<String> getUpstreamServices(String tenantId, String serviceId) {
        return serviceRepository.findUpstreamServices(tenantId, serviceId)
                .stream()
                .map(ServiceEntity::getServiceId)
                .collect(Collectors.toList());
    }

    /**
     * Update service health status
     */
    public void updateServiceHealth(String tenantId, String serviceId, String health) {
        serviceRepository.updateHealth(tenantId, serviceId, health);
        // Invalidate cache
        String cachePattern = String.format("topology:%s:%s:*", tenantId, serviceId);
        // redisTemplate.delete(redisTemplate.keys(cachePattern));
    }

    /**
     * Register a dependency between services
     */
    public void addDependency(String tenantId, String sourceService, String targetService) {
        serviceRepository.addDependency(tenantId, sourceService, targetService);
        // Invalidate caches
        redisTemplate.delete(String.format("topology:%s:%s:*", tenantId, sourceService));
        redisTemplate.delete(String.format("topology:%s:%s:*", tenantId, targetService));
    }

    /**
     * Remove a dependency
     */
    public void removeDependency(String tenantId, String sourceService, String targetService) {
        serviceRepository.removeDependency(tenantId, sourceService, targetService);
        // Invalidate caches
        redisTemplate.delete(String.format("topology:%s:%s:*", tenantId, sourceService));
        redisTemplate.delete(String.format("topology:%s:%s:*", tenantId, targetService));
    }
}
