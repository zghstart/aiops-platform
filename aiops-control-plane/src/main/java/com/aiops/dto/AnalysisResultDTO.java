package com.aiops.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisResultDTO {
    private String incidentId;
    private List<String> alertIds;
    private String rootCause;
    private Double confidence;
    private List<String> evidence;
    private List<String> recommendations;
    private Integer tokensUsed;
    private Double analysisTimeSec;
    private Instant completedAt;
}
