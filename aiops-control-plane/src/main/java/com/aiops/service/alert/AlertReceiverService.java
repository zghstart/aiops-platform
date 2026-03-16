package com.aiops.service.alert;

import com.aiops.domain.event.AlertReceivedEvent;
import com.aiops.domain.model.AlertModel;
import com.aiops.domain.model.IncidentModel;
import com.aiops.dto.AlertResponse;
import com.aiops.dto.AlertVO;
import com.aiops.dto.AlertWebhookRequest;
import com.aiops.dto.ApiResponse;
import com.aiops.repository.entity.Alert;
import com.aiops.repository.entity.Incident;
import com.aiops.repository.repository.AlertRepository;
import com.aiops.repository.repository.IncidentRepository;
import com.aiops.service.ai.AITaskDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertReceiverService {

    private final AlertRepository alertRepository;
    private final IncidentRepository incidentRepository;
    private final NoiseReducerService noiseReducer;
    private final AITaskDispatcher aiDispatcher;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public AlertModel receive(AlertWebhookRequest request, String tenantId) {
        log.info("Receiving alert from source: {}, tenant: {}", request.getSource(), tenantId);

        Alert alert = Alert.builder()
                .alertId(request.getAlertId() != null ? request.getAlertId() : generateId())
                .tenantId(tenantId)
                .source(request.getSource())
                .severity(request.getSeverity())
                .title(request.getTitle())
                .description(request.getDescription())
                .serviceId(request.getServiceId())
                .labels(request.getLabels())
                .instance(request.getLabels() != null ? request.getLabels().get("instance") : null)
                .startsAt(request.getStartsAt())
                .status("active")
                .aiStatus("pending")
                .payload(request.getPayload() != null ? request.getPayload().toString() : null)
                .build();

        NoiseReductionResult noiseResult = noiseReducer.process(alert);

        if (noiseResult.isSuppressed()) {
            alert.setStatus("suppressed");
            alert.setSilencedBy(noiseResult.getRuleId());
            alert.setSilenceReason(noiseResult.getReason());
            log.info("Alert {} suppressed by rule {}", alert.getAlertId(), noiseResult.getRuleId());
        }

        IncidentModel incident = findOrCreateIncident(alert, noiseResult.getClusterKey());
        alert.setIncidentId(incident.getId());

        alertRepository.save(alert);

        if (noiseResult.isRootCauseCandidate() && !"suppressed".equals(alert.getStatus())) {
            alert.setAiStatus("in_progress");
            alertRepository.updateAiStatus(alert.getAlertId(), "in_progress");
            aiDispatcher.dispatch(incident.getId(), alert);
        }

        eventPublisher.publishEvent(new AlertReceivedEvent(this, alert));

        return AlertModel.fromEntity(alert);
    }

    public ApiResponse.PageResult<AlertVO> list(String tenantId, String status, String severity,
                                                 String serviceId, int page, int size) {
        Page<Alert> alerts = alertRepository.findAlerts(tenantId, status, severity, serviceId,
                PageRequest.of(page - 1, size));

        return ApiResponse.PageResult.<AlertVO>builder()
                .items(alerts.getContent().stream()
                        .map(this::toAlertVO)
                        .toList())
                .total(alerts.getTotalElements())
                .page(page)
                .size(size)
                .totalPages(alerts.getTotalPages())
                .build();
    }

    @Transactional
    public void silence(String alertId, Integer durationMinutes, String reason) {
        alertRepository.silenceAlert(alertId, "MANUAL", reason);
        log.info("Alert {} silenced for {} minutes, reason: {}", alertId, durationMinutes, reason);
    }

    private IncidentModel findOrCreateIncident(Alert alert, String clusterKey) {
        Optional<Incident> existing = incidentRepository
                .findActiveByClusterKey(alert.getTenantId(), clusterKey, Instant.now().minusSeconds(1800));

        if (existing.isPresent()) {
            return IncidentModel.fromEntity(existing.get());
        }

        Incident incident = Incident.builder()
                .id(generateId())
                .tenantId(alert.getTenantId())
                .clusterKey(clusterKey)
                .serviceId(alert.getServiceId())
                .status("analyzing")
                .createdAt(Instant.now())
                .build();

        incidentRepository.save(incident);
        return IncidentModel.fromEntity(incident);
    }

    private String generateId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private AlertVO toAlertVO(Alert alert) {
        return AlertVO.builder()
                .alertId(alert.getAlertId())
                .incidentId(alert.getIncidentId())
                .source(alert.getSource())
                .severity(alert.getSeverity())
                .title(alert.getTitle())
                .serviceId(alert.getServiceId())
                .status(alert.getStatus())
                .aiStatus(alert.getAiStatus())
                .startsAt(alert.getStartsAt())
                .createdAt(alert.getCreatedAt())
                .build();
    }
}
