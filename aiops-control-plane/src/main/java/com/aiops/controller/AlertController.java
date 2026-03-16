package com.aiops.controller;

import com.aiops.dto.*;
import com.aiops.service.alert.AlertReceiverService;
import com.aiops.service.dashboard.DashboardDataService;
import com.aiops.service.ai.ReasoningStreamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AlertController {

    private final AlertReceiverService alertReceiver;
    private final DashboardDataService dashboardService;
    private final ReasoningStreamService reasoningStreamService;

    // Simplified tenant validation
    private String validateApiKey(String apiKey) {
        return "default";
    }

    @RateLimiter(name = "alert-webhook", fallbackMethod = "rateLimitFallback")
    public ApiResponse<AlertResponse> receiveAlert(
            @RequestHeader("X-API-Key") String apiKey,
            @Valid @RequestBody AlertWebhookRequest request) {

        log.info("Received alert from source: {}", request.getSource());

        String tenantId = validateApiKey(apiKey);
        var alert = alertReceiver.receive(request, tenantId);

        return ApiResponse.success(
                AlertResponse.builder()
                        .alertId(alert.getId())
                        .incidentId(alert.getIncidentId())
                        .status(alert.getStatus())
                        .aiStatus(alert.getAiStatus())
                        .build()
        );
    }

    private ApiResponse<AlertResponse> rateLimitFallback(String apiKey, AlertWebhookRequest request, Throwable t) {
        log.warn("Rate limit exceeded for alert webhook");
        return ApiResponse.error(429, "Too many requests, please retry later");
    }

    @GetMapping("/alerts")
    public ApiResponse<ApiResponse.PageResult<AlertVO>> listAlerts(
            @RequestParam String tenantId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String serviceId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ApiResponse.success(alertReceiver.list(tenantId, status, severity, serviceId, page, size));
    }

    @PostMapping("/alerts/{alertId}/silence")
    public ApiResponse<Void> silenceAlert(
            @PathVariable String alertId,
            @RequestBody SilenceRequest request) {

        alertReceiver.silence(alertId, request.getDurationMinutes(), request.getReason());
        return ApiResponse.success();
    }

    @GetMapping("/dashboard/summary")
    public ApiResponse<DashboardSummaryDTO> getSummary(@RequestParam String tenantId) {
        return ApiResponse.success(dashboardService.getSummary(tenantId));
    }

    @GetMapping("/dashboard/topology")
    public ApiResponse<TopologyDataDTO> getTopology(
            @RequestParam String tenantId,
            @RequestParam(required = false) String serviceId,
            @RequestParam(defaultValue = "2") int depth) {
        return ApiResponse.success(dashboardService.getTopology(tenantId, serviceId, depth));
    }

    // SSE emitter storage
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    @GetMapping(value = "/dashboard/ai-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAIReasoning(@RequestParam String tenantId) {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.put(tenantId, emitter);

        emitter.onCompletion(() -> emitters.remove(tenantId));
        emitter.onTimeout(() -> emitters.remove(tenantId));
        emitter.onError((e) -> emitters.remove(tenantId));

        return emitter;
    }

    @PostMapping("/internal/analysis-result")
    public ApiResponse<Void> receiveAnalysisResult(@RequestBody AnalysisResultDTO result) {
        log.info("Received analysis result for incident: {}", result.getIncidentId());
        reasoningStreamService.handleAnalysisResult(result.getIncidentId(), result);
        return ApiResponse.success();
    }
}
