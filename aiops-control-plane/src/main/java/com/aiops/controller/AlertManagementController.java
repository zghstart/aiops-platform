package com.aiops.controller;

import com.aiops.domain.model.AlertModel;
import com.aiops.dto.ApiResponse;
import com.aiops.dto.SilenceRequest;
import com.aiops.service.alert.AlertReceiverService;
import com.aiops.service.alert.NoiseReducerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Alert Management Controller - Full alert lifecycle operations
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
public class AlertManagementController {

    private final AlertReceiverService alertReceiver;
    private final NoiseReducerService noiseReducer;

    /**
     * Get alert by ID
     */
    @GetMapping("/{alertId}")
    public ApiResponse<AlertModel> getAlert(
            @PathVariable String alertId,
            @RequestParam String tenantId) {
        log.debug("Getting alert: {}", alertId);
        var alert = alertReceiver.get(alertId, tenantId);
        return ApiResponse.success(alert);
    }

    /**
     * Acknowledge an alert
     */
    @PostMapping("/{alertId}/acknowledge")
    public ApiResponse<Void> acknowledgeAlert(
            @PathVariable String alertId,
            @RequestParam String tenantId,
            @RequestBody(required = false) Map<String, String> body) {

        String reason = body != null ? body.get("reason") : "Acknowledged by user";
        log.info("Acknowledging alert: {}, reason: {}", alertId, reason);

        alertReceiver.acknowledge(alertId, tenantId, reason);
        return ApiResponse.success();
    }

    /**
     * Silence an alert
     */
    @PostMapping("/{alertId}/silence")
    public ApiResponse<Void> silenceAlert(
            @PathVariable String alertId,
            @RequestParam String tenantId,
            @RequestBody SilenceRequest request) {

        log.info("Silencing alert: {} for {} minutes", alertId, request.getDurationMinutes());
        alertReceiver.silence(alertId, request.getDurationMinutes(), request.getReason());
        return ApiResponse.success();
    }

    /**
     * Resolve an alert
     */
    @PostMapping("/{alertId}/resolve")
    public ApiResponse<Void> resolveAlert(
            @PathVariable String alertId,
            @RequestParam String tenantId,
            @RequestBody Map<String, String> body) {

        String resolution = body.get("resolution");
        log.info("Resolving alert: {}, resolution: {}", alertId, resolution);

        alertReceiver.resolve(alertId, tenantId, resolution);
        return ApiResponse.success();
    }

    /**
     * Batch operations on alerts
     */
    @PostMapping("/batch")
    public ApiResponse<Map<String, Object>> batchOperation(
            @RequestParam String tenantId,
            @RequestBody BatchAlertRequest request) {

        log.info("Batch {} operation on {} alerts", request.getAction(), request.getAlertIds().size());

        int success = 0;
        int failed = 0;

        for (String alertId : request.getAlertIds()) {
            try {
                switch (request.getAction()) {
                    case "acknowledge":
                        alertReceiver.acknowledge(alertId, tenantId, request.getReason());
                        break;
                    case "silence":
                        alertReceiver.silence(alertId, request.getDurationMinutes(), request.getReason());
                        break;
                    case "resolve":
                        alertReceiver.resolve(alertId, tenantId, request.getReason());
                        break;
                }
                success++;
            } catch (Exception e) {
                log.error("Failed to {} alert {}", request.getAction(), alertId, e);
                failed++;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("failed", failed);
        result.put("total", request.getAlertIds().size());

        return ApiResponse.success(result);
    }

    /**
     * Get noise reduction statistics
     */
    @GetMapping("/noise-reduction/stats")
    public ApiResponse<Map<String, NoiseReducerService.NoiseStats>> getNoiseReductionStats(
            @RequestParam String tenantId) {
        var stats = noiseReducer.getStats();
        return ApiResponse.success(stats);
    }

    // Request DTO
    public static class BatchAlertRequest {
        private java.util.List<String> alertIds;
        private String action; // acknowledge, silence, resolve
        private String reason;
        private Integer durationMinutes;

        // Getters and setters
        public java.util.List<String> getAlertIds() { return alertIds; }
        public void setAlertIds(java.util.List<String> alertIds) { this.alertIds = alertIds; }
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public Integer getDurationMinutes() { return durationMinutes; }
        public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }
    }
}
