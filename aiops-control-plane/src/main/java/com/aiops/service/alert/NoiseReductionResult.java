package com.aiops.service.alert;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Noise reduction result
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoiseReductionResult {
    private boolean suppressed;
    private String ruleId;
    private String reason;
    private String clusterKey;
    private boolean rootCauseCandidate;

    public static NoiseReductionResult accepted(String clusterKey, boolean rootCauseCandidate) {
        return NoiseReductionResult.builder()
                .suppressed(false)
                .clusterKey(clusterKey)
                .rootCauseCandidate(rootCauseCandidate)
                .build();
    }

    public static NoiseReductionResult suppressed(String ruleId, String existingId, String reason) {
        return NoiseReductionResult.builder()
                .suppressed(true)
                .ruleId(ruleId)
                .clusterKey(existingId)
                .reason(reason)
                .rootCauseCandidate(false)
                .build();
    }
}
