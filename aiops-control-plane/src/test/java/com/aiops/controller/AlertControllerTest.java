package com.aiops.controller;

import com.aiops.dto.ApiResponse;
import com.aiops.service.alert.AlertReceiverService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AlertController
 */
@ExtendWith(MockitoExtension.class)
public class AlertControllerTest {

    @Mock
    private AlertReceiverService alertReceiverService;

    @InjectMocks
    private AlertController alertController;

    @Test
    void testReceiveAlert() {
        // Given
        var request = new com.aiops.dto.AlertWebhookRequest();
        request.setSource("prometheus");
        request.setTitle("High CPU");
        request.setSeverity("P1");
        request.setServiceId("payment-service");

        var mockResult = new com.aiops.domain.model.AlertModel();
        mockResult.setId("alert-001");
        mockResult.setIncidentId("inc-001");
        mockResult.setStatus("active");

        when(alertReceiverService.receive(any(), any())).thenReturn(mockResult);

        // When
        var response = alertController.receiveAlert("api-key", request);

        // Then
        assertNotNull(response);
        assertNotNull(response.getData());
    }

    @Test
    void testListAlerts() {
        // Given
        var mockResult = new ApiResponse.PageResult<com.aiops.dto.AlertVO>();
        mockResult.setItems(Collections.emptyList());
        mockResult.setTotal(0L);
        mockResult.setPage(1);
        mockResult.setSize(20);
        mockResult.setTotalPages(0);

        when(alertReceiverService.list(anyString(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(mockResult);

        // When
        var response = alertController.listAlerts("tenant-001", "active", "P1", "service-001", 1, 20);

        // Then
        assertNotNull(response);
        assertNotNull(response.getData());
        assertEquals(0L, response.getData().getTotal());
    }
}
