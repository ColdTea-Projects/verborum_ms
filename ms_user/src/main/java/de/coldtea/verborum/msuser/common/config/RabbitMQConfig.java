package de.coldtea.verborum.msuser.common.config;

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
 * RabbitMQ wiring for ms_user. Mirrors ms_dictionary's config — see docs/agent/rabbitmq.md for the
 * exchange design and the routing key table.
 * <p>
 * ms_user publishes `user.deleted` (P2-08) and consumes `dictionary.imported` on the
 * `user.dictionary.imported` queue (P2-09). Nothing publishes `dictionary.imported` yet —
 * ms_marketplace does at P4-07 — but the queue is durable and bound, so imports are captured from
 * the moment that service ships rather than being discarded by the topic exchange.
 * <p>
 * All services declare the same exchange; declarations are idempotent, so whichever service starts
 * first creates it.
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "verborum.events";
    public static final String DEAD_LETTER_EXCHANGE = EXCHANGE + ".dlx";
    public static final String DEAD_LETTER_QUEUE = "verborum.dead-letter";

    public static final String ROUTING_KEY_USER_DELETED = "user.deleted";
    public static final String ROUTING_KEY_DICTIONARY_IMPORTED = "dictionary.imported";

    public static final String QUEUE_DICTIONARY_IMPORTED = "user.dictionary.imported";

    @Bean
    public TopicExchange verborumExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    /**
     * ms_user's only consumer queue: marketplace imports that must land in a user's vault.
     * <p>
     * `x-dead-letter-exchange` is all it needs — the DLX is a fanout, so a message that keeps
     * failing arrives in the DLQ whatever its routing key.
     */
    @Bean
    public Queue dictionaryImportedQueue() {
        return QueueBuilder.durable(QUEUE_DICTIONARY_IMPORTED)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .build();
    }

    @Bean
    public Binding dictionaryImportedBinding(Queue dictionaryImportedQueue, TopicExchange verborumExchange) {
        return BindingBuilder
                .bind(dictionaryImportedQueue)
                .to(verborumExchange)
                .with(ROUTING_KEY_DICTIONARY_IMPORTED);
    }

    /**
     * Fanout, not direct: RabbitMQ keeps a message's original routing key when it dead-letters it
     * (e.g. `user.deleted`). A direct DLX would only match a binding under that same key, so the
     * message would be dropped as unroutable instead of landing in the DLQ. Fanout ignores the
     * routing key, so a consumer queue only has to name the DLX to be safe.
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
     * Spring AMQP's enhanced mapper already registers `JavaTimeModule`, but it leaves
     * `WRITE_DATES_AS_TIMESTAMPS` on, which renders an event's `LocalDateTime` as a numeric array
     * (`[2026,7,16,15,17,53,415040500]`). Pinned to ISO-8601 instead — this is a wire contract
     * shared with ms_dictionary, not a local preference: a publisher and a consumer that disagree
     * on the timestamp format break at the boundary.
     * <p>
     * Deliberately not Boot's auto-configured `ObjectMapper` — that one is shared with the web
     * layer, and event serialization should not shift because someone tunes the REST JSON.
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
     * Cross-service deserialization. Jackson2JsonMessageConverter stamps every outgoing message with
     * a `__TypeId__` header holding the publisher's fully-qualified class name, and by default the
     * consumer trusts that header. That cannot work between services: ms_marketplace will publish
     * `de.coldtea.verborum.msmarketplace.common.event.DictionaryImportedEvent`, a class that does
     * not exist here, and the listener would fail on every message — permanently, straight to the
     * DLQ, with a ClassNotFoundException that reads like a broker problem.
     * <p>
     * `INFERRED` precedence makes the @RabbitListener method's own parameter type win instead, so
     * each service deserializes into its own copy of the event and the class names never have to
     * agree — only the JSON field names do. Trusted packages are still restricted, because the
     * header is used when nothing can be inferred.
     * <p>
     * Every consuming service needs this — ms_dictionary must add it at P2-10, where it starts
     * consuming ms_user's `user.deleted`.
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
