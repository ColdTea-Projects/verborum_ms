package de.coldtea.verborum.msnotification.common.config;

import de.coldtea.verborum.msnotification.common.event.DictionaryChangedEvent;
import de.coldtea.verborum.msnotification.common.event.DictionaryImportedEvent;
import de.coldtea.verborum.msnotification.common.event.UserDeletedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConverter;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The wire contract with the three publishers, exercised through the real converter: each publisher
 * stamps `__TypeId__` with a class that does not exist here, and the listener parameter type must win.
 */
class RabbitMQConfigTest {

    private final MessageConverter converter = new RabbitMQConfig().jsonMessageConverter();

    private Object consume(String json, Class<?> listenerParameterType, String publisherClass) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setInferredArgumentType(listenerParameterType);
        properties.setHeader("__TypeId__", publisherClass);
        return converter.fromMessage(new Message(json.getBytes(StandardCharsets.UTF_8), properties));
    }

    @Test
    void dictionaryChanged_FromMsDictionary_Deserializes() {
        // Act — exactly the JSON ms_dictionary publishes, with a device id
        DictionaryChangedEvent event = (DictionaryChangedEvent) consume("""
                {"keycloakId":"kc-owner","dictionaryId":"d1","deviceId":"3f2c1b8e-6a54-4f0e-9d2a-1b7c5e8a9f10",
                 "eventTimestamp":"2026-10-14T21:30:00.123456+02:00"}""",
                DictionaryChangedEvent.class, "de.coldtea.verborum.msdictionary.common.event.DictionaryChangedEvent");

        // Assert
        assertEquals("kc-owner", event.getKeycloakId());
        assertEquals("d1", event.getDictionaryId());
        assertEquals("3f2c1b8e-6a54-4f0e-9d2a-1b7c5e8a9f10", event.getDeviceId());
    }

    @Test
    void dictionaryChanged_WithoutDeviceId_HasANullDevice() {
        // Act — a client that sent no header, or a server-made change
        DictionaryChangedEvent event = (DictionaryChangedEvent) consume("""
                {"keycloakId":"kc-owner","dictionaryId":"d1","deviceId":null,"eventTimestamp":"2026-10-14T21:30:00+02:00"}""",
                DictionaryChangedEvent.class, "de.coldtea.verborum.msdictionary.common.event.DictionaryChangedEvent");

        // Assert
        assertNull(event.getDeviceId());
    }

    @Test
    void dictionaryImported_FromMsMarketplace_CarriesThePublisher() {
        // Act
        DictionaryImportedEvent event = (DictionaryImportedEvent) consume("""
                {"dictionaryId":"d1","keycloakId":"kc-importer","publisherId":"kc-publisher",
                 "eventTimestamp":"2026-10-14T21:30:00+02:00"}""",
                DictionaryImportedEvent.class, "de.coldtea.verborum.msmarketplace.common.event.DictionaryImportedEvent");

        // Assert
        assertEquals("kc-publisher", event.getPublisherId());
        assertEquals("kc-importer", event.getKeycloakId());
    }

    @Test
    void userDeleted_FromMsUser_Deserializes() {
        // Act — with a field this service has never heard of: a publisher may add one without breaking us
        UserDeletedEvent event = (UserDeletedEvent) consume("""
                {"userId":"u-1","keycloakId":"kc-1","somethingNew":true,"eventTimestamp":"2026-10-14T21:30:00+02:00"}""",
                UserDeletedEvent.class, "de.coldtea.verborum.msuser.common.event.UserDeletedEvent");

        // Assert
        assertEquals("kc-1", event.getKeycloakId());
    }
}
