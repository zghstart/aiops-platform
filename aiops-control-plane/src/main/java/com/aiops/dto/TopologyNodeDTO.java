package com.aiops.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
