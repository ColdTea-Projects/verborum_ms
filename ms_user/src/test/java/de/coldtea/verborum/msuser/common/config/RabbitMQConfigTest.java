package de.coldtea.verborum.msuser.common.config;

import de.coldtea.verborum.msuser.common.event.DictionaryImportedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConverter;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The wire contract of `dictionary.imported`: ms_marketplace may add fields (P4N-06 added
 * `publisherId`) without breaking this consumer, which keeps its own copy of the event.
 */
class RabbitMQConfigTest {

    private final MessageConverter converter = new RabbitMQConfig().jsonMessageConverter();

    private DictionaryImportedEvent consume(String json) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        // What a @RabbitListener parameter provides; INFERRED precedence makes it win over __TypeId__
        properties.setInferredArgumentType(DictionaryImportedEvent.class);
        // the publisher's class name, which does not exist in this service
        properties.setHeader("__TypeId__", "de.coldtea.verborum.msmarketplace.common.event.DictionaryImportedEvent");
        return (DictionaryImportedEvent) converter.fromMessage(new Message(json.getBytes(StandardCharsets.UTF_8), properties));
    }

    @Test
    void dictionaryImported_WithTheAdditivePublisherId_StillDeserializes() {
        // Act
        DictionaryImportedEvent event = consume("""
                {"dictionaryId":"d1","keycloakId":"kc-importer","publisherId":"kc-publisher",
                 "eventTimestamp":"2026-10-14T21:30:00+02:00"}""");

        // Assert — the unknown field is ignored, the known ones arrive
        assertEquals("d1", event.getDictionaryId());
        assertEquals("kc-importer", event.getKeycloakId());
    }

    @Test
    void dictionaryImported_WithoutPublisherId_StillDeserializes() {
        // Act — an event from before P4N-06
        DictionaryImportedEvent event = consume("""
                {"dictionaryId":"d1","keycloakId":"kc-importer","eventTimestamp":"2026-10-14T21:30:00+02:00"}""");

        // Assert
        assertEquals("kc-importer", event.getKeycloakId());
    }
}
