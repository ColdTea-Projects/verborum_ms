package de.coldtea.verborum.msdictionary.common.listener;

import de.coldtea.verborum.msdictionary.common.event.OutboundEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static de.coldtea.verborum.msdictionary.common.config.RabbitMQConfig.EXCHANGE;
import static de.coldtea.verborum.msdictionary.common.constants.ErrorMessageConstants.EVENT_PUBLISH_FAILED;

/**
 * The one place this service touches `RabbitTemplate`. Services raise an {@link OutboundEvent}; this
 * sends it once the transaction has committed (rule 1 in docs/agent/rabbitmq.md).
 * <p>
 * The stakes are lower here than in ms_user — nothing consumes these events destructively yet — but
 * they rise the moment ms_marketplace exists: a phantom `dictionary.visibility.public` would create
 * a listing for a dictionary that was never saved, and a phantom `dictionary.deleted` would remove
 * one that still exists. The pattern is applied now so all publishers behave identically before a
 * third service starts consuming them.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboundEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    /**
     * `fallbackExecution = true` on purpose: by default a `@TransactionalEventListener` does nothing
     * when there is no active transaction, so any non-transactional publisher path would drop its
     * events silently. With the fallback it sends immediately instead.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void publish(OutboundEvent event) {
        try {
            rabbitTemplate.convertAndSend(EXCHANGE, event.routingKey(), event.payload());
        } catch (Exception e) {
            // The transaction has already committed — throwing now cannot undo it and would only
            // surface as a 500 for work that actually succeeded. This log is the only record that
            // the event never went out.
            log.error(EVENT_PUBLISH_FAILED, event.routingKey(), e);
        }
    }
}
