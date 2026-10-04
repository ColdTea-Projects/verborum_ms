package de.coldtea.verborum.msuser.user.service.impl;

import de.coldtea.verborum.msuser.common.event.KeycloakUserDeletionRequested;
import de.coldtea.verborum.msuser.common.event.OutboundEvent;
import de.coldtea.verborum.msuser.common.event.UserDeletedEvent;
import de.coldtea.verborum.msuser.common.event.UserProfileUpdatedEvent;
import de.coldtea.verborum.msuser.common.exception.ForbiddenOperationException;
import de.coldtea.verborum.msuser.common.exception.RecordNotFoundException;
import de.coldtea.verborum.msuser.common.mapper.UserMapper;
import de.coldtea.verborum.msuser.user.dto.UserRequestDTO;
import de.coldtea.verborum.msuser.user.dto.UserResponseDTO;
import de.coldtea.verborum.msuser.user.entity.User;
import de.coldtea.verborum.msuser.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEventPublisher;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static de.coldtea.verborum.msuser.common.config.RabbitMQConfig.ROUTING_KEY_USER_DELETED;
import static de.coldtea.verborum.msuser.common.config.RabbitMQConfig.ROUTING_KEY_USER_PROFILE_UPDATED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class UserServiceImplTest {

    /** The JWT subject of the caller — in ms_user that is the profile's keycloakId (P3-05). */
    private static final String CALLER_KC_ID = "kc-1";

    private static final OffsetDateTime UPDATED_AT = OffsetDateTime.of(2026, 10, 4, 12, 0, 0, 0, ZoneOffset.UTC);

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    // The service raises application events; the after-commit listeners do the sending. The send
    // itself is covered by OutboundEventPublisherTest and UserDeletedAfterCommitTest.
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void saveUser_Success() {
        // Arrange
        UserRequestDTO requestDTO = requestDTO(CALLER_KC_ID);
        User user = new User();
        UserResponseDTO responseDTO = new UserResponseDTO();

        when(userMapper.toUser(requestDTO)).thenReturn(user);
        when(userRepository.saveAndFlush(user)).thenReturn(user);
        when(userMapper.toUserResponseDTO(user)).thenReturn(responseDTO);

        // Act
        UserResponseDTO result = userService.saveUser(requestDTO, CALLER_KC_ID);

        // Assert
        assertEquals(responseDTO, result);
        verify(userMapper).toUser(requestDTO);
        verify(userRepository).saveAndFlush(user);
        verify(userMapper).toUserResponseDTO(user);
    }

    @Test
    void saveUser_Failure() {
        // Arrange
        UserRequestDTO requestDTO = requestDTO(CALLER_KC_ID);
        User user = new User();

        when(userMapper.toUser(requestDTO)).thenReturn(user);
        when(userRepository.saveAndFlush(user)).thenThrow(new RuntimeException("Unable to save user"));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> userService.saveUser(requestDTO, CALLER_KC_ID));
        verify(userMapper).toUser(requestDTO);
        verify(userRepository).saveAndFlush(user);
        verifyNoMoreInteractions(userMapper);
    }

    @Test
    void getUserById_Success() {
        // Arrange
        String userId = "1";
        User user = User.builder().userId(userId).keycloakId(CALLER_KC_ID).build();
        UserResponseDTO responseDTO = new UserResponseDTO();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userMapper.toUserResponseDTO(user)).thenReturn(responseDTO);

        // Act
        UserResponseDTO result = userService.getUserById(userId, CALLER_KC_ID);

        // Assert
        assertEquals(responseDTO, result);
        verify(userRepository).findById(userId);
        verify(userMapper).toUserResponseDTO(user);
    }

    @Test
    void getUserById_NotFound() {
        // Arrange
        String userId = "1";
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(RecordNotFoundException.class, () -> userService.getUserById(userId, CALLER_KC_ID));
        verifyNoInteractions(userMapper);
    }

    @Test
    void deleteUser_Success() {
        // Arrange
        String userId = "1";
        User user = User.builder().userId(userId).keycloakId("kc-1").build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // Act
        userService.deleteUser(userId, CALLER_KC_ID);

        // Assert
        verify(userRepository).deleteById(userId);
        assertEquals(ROUTING_KEY_USER_DELETED, capturedOutboundEvent().routingKey());
    }

    @Test
    void deleteUser_PublishesBothIds() {
        // Arrange — consumers in other services match on keycloakId (their fk_user_id is the JWT
        // subject), so the event is useless to them without it
        String userId = "1";
        User user = User.builder().userId(userId).keycloakId("kc-1").build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // Act
        userService.deleteUser(userId, CALLER_KC_ID);

        // Assert
        UserDeletedEvent payload = (UserDeletedEvent) capturedOutboundEvent().payload();
        assertEquals(userId, payload.getUserId());
        assertEquals("kc-1", payload.getKeycloakId());
    }

    @Test
    void deleteUser_UnknownUser_PublishesNothing() {
        // Arrange
        String userId = "1";
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // Act
        userService.deleteUser(userId, CALLER_KC_ID);

        // Assert — still a no-op 200, but nothing is announced and no identity is touched
        verify(userRepository).deleteById(userId);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void deleteUser_RequestsTheKeycloakIdentityDeletion() {
        // Arrange — without this the account outlives the profile and can simply re-register. It is
        // now requested as an event so it happens after commit, not inside the transaction: a
        // Keycloak account cannot be recreated with the same subject, so a rollback after the call
        // would strand a profile whose owner can never log in
        String userId = "1";
        User user = User.builder().userId(userId).keycloakId("kc-1").build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // Act
        userService.deleteUser(userId, CALLER_KC_ID);

        // Assert
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(2)).publishEvent(captor.capture());
        assertEquals(new KeycloakUserDeletionRequested("kc-1"),
                captor.getAllValues().stream()
                        .filter(KeycloakUserDeletionRequested.class::isInstance)
                        .findFirst()
                        .orElseThrow());
    }

    private OutboundEvent capturedOutboundEvent() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(captor.capture());
        return captor.getAllValues().stream()
                .filter(OutboundEvent.class::isInstance)
                .map(OutboundEvent.class::cast)
                .findFirst()
                .orElseThrow();
    }

    @Test
    void saveUser_ClaimingAnotherSubject_IsForbidden() {
        // Arrange — keycloak_id is the cross-service join key, so claiming someone else's would
        // hand the caller that user's dictionaries too
        UserRequestDTO requestDTO = requestDTO("kc-someone-else");

        // Act & Assert
        assertThrows(ForbiddenOperationException.class, () -> userService.saveUser(requestDTO, CALLER_KC_ID));
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void saveUser_OverwritingAnotherUsersProfile_IsForbidden() {
        // Arrange — the client supplies userId, so a PUT could otherwise take over an existing row
        UserRequestDTO requestDTO = requestDTO(CALLER_KC_ID);
        requestDTO.setUserId("1");

        when(userRepository.findById("1"))
                .thenReturn(Optional.of(User.builder().userId("1").keycloakId("kc-someone-else").build()));

        // Act & Assert
        assertThrows(ForbiddenOperationException.class, () -> userService.saveUser(requestDTO, CALLER_KC_ID));
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void getUserById_AnotherUsersProfile_IsForbidden() {
        // Arrange
        String userId = "1";
        when(userRepository.findById(userId))
                .thenReturn(Optional.of(User.builder().userId(userId).keycloakId("kc-someone-else").build()));

        // Act & Assert
        assertThrows(ForbiddenOperationException.class, () -> userService.getUserById(userId, CALLER_KC_ID));
        verifyNoInteractions(userMapper);
    }

    @Test
    void deleteUser_AnotherUsersProfile_IsForbidden() {
        // Arrange
        String userId = "1";
        when(userRepository.findById(userId))
                .thenReturn(Optional.of(User.builder().userId(userId).keycloakId("kc-someone-else").build()));

        // Act & Assert
        assertThrows(ForbiddenOperationException.class, () -> userService.deleteUser(userId, CALLER_KC_ID));
        verify(userRepository, never()).deleteById(anyString());
        verifyNoInteractions(eventPublisher);
    }

    private static UserRequestDTO requestDTO(String keycloakId) {
        UserRequestDTO requestDTO = new UserRequestDTO();
        requestDTO.setKeycloakId(keycloakId);
        return requestDTO;
    }

    // ---- user.profile.updated (P4-13) ----

    @Test
    void saveUser_NewProfileWithDisplayName_PublishesProfileUpdated() {
        // Arrange
        UserRequestDTO requestDTO = requestDTO(CALLER_KC_ID);
        requestDTO.setUserId("user-1");
        User saved = user("Anna Bauer", UPDATED_AT);
        when(userRepository.findById("user-1")).thenReturn(Optional.empty());
        when(userMapper.toUser(requestDTO)).thenReturn(saved);
        when(userRepository.saveAndFlush(saved)).thenReturn(saved);

        // Act
        userService.saveUser(requestDTO, CALLER_KC_ID);

        // Assert
        ArgumentCaptor<OutboundEvent> captor = ArgumentCaptor.forClass(OutboundEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertEquals(ROUTING_KEY_USER_PROFILE_UPDATED, captor.getValue().routingKey());
        UserProfileUpdatedEvent event = (UserProfileUpdatedEvent) captor.getValue().payload();
        assertEquals(CALLER_KC_ID, event.getKeycloakId());
        assertEquals("Anna Bauer", event.getDisplayName());
        assertEquals(UPDATED_AT, event.getUpdatedAt());
    }

    @Test
    void saveUser_NewProfileWithoutDisplayName_PublishesNothing() {
        // Arrange — nothing to announce: the marketplace treats an unknown publisher as nameless
        UserRequestDTO requestDTO = requestDTO(CALLER_KC_ID);
        requestDTO.setUserId("user-1");
        User saved = user(null, UPDATED_AT);
        when(userRepository.findById("user-1")).thenReturn(Optional.empty());
        when(userMapper.toUser(requestDTO)).thenReturn(saved);
        when(userRepository.saveAndFlush(saved)).thenReturn(saved);

        // Act
        userService.saveUser(requestDTO, CALLER_KC_ID);

        // Assert
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void saveUser_DisplayNameChanged_PublishesTheNewName() {
        // Arrange — saveAndFlush merges onto the managed instance, so the old name must be read first;
        // simulate that by changing the existing object during the save
        UserRequestDTO requestDTO = requestDTO(CALLER_KC_ID);
        requestDTO.setUserId("user-1");
        User existing = user("Anna", UPDATED_AT.minusDays(1));
        User incoming = user("Anna Bauer", null);
        when(userRepository.findById("user-1")).thenReturn(Optional.of(existing));
        when(userMapper.toUser(requestDTO)).thenReturn(incoming);
        when(userRepository.saveAndFlush(incoming)).thenAnswer(invocation -> {
            existing.setDisplayName("Anna Bauer");
            existing.setUpdatedAt(UPDATED_AT);
            return existing;
        });

        // Act
        userService.saveUser(requestDTO, CALLER_KC_ID);

        // Assert
        ArgumentCaptor<OutboundEvent> captor = ArgumentCaptor.forClass(OutboundEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        UserProfileUpdatedEvent event = (UserProfileUpdatedEvent) captor.getValue().payload();
        assertEquals("Anna Bauer", event.getDisplayName());
        assertEquals(UPDATED_AT, event.getUpdatedAt());
    }

    @Test
    void saveUser_DisplayNameCleared_PublishesNull() {
        // Arrange — the marketplace hides a publisher's listings once the name is gone
        UserRequestDTO requestDTO = requestDTO(CALLER_KC_ID);
        requestDTO.setUserId("user-1");
        User existing = user("Anna", UPDATED_AT.minusDays(1));
        User saved = user(null, UPDATED_AT);
        when(userRepository.findById("user-1")).thenReturn(Optional.of(existing));
        when(userMapper.toUser(requestDTO)).thenReturn(saved);
        when(userRepository.saveAndFlush(saved)).thenReturn(saved);

        // Act
        userService.saveUser(requestDTO, CALLER_KC_ID);

        // Assert
        ArgumentCaptor<OutboundEvent> captor = ArgumentCaptor.forClass(OutboundEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertNull(((UserProfileUpdatedEvent) captor.getValue().payload()).getDisplayName());
    }

    @Test
    void saveUser_SameDisplayNameResaved_PublishesNothing() {
        // Arrange — e.g. an email change; the marketplace has nothing to learn
        UserRequestDTO requestDTO = requestDTO(CALLER_KC_ID);
        requestDTO.setUserId("user-1");
        User existing = user("Anna", UPDATED_AT.minusDays(1));
        User saved = user("Anna", UPDATED_AT);
        when(userRepository.findById("user-1")).thenReturn(Optional.of(existing));
        when(userMapper.toUser(requestDTO)).thenReturn(saved);
        when(userRepository.saveAndFlush(saved)).thenReturn(saved);

        // Act
        userService.saveUser(requestDTO, CALLER_KC_ID);

        // Assert
        verifyNoInteractions(eventPublisher);
    }

    private static User user(String displayName, OffsetDateTime updatedAt) {
        User user = new User();
        user.setUserId("user-1");
        user.setKeycloakId(CALLER_KC_ID);
        user.setDisplayName(displayName);
        user.setUpdatedAt(updatedAt);
        return user;
    }
}
