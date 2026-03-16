package com.aiops.service.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AITaskMessage {
    private String taskId;
    private String incidentId;
    private String tenantId;
    private List<String> alertIds;
    private Map<String, Object> context;
    private Map<String, Object> options;
    private Long timestamp;
}
