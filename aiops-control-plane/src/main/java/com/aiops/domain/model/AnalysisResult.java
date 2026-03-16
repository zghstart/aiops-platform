package com.aiops.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisResult {

    private String incidentId;
    private String tenantId;
    private Double confidence;
    private String rootCause;
    private List<String> evidence;
    private List<String> recommendations;
    private List<ReasoningStep> reasoningChain;
    private Integer tokensUsed;
    private Double analysisTimeSec;
    private Instant completedAt;
    private Map<String, Object> metadata;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReasoningStep {
        private Integer step;
        private String thought;
        private String action;
        private String actionInput;
        private String observation;
        private Instant timestamp;
    }
}
