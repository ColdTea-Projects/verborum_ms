package de.coldtea.verborum.msuser.user.service.impl;

import de.coldtea.verborum.msuser.common.event.UserDeletedEvent;
import de.coldtea.verborum.msuser.common.exception.ForbiddenOperationException;
import de.coldtea.verborum.msuser.common.exception.RecordNotFoundException;
import de.coldtea.verborum.msuser.common.mapper.UserMapper;
import de.coldtea.verborum.msuser.user.dto.UserRequestDTO;
import de.coldtea.verborum.msuser.user.dto.UserResponseDTO;
import de.coldtea.verborum.msuser.user.entity.User;
import de.coldtea.verborum.msuser.user.repository.UserRepository;
import de.coldtea.verborum.msuser.user.service.KeycloakUserService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.Optional;

import static de.coldtea.verborum.msuser.common.config.RabbitMQConfig.EXCHANGE;
import static de.coldtea.verborum.msuser.common.config.RabbitMQConfig.ROUTING_KEY_USER_DELETED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class UserServiceImplTest {

    /** The JWT subject of the caller — in ms_user that is the profile's keycloakId (P3-05). */
    private static final String CALLER_KC_ID = "kc-1";

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private KeycloakUserService keycloakUserService;

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
        verify(rabbitTemplate).convertAndSend(eq(EXCHANGE), eq(ROUTING_KEY_USER_DELETED), any(UserDeletedEvent.class));
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
        ArgumentCaptor<UserDeletedEvent> eventCaptor = ArgumentCaptor.forClass(UserDeletedEvent.class);
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), eventCaptor.capture());
        assertEquals(userId, eventCaptor.getValue().getUserId());
        assertEquals("kc-1", eventCaptor.getValue().getKeycloakId());
    }

    @Test
    void deleteUser_UnknownUser_PublishesNothing() {
        // Arrange
        String userId = "1";
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // Act
        userService.deleteUser(userId, CALLER_KC_ID);

        // Assert — still a no-op 200, but no deletion is announced and no identity is touched
        verify(userRepository).deleteById(userId);
        verifyNoInteractions(rabbitTemplate);
        verifyNoInteractions(keycloakUserService);
    }

    @Test
    void deleteUser_DeletesTheKeycloakIdentity() {
        // Arrange — without this the account outlives the profile and can simply re-register
        String userId = "1";
        User user = User.builder().userId(userId).keycloakId("kc-1").build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // Act
        userService.deleteUser(userId, CALLER_KC_ID);

        // Assert — after the row is gone and the event is out
        InOrder inOrder = inOrder(userRepository, rabbitTemplate, keycloakUserService);
        inOrder.verify(userRepository).deleteById(userId);
        inOrder.verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(UserDeletedEvent.class));
        inOrder.verify(keycloakUserService).deleteUser("kc-1");
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
        verifyNoInteractions(rabbitTemplate, keycloakUserService);
    }

    private static UserRequestDTO requestDTO(String keycloakId) {
        UserRequestDTO requestDTO = new UserRequestDTO();
        requestDTO.setKeycloakId(keycloakId);
        return requestDTO;
    }
}
