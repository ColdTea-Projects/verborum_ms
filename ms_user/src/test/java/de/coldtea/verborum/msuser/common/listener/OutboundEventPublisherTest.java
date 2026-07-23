package de.coldtea.verborum.msuser.common.listener;

import de.coldtea.verborum.msuser.common.event.OutboundEvent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static de.coldtea.verborum.msuser.common.config.RabbitMQConfig.EXCHANGE;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OutboundEventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private OutboundEventPublisher outboundEventPublisher;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void publish_SendsToTheExchangeWithTheRoutingKey() {
        // Arrange
        Object payload = new Object();

        // Act
        outboundEventPublisher.publish(new OutboundEvent("user.deleted", payload));

        // Assert
        verify(rabbitTemplate).convertAndSend(EXCHANGE, "user.deleted", payload);
    }

    @Test
    void publish_SwallowsSendFailures() {
        // Arrange — this runs after the transaction has committed, so throwing could not undo the
        // write and would only turn successful work into a 500. The ERROR log is the record.
        doThrow(new AmqpException("broker down"))
                .when(rabbitTemplate).convertAndSend(eq(EXCHANGE), eq("user.deleted"), any(Object.class));

        // Act & Assert
        assertDoesNotThrow(() -> outboundEventPublisher.publish(new OutboundEvent("user.deleted", new Object())));
    }
}
