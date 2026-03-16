package com.aiops.controller;

import com.aiops.domain.model.IncidentModel;
import com.aiops.dto.ApiResponse;
import com.aiops.dto.AnalysisResultDTO;
import com.aiops.service.ai.ReasoningStreamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Incident Controller - Incident lifecycle management
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/incidents")
@RequiredArgsConstructor
public class IncidentController {

    private final ReasoningStreamService reasoningStreamService;
    private final com.aiops.service.incident.IncidentService incidentService;

    /**
     * List incidents with filtering
     */
    @GetMapping
    public ApiResponse<ApiResponse.PageResult<IncidentModel>> listIncidents(
            @RequestParam String tenantId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String serviceId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        var result = incidentService.list(tenantId, status, serviceId, page, size);
        return ApiResponse.success(result);
    }

    /**
     * Get incident by ID
     */
    @GetMapping("/{incidentId}")
    public ApiResponse<IncidentModel> getIncident(
            @PathVariable String incidentId,
            @RequestParam String tenantId) {

        var incident = incidentService.get(incidentId, tenantId);
        return ApiResponse.success(incident);
    }

    /**
     * Get incident with related alerts
     */
    @GetMapping("/{incidentId}/details")
    public ApiResponse<Map<String, Object>> getIncidentDetails(
            @PathVariable String incidentId,
            @RequestParam String tenantId) {

        var details = incidentService.getDetails(incidentId, tenantId);
        return ApiResponse.success(details);
    }

    /**
     * Acknowledge an incident
     */
    @PostMapping("/{incidentId}/acknowledge")
    public ApiResponse<Void> acknowledgeIncident(
            @PathVariable String incidentId,
            @RequestParam String tenantId,
            @RequestBody(required = false) Map<String, String> body) {

        String reason = body != null ? body.get("reason") : "Acknowledged by user";
        incidentService.acknowledge(incidentId, tenantId, reason);
        log.info("Incident {} acknowledged: {}", incidentId, reason);
        return ApiResponse.success();
    }

    /**
     * Update incident status
     */
    @PutMapping("/{incidentId}/status")
    public ApiResponse<Void> updateStatus(
            @PathVariable String incidentId,
            @RequestParam String tenantId,
            @RequestBody StatusUpdateRequest request) {

        incidentService.updateStatus(incidentId, tenantId, request.getStatus(), request.getComment());
        log.info("Incident {} status updated to {}", incidentId, request.getStatus());
        return ApiResponse.success();
    }

    /**
     * Get incident timeline (history of changes)
     */
    @GetMapping("/{incidentId}/timeline")
    public ApiResponse<List<Map<String, Object>>> getIncidentTimeline(
            @PathVariable String incidentId,
            @RequestParam String tenantId) {

        var timeline = incidentService.getTimeline(incidentId, tenantId);
        return ApiResponse.success(timeline);
    }

    /**
     * Get AI analysis result for incident
     */
    @GetMapping("/{incidentId}/analysis")
    public ApiResponse<AnalysisResultDTO> getAnalysisResult(
            @PathVariable String incidentId,
            @RequestParam String tenantId) {

        var result = incidentService.getAnalysisResult(incidentId, tenantId);
        return ApiResponse.success(result);
    }

    /**
     * Trigger AI analysis for incident
     */
    @PostMapping("/{incidentId}/analyze")
    public ApiResponse<Map<String, String>> triggerAnalysis(
            @PathVariable String incidentId,
            @RequestParam String tenantId) {

        String analysisId = incidentService.triggerAnalysis(incidentId, tenantId);
        return ApiResponse.success(Map.of(
                "analysisId", analysisId,
                "status", "in_progress",
                "message", "AI analysis started for incident: " + incidentId
        ));
    }

    /**
     * Merge incidents
     */
    @PostMapping("/{targetId}/merge")
    public ApiResponse<Void> mergeIncidents(
            @PathVariable String targetId,
            @RequestParam String tenantId,
            @RequestBody MergeRequest request) {

        incidentService.merge(targetId, request.getSourceIds(), tenantId);
        log.info("Merged {} incidents into {}", request.getSourceIds().size(), targetId);
        return ApiResponse.success();
    }

    // Request DTOs
    public static class StatusUpdateRequest {
        private String status;
        private String comment;

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }
    }

    public static class MergeRequest {
        private List<String> sourceIds;

        public List<String> getSourceIds() { return sourceIds; }
        public void setSourceIds(List<String> sourceIds) { this.sourceIds = sourceIds; }
    }
}
