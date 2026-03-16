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
    private String tenantId;
    private String serviceId;
    private int depth;
    private List<Map<String, Object>> nodes;
    private List<Map<String, Object>> edges;
}
