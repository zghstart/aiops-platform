package com.aiops.service.ai;

import com.aiops.repository.entity.Alert;
import com.aiops.service.topology.TopologyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class AITaskDispatcher {

    private final TopologyService topologyService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void dispatch(String incidentId, Alert alert) {
        log.info("Dispatching AI task for incident: {}, alert: {}", incidentId, alert.getAlertId());

        AITaskMessage task = AITaskMessage.builder()
                .taskId(UUID.randomUUID().toString())
                .incidentId(incidentId)
                .tenantId(alert.getTenantId())
                .alertIds(List.of(alert.getAlertId()))
                .context(buildContext(alert))
                .options(Map.of(
                        "stream", true,
                        "max_rounds", 5,
                        "confidence_threshold", 0.7,
                        "max_analysis_time_sec", 60
                ))
                .timestamp(System.currentTimeMillis())
                .build();

        String topic = "aiops.ai-tasks";
        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(topic, alert.getTenantId(), task);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("AI task dispatched successfully: {}", task.getTaskId());
            } else {
                log.error("Failed to dispatch AI task: {}", task.getTaskId(), ex);
            }
        });
    }

    private Map<String, Object> buildContext(Alert alert) {
        String serviceId = alert.getServiceId();
        String instance = alert.getLabels() != null ? alert.getLabels().get("instance") : null;

        Map<String, Object> timeRange = Map.of(
                "start", DateTimeFormatter.ISO_INSTANT.format(alert.getStartsAt().minusSeconds(1800)),
                "end", DateTimeFormatter.ISO_INSTANT.format(alert.getStartsAt().plusSeconds(300))
        );

        return Map.of(
                "serviceId", serviceId,
                "instance", instance != null ? instance : "",
                "timeRange", timeRange,
                "topology", topologyService.getTopology(alert.getTenantId(), serviceId, 2, "both"),
                "alertTitle", alert.getTitle(),
                "alertDescription", alert.getDescription() != null ? alert.getDescription() : ""
        );
    }
}
