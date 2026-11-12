package com.liberty.queueboardbe.simulation;

import com.liberty.queueboardbe.model.payload.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** The outbound email/push gateway, simulated: nothing leaves the process. Tests spy on it to inject failures. */
@Component
public class NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(NotificationSender.class);

    public record Message(UUID jobId, long studentId, Channel channel, String subject) {
    }

    public void send(Message message) {
        log.trace("Simulated {} to student {}: {}", message.channel(), message.studentId(), message.subject());
    }
}
