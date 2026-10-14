package de.coldtea.verborum.msnotification.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.JacksonUtils;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ wiring for ms_notification (P4N-07). See docs/agent/rabbitmq.md for the exchange design and
 * the routing key table.
 * <p>
 * Consumes only: `dictionary.changed` (ms_dictionary → silent SYNC to the user's other devices),
 * `dictionary.imported` and `dictionary.rated` (ms_marketplace → silent REFRESH to the publisher), and
 * `user.deleted` (ms_user → drop the user's device tokens). It publishes nothing, so there is no
 * RabbitTemplate and no OutboundEventPublisher here.
 * <p>
 * All services declare the same exchange; declarations are idempotent, so whichever service starts
 * first creates it.
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "verborum.events";
    public static final String DEAD_LETTER_EXCHANGE = EXCHANGE + ".dlx";
    public static final String DEAD_LETTER_QUEUE = "verborum.dead-letter";

    public static final String ROUTING_KEY_DICTIONARY_CHANGED = "dictionary.changed";
    public static final String ROUTING_KEY_DICTIONARY_IMPORTED = "dictionary.imported";
    public static final String ROUTING_KEY_DICTIONARY_RATED = "dictionary.rated";
    public static final String ROUTING_KEY_USER_DELETED = "user.deleted";

    public static final String QUEUE_DICTIONARY_CHANGED = "notification.dictionary.changed";
    public static final String QUEUE_DICTIONARY_IMPORTED = "notification.dictionary.imported";
    public static final String QUEUE_DICTIONARY_RATED = "notification.dictionary.rated";
    public static final String QUEUE_USER_DELETED = "notification.user.deleted";

    @Bean
    public TopicExchange verborumExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    // One durable queue per event, each with `x-dead-letter-exchange`: the DLX is a fanout, so a message
    // that keeps failing reaches the DLQ whatever its routing key. The queues are independent — no
    // handler relies on an order between the event kinds

    @Bean
    public Queue dictionaryChangedQueue() {
        return QueueBuilder.durable(QUEUE_DICTIONARY_CHANGED)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .build();
    }

    @Bean
    public Binding dictionaryChangedBinding(Queue dictionaryChangedQueue, TopicExchange verborumExchange) {
        return BindingBuilder.bind(dictionaryChangedQueue).to(verborumExchange).with(ROUTING_KEY_DICTIONARY_CHANGED);
    }

    @Bean
    public Queue dictionaryImportedQueue() {
        return QueueBuilder.durable(QUEUE_DICTIONARY_IMPORTED)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .build();
    }

    @Bean
    public Binding dictionaryImportedBinding(Queue dictionaryImportedQueue, TopicExchange verborumExchange) {
        return BindingBuilder.bind(dictionaryImportedQueue).to(verborumExchange).with(ROUTING_KEY_DICTIONARY_IMPORTED);
    }

    @Bean
    public Queue dictionaryRatedQueue() {
        return QueueBuilder.durable(QUEUE_DICTIONARY_RATED)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .build();
    }

    @Bean
    public Binding dictionaryRatedBinding(Queue dictionaryRatedQueue, TopicExchange verborumExchange) {
        return BindingBuilder.bind(dictionaryRatedQueue).to(verborumExchange).with(ROUTING_KEY_DICTIONARY_RATED);
    }

    @Bean
    public Queue userDeletedQueue() {
        return QueueBuilder.durable(QUEUE_USER_DELETED)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .build();
    }

    @Bean
    public Binding userDeletedBinding(Queue userDeletedQueue, TopicExchange verborumExchange) {
        return BindingBuilder.bind(userDeletedQueue).to(verborumExchange).with(ROUTING_KEY_USER_DELETED);
    }

    /**
     * Fanout, not direct: RabbitMQ keeps a message's original routing key when it dead-letters it. A
     * direct DLX would only match a binding under that same key, so the message would be dropped as
     * unroutable instead of landing in the DLQ.
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
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange);
    }

    /**
     * ISO-8601 timestamps on the wire, matching the other services. Deliberately not Boot's
     * auto-configured `ObjectMapper`, which is shared with the web layer. `enhancedObjectMapper()` also
     * ignores unknown properties, so a publisher may add a field without breaking this consumer.
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
     * Cross-service deserialization. Every publisher stamps a `__TypeId__` header naming its own class,
     * which does not exist here — trusting it would fail every message as ClassNotFound, straight to the
     * DLQ. `INFERRED` makes the @RabbitListener parameter type win, so only the JSON field names have to
     * agree. Trusted packages stay restricted, since the header is still used when nothing can be inferred.
     */
    private DefaultJackson2JavaTypeMapper inboundTypeMapper() {
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        typeMapper.setTrustedPackages("de.coldtea.verborum.*");
        return typeMapper;
    }
}
