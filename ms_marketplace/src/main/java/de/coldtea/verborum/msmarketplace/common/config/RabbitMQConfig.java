package de.coldtea.verborum.msmarketplace.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.JacksonUtils;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ wiring for ms_marketplace. See docs/agent/rabbitmq.md for the exchange design and the
 * routing key table.
 * <p>
 * ms_marketplace keeps its `dictionary_stats` read model current from ms_dictionary's events
 * (P4-03): going public, listed-field updates, and the scheduled `dictionary.snapshot`. The private
 * and deleted consumers arrive at P4-04/P4-05, publishing `dictionary.imported` at P4-07.
 * <p>
 * All services declare the same exchange; declarations are idempotent, so whichever service
 * starts first creates it.
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "verborum.events";
    public static final String DEAD_LETTER_EXCHANGE = EXCHANGE + ".dlx";
    public static final String DEAD_LETTER_QUEUE = "verborum.dead-letter";

    public static final String ROUTING_KEY_DICTIONARY_VISIBILITY_PUBLIC = "dictionary.visibility.public";
    public static final String ROUTING_KEY_DICTIONARY_UPDATED = "dictionary.updated";
    public static final String ROUTING_KEY_DICTIONARY_SNAPSHOT = "dictionary.snapshot";

    public static final String QUEUE_DICTIONARY_VISIBILITY_PUBLIC = "marketplace.dictionary.visibility.public";
    public static final String QUEUE_DICTIONARY_UPDATED = "marketplace.dictionary.updated";
    public static final String QUEUE_DICTIONARY_SNAPSHOT = "marketplace.dictionary.snapshot";

    @Bean
    public TopicExchange verborumExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    // One queue per event, each with `x-dead-letter-exchange` — the DLX is a fanout, so a message
    // that keeps failing reaches the DLQ whatever its routing key. Separate queues mean no ordering
    // between the three event kinds; the consumers do not rely on any — each compares the
    // dictionary's updatedAt against what it holds (rule 4)

    @Bean
    public Queue dictionaryVisibilityPublicQueue() {
        return QueueBuilder.durable(QUEUE_DICTIONARY_VISIBILITY_PUBLIC)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .build();
    }

    @Bean
    public Binding dictionaryVisibilityPublicBinding(Queue dictionaryVisibilityPublicQueue, TopicExchange verborumExchange) {
        return BindingBuilder
                .bind(dictionaryVisibilityPublicQueue)
                .to(verborumExchange)
                .with(ROUTING_KEY_DICTIONARY_VISIBILITY_PUBLIC);
    }

    @Bean
    public Queue dictionaryUpdatedQueue() {
        return QueueBuilder.durable(QUEUE_DICTIONARY_UPDATED)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .build();
    }

    @Bean
    public Binding dictionaryUpdatedBinding(Queue dictionaryUpdatedQueue, TopicExchange verborumExchange) {
        return BindingBuilder
                .bind(dictionaryUpdatedQueue)
                .to(verborumExchange)
                .with(ROUTING_KEY_DICTIONARY_UPDATED);
    }

    @Bean
    public Queue dictionarySnapshotQueue() {
        return QueueBuilder.durable(QUEUE_DICTIONARY_SNAPSHOT)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .build();
    }

    @Bean
    public Binding dictionarySnapshotBinding(Queue dictionarySnapshotQueue, TopicExchange verborumExchange) {
        return BindingBuilder
                .bind(dictionarySnapshotQueue)
                .to(verborumExchange)
                .with(ROUTING_KEY_DICTIONARY_SNAPSHOT);
    }

    /**
     * Fanout, not direct: RabbitMQ keeps a message's original routing key when it dead-letters it.
     * A direct DLX would only match a binding under that same key, so the message would be dropped
     * as unroutable instead of landing in the DLQ. Fanout ignores the routing key, so a consumer
     * queue only has to name the DLX to be safe.
     */
    @Bean
    public FanoutExchange deadLetterExchange() {
        return new FanoutExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue, FanoutExchange deadLetterExchange) {
        return BindingBuilder
                .bind(deadLetterQueue)
                .to(deadLetterExchange);
    }

    /**
     * ISO-8601 timestamps on the wire, matching the other services: the enhanced mapper registers
     * `JavaTimeModule` but leaves `WRITE_DATES_AS_TIMESTAMPS` on. Deliberately not Boot's
     * auto-configured `ObjectMapper`, which is shared with the web layer.
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        ObjectMapper objectMapper = JacksonUtils.enhancedObjectMapper();
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        converter.setJavaTypeMapper(inboundTypeMapper());
        return converter;
    }

    /**
     * Cross-service deserialization. ms_dictionary stamps every message with a `__TypeId__` header
     * naming its own class (`…msdictionary.common.event.DictionaryVisibilityEvent`), which does not
     * exist here — trusting it would fail every message as ClassNotFound, straight to the DLQ.
     * `INFERRED` makes the @RabbitListener parameter type win, so only the JSON field names have to
     * agree. Trusted packages stay restricted, since the header is still used when nothing can be
     * inferred.
     */
    private DefaultJackson2JavaTypeMapper inboundTypeMapper() {
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        typeMapper.setTrustedPackages("de.coldtea.verborum.*");
        return typeMapper;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }
}
