package de.coldtea.verborum.msdictionary.common.utils;

import de.coldtea.verborum.msdictionary.common.event.DictionaryChangedEvent;
import de.coldtea.verborum.msdictionary.common.event.OutboundEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static de.coldtea.verborum.msdictionary.common.config.RabbitMQConfig.ROUTING_KEY_DICTIONARY_CHANGED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;

class DictionaryChangeNotifierTest {

    private static final String DEVICE = "3f2c1b8e-6a54-4f0e-9d2a-1b7c5e8a9f10";

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private DictionaryChangeNotifier notifier;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        notifier = new DictionaryChangeNotifier(eventPublisher);
    }

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    private DictionaryChangedEvent published() {
        ArgumentCaptor<OutboundEvent> captor = ArgumentCaptor.forClass(OutboundEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertEquals(ROUTING_KEY_DICTIONARY_CHANGED, captor.getValue().routingKey());
        return (DictionaryChangedEvent) captor.getValue().payload();
    }

    private static void givenRequestWithHeader(String value) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (value != null) {
            request.addHeader(DictionaryChangeNotifier.DEVICE_ID_HEADER, value);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    void dictionaryChanged_CarriesTheOwnerTheDictionaryAndTheWritingDevice() {
        // Arrange
        givenRequestWithHeader(DEVICE);

        // Act
        notifier.dictionaryChanged("kc-owner", "dict-1");

        // Assert
        DictionaryChangedEvent event = published();
        assertEquals("kc-owner", event.getKeycloakId());
        assertEquals("dict-1", event.getDictionaryId());
        assertEquals(DEVICE, event.getDeviceId());
        assertNotNull(event.getEventTimestamp());
    }

    @Test
    void dictionaryChanged_NoHeader_HasNoDeviceId() {
        // Arrange
        givenRequestWithHeader(null);

        // Act
        notifier.dictionaryChanged("kc-owner", "dict-1");

        // Assert
        assertNull(published().getDeviceId());
    }

    @Test
    void dictionaryChanged_HeaderIsNotAUuid_IsDroppedNotForwarded() {
        // Arrange — the header is client input and is never trusted as free text
        givenRequestWithHeader("<script>alert(1)</script>");

        // Act
        notifier.dictionaryChanged("kc-owner", "dict-1");

        // Assert
        assertNull(published().getDeviceId());
    }

    @Test
    void dictionaryChanged_OutsideARequest_HasNoDeviceId() {
        // Arrange — a RabbitMQ listener thread: no request is bound

        // Act
        notifier.dictionaryChanged("kc-owner", "dict-1");

        // Assert
        assertNull(published().getDeviceId());
    }
}
