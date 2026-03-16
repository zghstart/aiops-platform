package com.aiops.domain.event;

import com.aiops.repository.entity.Alert;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class AlertReceivedEvent extends ApplicationEvent {

    private final Alert alert;

    public AlertReceivedEvent(Object source, Alert alert) {
        super(source);
        this.alert = alert;
    }
}
