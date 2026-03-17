package com.aiops.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

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
