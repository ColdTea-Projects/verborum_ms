package de.coldtea.verborum.msdictionary.common.config;

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
 * RabbitMQ wiring for ms_dictionary. See docs/agent/rabbitmq.md for the exchange design
 * and the routing key table.
 * <p>
 * ms_dictionary publishes the dictionary and word events, and consumes `user.deleted` on the
 * `dictionary.user.deleted` queue (roadmap P2-10) to cascade-delete a removed user's data.
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
    public static final String ROUTING_KEY_DICTIONARY_VISIBILITY_PRIVATE = "dictionary.visibility.private";
    public static final String ROUTING_KEY_DICTIONARY_DELETED = "dictionary.deleted";
    public static final String ROUTING_KEY_WORD_CREATED = "word.created";
    public static final String ROUTING_KEY_USER_DELETED = "user.deleted";

    public static final String QUEUE_USER_DELETED = "dictionary.user.deleted";

    @Bean
    public TopicExchange verborumExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    /**
     * ms_dictionary's only consumer queue: cascade-delete a removed user's dictionaries and words.
     * <p>
     * `x-dead-letter-exchange` is all it needs — the DLX is a fanout, so a message that keeps
     * failing reaches the DLQ whatever its routing key.
     */
    @Bean
    public Queue userDeletedQueue() {
        return QueueBuilder.durable(QUEUE_USER_DELETED)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .build();
    }

    @Bean
    public Binding userDeletedBinding(Queue userDeletedQueue, TopicExchange verborumExchange) {
        return BindingBuilder
                .bind(userDeletedQueue)
                .to(verborumExchange)
                .with(ROUTING_KEY_USER_DELETED);
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
     * (`[2026,7,16,15,17,53,415040500]`). Pinned to ISO-8601 instead: the wire format is readable
     * in the Management UI and portable to any consumer that is not a Java service using this
     * same converter.
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
     * Cross-service deserialization, mirroring ms_user's config (added there at P2-09).
     * <p>
     * Jackson2JsonMessageConverter stamps every outgoing message with a `__TypeId__` header holding
     * the publisher's fully-qualified class name, and by default the consumer trusts it. ms_user
     * publishes `de.coldtea.verborum.msuser.common.event.UserDeletedEvent` — a class that does not
     * exist here — so without this, every `user.deleted` message would fail with ClassNotFound,
     * permanently, straight to the DLQ, with an error that reads like a broker fault.
     * <p>
     * `INFERRED` precedence makes the @RabbitListener method's own parameter type win, so each
     * service deserializes into its own copy of the event and only the JSON field names have to
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
