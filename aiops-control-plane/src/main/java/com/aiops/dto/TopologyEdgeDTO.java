package com.aiops.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopologyEdgeDTO {
    private String source;
    private String target;
    private String type; // depends, calls, uses_database, uses_cache
}
