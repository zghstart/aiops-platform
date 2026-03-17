package com.aiops.service.ai;

import com.aiops.dto.AnalysisResultDTO;
import com.aiops.repository.repository.AlertRepository;
import com.aiops.repository.repository.IncidentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReasoningStreamService {

    private final IncidentRepository incidentRepository;
    private final AlertRepository alertRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void handleAnalysisResult(String incidentId, AnalysisResultDTO result) {
        log.info("Received analysis result for incident: {}, confidence: {}",
                incidentId, result.getConfidence());

        try {
            String recommendationsJson = objectMapper.writeValueAsString(result.getRecommendations());

            incidentRepository.updateAnalysisResult(
                    incidentId,
                    result.getRootCause(),
                    result.getConfidence(),
                    recommendationsJson
            );

            for (String alertId : result.getAlertIds()) {
                alertRepository.updateAiStatus(alertId, "completed");
            }

            log.info("Analysis result saved for incident: {}", incidentId);
        } catch (Exception e) {
            log.error("Failed to process analysis result for incident: {}", incidentId, e);
        }
    }
}
