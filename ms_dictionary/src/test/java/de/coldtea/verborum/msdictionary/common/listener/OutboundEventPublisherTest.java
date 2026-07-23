package de.coldtea.verborum.msdictionary.common.listener;

import de.coldtea.verborum.msdictionary.common.event.OutboundEvent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static de.coldtea.verborum.msdictionary.common.config.RabbitMQConfig.EXCHANGE;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Mirrors ms_user's test of the same class. The transactional guarantee itself — that a rollback
 * publishes nothing — is proven once, in ms_user's UserDeletedAfterCommitTest, since both services
 * use the identical listener.
 */
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
        outboundEventPublisher.publish(new OutboundEvent("dictionary.deleted", payload));

        // Assert
        verify(rabbitTemplate).convertAndSend(EXCHANGE, "dictionary.deleted", payload);
    }

    @Test
    void publish_SwallowsSendFailures() {
        // Arrange — the transaction has already committed; throwing would turn successful work into
        // a 500 and could not undo the write anyway
        doThrow(new AmqpException("broker down"))
                .when(rabbitTemplate).convertAndSend(eq(EXCHANGE), eq("dictionary.deleted"), any(Object.class));

        // Act & Assert
        assertDoesNotThrow(() ->
                outboundEventPublisher.publish(new OutboundEvent("dictionary.deleted", new Object())));
    }
}
