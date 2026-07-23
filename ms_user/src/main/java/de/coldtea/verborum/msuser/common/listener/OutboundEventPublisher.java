package de.coldtea.verborum.msuser.common.listener;

import de.coldtea.verborum.msuser.common.event.OutboundEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static de.coldtea.verborum.msuser.common.config.RabbitMQConfig.EXCHANGE;
import static de.coldtea.verborum.msuser.common.constants.ErrorMessageConstants.EVENT_PUBLISH_FAILED;

/**
 * The one place this service touches `RabbitTemplate`. Services raise an {@link OutboundEvent}; this
 * sends it once the transaction has committed.
 * <p>
 * Why after commit (rule 1 in docs/agent/rabbitmq.md): publishing inside the transaction means a
 * rollback leaves an event announcing something that never happened. For `user.deleted` that is not
 * a cosmetic problem — ms_dictionary reacts by deleting that user's dictionaries and words, so a
 * phantom event destroys live data with no way back. After commit, the worst case is the opposite
 * and far milder: the event is lost and some rows are orphaned, which a re-publish or a
 * reconciliation sweep fixes.
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
