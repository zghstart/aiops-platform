package com.aiops.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Topology Data Transfer Objects
 */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopologyDataDTO {
    private String serviceId;
    private List<TopologyNodeDTO> nodes;
    private List<TopologyEdgeDTO> edges;
    private int depth;
    private String direction;
    private TopologyImpactDTO impactAnalysis;
    private Map<String, Object> metadata;
    private boolean cacheHit;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopologyNodeDTO {
    private String id;
    private String name;
    private String type; // service, database, cache, gateway, queue
    private String health; // healthy, warning, error, critical, unknown
    private boolean isRoot;
    private Double latencyP99;
    private Double errorRate;
    private Double qps;
    private Double availability;
    private String infraType; // mysql, redis, kafka, etc.
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopologyEdgeDTO {
    private String source;
    private String target;
    private String type; // depends, calls, uses_database, uses_cache
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopologyImpactDTO {
    private List<String> directDependencies;
    private List<String> dependentServices;
    private int blastRadius;
    private List<String> unhealthyDependencies;
    private String riskLevel; // high, medium, low
}
