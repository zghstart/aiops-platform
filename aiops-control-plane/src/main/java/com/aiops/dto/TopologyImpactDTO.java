package com.aiops.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

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
