# RabbitMQConfig

One per service, in `common/config/`. Routing keys and queue names are `public static final`
constants, declared in **both** the publishing and the consuming service.

```java
public static final String EXCHANGE = "verborum.events";
public static final String DEAD_LETTER_EXCHANGE = EXCHANGE + ".dlx";
public static final String DEAD_LETTER_QUEUE = "verborum.dead-letter";

public static final String ROUTING_KEY_DICTIONARY_VISIBILITY_PUBLIC = "dictionary.visibility.public";
public static final String ROUTING_KEY_USER_DELETED = "user.deleted";
public static final String QUEUE_USER_DELETED = "dictionary.user.deleted";
```

## Exchange, queue, binding

```java
@Bean
public TopicExchange verborumExchange() {
    return new TopicExchange(EXCHANGE, true, false);      // durable
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
```

All services declare the same exchange. Declarations are idempotent, so whichever service starts
first creates it.

## The dead-letter exchange is a fanout — on purpose

```java
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
    return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange);   // fanout: no routing key
}
```

RabbitMQ keeps a message's **original** routing key when it dead-letters it — a failed
`user.deleted` still carries `user.deleted`. A direct dead-letter exchange would only catch it if
the queue also set `x-dead-letter-routing-key`; forget that on one queue and its failed messages
are dropped as unroutable, silently. A fanout ignores the routing key, so naming the exchange in
`x-dead-letter-exchange` is always sufficient.

Do not convert it to a direct exchange without also adding `x-dead-letter-routing-key` to every
consumer queue.

Retry before dead-lettering is configured in properties: enabled, 1000 ms initial interval, 3
attempts, multiplier 2.0.

## The JSON converter — two load-bearing settings

```java
@Bean
public MessageConverter jsonMessageConverter() {
    ObjectMapper objectMapper = JacksonUtils.enhancedObjectMapper();
    objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
    converter.setJavaTypeMapper(inboundTypeMapper());
    return converter;
}

private DefaultJackson2JavaTypeMapper inboundTypeMapper() {
    DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
    typeMapper.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
    typeMapper.setTrustedPackages("de.coldtea.verborum.*");
    return typeMapper;
}

@Bean
public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                     MessageConverter jsonMessageConverter) {
    RabbitTemplate template = new RabbitTemplate(connectionFactory);
    template.setMessageConverter(jsonMessageConverter);
    return template;
}
```

**`WRITE_DATES_AS_TIMESTAMPS` disabled.** `JacksonUtils.enhancedObjectMapper()` registers
`JavaTimeModule` but leaves that feature on, which renders a timestamp as a numeric array like
`[2026,7,16,15,17,53,415040500]`. Pinned to ISO-8601 instead: readable in the Management UI, and
portable to a consumer that is not a Java service using this same converter. The timestamp format
is part of the wire contract — a publisher and a consumer that disagree on it fail at the boundary.

**`INFERRED` type precedence is mandatory for any consumer.** `Jackson2JsonMessageConverter` stamps
every outgoing message with a `__TypeId__` header holding the publisher's fully-qualified class
name, and by default the consumer trusts that header. Between services that never works: the
publisher's `…msuser.common.event.UserDeletedEvent` does not exist in the consumer, so every
message fails with ClassNotFound — permanently, straight to the dead-letter queue, with an error
that reads like a broker fault. `INFERRED` makes the `@RabbitListener` method's own parameter type
win, so each service deserializes into its own copy of the event. Trusted packages stay restricted,
since the header is still used when nothing can be inferred.

**Not Boot's auto-configured `ObjectMapper`.** That one is shared with the web layer, and event
serialization should not shift because someone tunes the REST JSON.

`JacksonUtils` lives in `org.springframework.amqp.support.converter`, not `...amqp.support`.
