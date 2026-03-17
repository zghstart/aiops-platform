package com.aiops.service.dashboard;

import com.aiops.dto.ApiResponse;
import com.aiops.dto.DashboardSummaryDTO;
import com.aiops.dto.TopologyDataDTO;
import com.aiops.repository.repository.AlertRepository;
import com.aiops.repository.repository.IncidentRepository;
import com.aiops.service.ai.ReasoningStreamService;
import com.aiops.service.topology.TopologyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardDataService {

    private final AlertRepository alertRepository;
    private final IncidentRepository incidentRepository;
    private final TopologyService topologyService;
    private final ReasoningStreamService reasoningStreamService;

    public DashboardSummaryDTO getSummary(String tenantId) {
        long activeAlerts = alertRepository.countActiveByTenant(tenantId);
        List<Object[]> severityCounts = alertRepository.countBySeverity(tenantId);

        Map<String, Long> alertBySeverity = severityCounts.stream()
                .collect(java.util.stream.Collectors.toMap(
                        arr -> (String) arr[0],
                        arr -> (Long) arr[1]
                ));

        long resolvedToday = incidentRepository.countResolvedSince(tenantId, Instant.now().minusSeconds(86400));
        Double avgMTTR = incidentRepository.calculateAverageMTTR(tenantId, Instant.now().minusSeconds(2592000));

        return DashboardSummaryDTO.builder()
                .activeAlerts(activeAlerts)
                .alertBySeverity(alertBySeverity)
                .resolvedToday(resolvedToday)
                .averageMTTR(avgMTTR != null ? avgMTTR / 60 : 0)
                .systemHealth(calculateSystemHealth(alertBySeverity))
                .build();
    }

    public com.aiops.dto.TopologyDataDTO getTopology(String tenantId, String serviceId, int depth) {
        return topologyService.getTopology(
                tenantId, serviceId != null ? serviceId : "root", depth, "both");
    }

    private String calculateSystemHealth(Map<String, Long> alertBySeverity) {
        long p1 = alertBySeverity.getOrDefault("P1", 0L);
        long p2 = alertBySeverity.getOrDefault("P2", 0L);

        if (p1 > 0) return "critical";
        if (p2 > 0) return "warning";
        return "healthy";
    }
}
