package de.coldtea.verborum.msuser.user.service.impl;

import de.coldtea.verborum.msuser.common.event.KeycloakUserDeletionRequested;
import de.coldtea.verborum.msuser.common.event.OutboundEvent;
import de.coldtea.verborum.msuser.common.event.UserDeletedEvent;
import de.coldtea.verborum.msuser.common.event.UserProfileUpdatedEvent;
import de.coldtea.verborum.msuser.common.exception.ForbiddenOperationException;
import de.coldtea.verborum.msuser.common.exception.InvalidProfileException;
import de.coldtea.verborum.msuser.common.exception.ProfileConflictException;
import de.coldtea.verborum.msuser.common.exception.RecordNotFoundException;
import de.coldtea.verborum.msuser.common.mapper.UserMapper;
import de.coldtea.verborum.msuser.user.dto.ProfileInfoRequestDTO;
import de.coldtea.verborum.msuser.user.dto.ProfileResponseDTO;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class UserServiceImplTest {

    /** The JWT subject of the caller — in ms_user that is the profile's keycloakId (P3-05). */
    private static final String CALLER_KC_ID = "kc-1";

    private static final OffsetDateTime UPDATED_AT = OffsetDateTime.of(2026, 10, 4, 12, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime ACCEPTED_AT = OffsetDateTime.of(2026, 10, 1, 9, 0, 0, 0, ZoneOffset.UTC);

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
        requestDTO.setDisplayName("Anna Bauer");
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
        requestDTO.setDisplayName("Anna Bauer");
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
        // Arrange — "" removes the name (null would keep it, P4-14 rule 3); allowed while the agreement
        // is not accepted. The marketplace hides a publisher's listings once the name is gone
        UserRequestDTO requestDTO = requestDTO(CALLER_KC_ID);
        requestDTO.setUserId("user-1");
        requestDTO.setDisplayName("");
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
        requestDTO.setDisplayName("Anna");
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

    // ---- P4-14: PUT /users/ never clears by omission, and keeps the agreement ----

    @Test
    void saveUser_DisplayNameOmitted_KeepsTheStoredName() {
        // Arrange — an older client that does not send displayName must not wipe it (rule 3)
        UserRequestDTO requestDTO = requestDTO(CALLER_KC_ID);
        requestDTO.setUserId("user-1");
        User existing = user("Anna", UPDATED_AT);
        User incoming = user(null, null);
        when(userRepository.findById("user-1")).thenReturn(Optional.of(existing));
        when(userMapper.toUser(requestDTO)).thenReturn(incoming);
        when(userRepository.saveAndFlush(incoming)).thenReturn(incoming);

        // Act
        userService.saveUser(requestDTO, CALLER_KC_ID);

        // Assert
        assertEquals("Anna", incoming.getDisplayName());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void saveUser_KeepsTheStoredAgreement() {
        // Arrange — the agreement is not in this request at all; the mapper leaves it unset
        UserRequestDTO requestDTO = requestDTO(CALLER_KC_ID);
        requestDTO.setUserId("user-1");
        requestDTO.setDisplayName("Anna");
        User existing = acceptedUser("Anna", "v1");
        User incoming = user("Anna", null);
        incoming.setMarketplaceAgreementAccepted(null);
        when(userRepository.findById("user-1")).thenReturn(Optional.of(existing));
        when(userMapper.toUser(requestDTO)).thenReturn(incoming);
        when(userRepository.saveAndFlush(incoming)).thenReturn(incoming);

        // Act
        userService.saveUser(requestDTO, CALLER_KC_ID);

        // Assert
        assertTrue(incoming.getMarketplaceAgreementAccepted());
        assertEquals("v1", incoming.getMarketplaceAgreementVersion());
        assertEquals(ACCEPTED_AT, incoming.getMarketplaceAgreementAcceptedAt());
    }

    @Test
    void saveUser_NewProfile_StartsWithTheAgreementNotAccepted() {
        // Arrange — NOT NULL column, and Hibernate ignores its default
        UserRequestDTO requestDTO = requestDTO(CALLER_KC_ID);
        requestDTO.setUserId("user-1");
        User incoming = user(null, null);
        incoming.setMarketplaceAgreementAccepted(null);
        when(userRepository.findById("user-1")).thenReturn(Optional.empty());
        when(userMapper.toUser(requestDTO)).thenReturn(incoming);
        when(userRepository.saveAndFlush(incoming)).thenReturn(incoming);

        // Act
        userService.saveUser(requestDTO, CALLER_KC_ID);

        // Assert
        assertEquals(false, incoming.getMarketplaceAgreementAccepted());
    }

    @Test
    void saveUser_ClearingTheNameWhileAgreementAccepted_Is400() {
        // Arrange — rule 2 holds on the full-profile PUT too
        UserRequestDTO requestDTO = requestDTO(CALLER_KC_ID);
        requestDTO.setUserId("user-1");
        requestDTO.setDisplayName("  ");
        when(userRepository.findById("user-1")).thenReturn(Optional.of(acceptedUser("Anna", "v1")));
        when(userMapper.toUser(requestDTO)).thenReturn(user(null, null));

        // Act & Assert
        assertThrows(InvalidProfileException.class, () -> userService.saveUser(requestDTO, CALLER_KC_ID));
        verify(userRepository, never()).saveAndFlush(any());
        verifyNoInteractions(eventPublisher);
    }

    // ---- P4-14: GET /users/me ----

    @Test
    void getMyProfile_FoundByTheTokenSubject() {
        // Arrange
        User user = acceptedUser("Anna", "v1");
        ProfileResponseDTO profile = ProfileResponseDTO.builder().id("user-1").build();
        when(userRepository.findByKeycloakId(CALLER_KC_ID)).thenReturn(Optional.of(user));
        when(userMapper.toProfileResponseDTO(user)).thenReturn(profile);

        // Act
        ProfileResponseDTO result = userService.getMyProfile(CALLER_KC_ID);

        // Assert
        assertEquals(profile, result);
    }

    @Test
    void getMyProfile_NoProfileYet_Is404() {
        // Arrange — the client then creates it with POST /users/
        when(userRepository.findByKeycloakId(CALLER_KC_ID)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(RecordNotFoundException.class, () -> userService.getMyProfile(CALLER_KC_ID));
    }

    // ---- P4-14: PUT /users/me/profile-info ----

    @Test
    void updateProfileInfo_Accept_RecordsVersionAndTimeAndPublishes() {
        // Arrange
        User user = givenMyProfile(user("Anna", UPDATED_AT));

        // Act
        userService.updateProfileInfo(profileInfo(null, true, " v1 "), CALLER_KC_ID);

        // Assert
        assertTrue(user.getMarketplaceAgreementAccepted());
        assertEquals("v1", user.getMarketplaceAgreementVersion());
        assertNotNull(user.getMarketplaceAgreementAcceptedAt());
        UserProfileUpdatedEvent event = capturedProfileEvent();
        assertTrue(event.getMarketplaceAgreementAccepted());
        assertEquals("Anna", event.getDisplayName());
    }

    @Test
    void updateProfileInfo_AcceptWithoutVersion_Is400() {
        // Arrange — rule 1: accepting means accepting a specific terms text
        givenMyProfile(user("Anna", UPDATED_AT));

        // Act & Assert
        assertThrows(InvalidProfileException.class,
                () -> userService.updateProfileInfo(profileInfo(null, true, " "), CALLER_KC_ID));
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateProfileInfo_AcceptWithoutAName_Is400() {
        // Arrange — rule 1: the user has no name and sends none
        givenMyProfile(user(null, UPDATED_AT));

        // Act & Assert
        assertThrows(InvalidProfileException.class,
                () -> userService.updateProfileInfo(profileInfo(null, true, "v1"), CALLER_KC_ID));
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateProfileInfo_AcceptAndNameInOneRequest_Succeeds() {
        // Arrange — the onboarding screen sends both at once
        User user = givenMyProfile(user(null, UPDATED_AT));

        // Act
        userService.updateProfileInfo(profileInfo("Anna Bauer", true, "v1"), CALLER_KC_ID);

        // Assert
        assertEquals("Anna Bauer", user.getDisplayName());
        assertTrue(user.getMarketplaceAgreementAccepted());
    }

    @Test
    void updateProfileInfo_ClearNameWhileAccepted_Is400() {
        // Arrange — rule 2: withdraw first
        User user = givenMyProfile(acceptedUser("Anna", "v1"));

        // Act & Assert
        assertThrows(InvalidProfileException.class,
                () -> userService.updateProfileInfo(profileInfo("", null, null), CALLER_KC_ID));
        verify(userRepository, never()).saveAndFlush(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void updateProfileInfo_WithdrawAndClearNameTogether_Succeeds() {
        // Arrange — the rule is checked on the outcome, so one request may do both
        User user = givenMyProfile(acceptedUser("Anna", "v1"));

        // Act
        userService.updateProfileInfo(profileInfo("", false, null), CALLER_KC_ID);

        // Assert
        assertNull(user.getDisplayName());
        assertFalse(user.getMarketplaceAgreementAccepted());
    }

    @Test
    void updateProfileInfo_Withdraw_KeepsTheRecordOfTheLastAcceptance() {
        // Arrange
        User user = givenMyProfile(acceptedUser("Anna", "v1"));

        // Act
        userService.updateProfileInfo(profileInfo(null, false, null), CALLER_KC_ID);

        // Assert — only the flag goes false; the marketplace hides the listings on the event
        assertFalse(user.getMarketplaceAgreementAccepted());
        assertEquals("v1", user.getMarketplaceAgreementVersion());
        assertEquals(ACCEPTED_AT, user.getMarketplaceAgreementAcceptedAt());
        assertFalse(capturedProfileEvent().getMarketplaceAgreementAccepted());
    }

    @Test
    void updateProfileInfo_AlreadyAcceptedNoVersionSent_ChangesNothing() {
        // Arrange
        User user = givenMyProfile(acceptedUser("Anna", "v1"));

        // Act
        userService.updateProfileInfo(profileInfo(null, true, null), CALLER_KC_ID);

        // Assert
        assertEquals("v1", user.getMarketplaceAgreementVersion());
        assertEquals(ACCEPTED_AT, user.getMarketplaceAgreementAcceptedAt());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void updateProfileInfo_AcceptANewVersion_StampsANewTimeWithoutAnEvent() {
        // Arrange — still on the marketplace, so nothing changes for ms_marketplace
        User user = givenMyProfile(acceptedUser("Anna", "v1"));

        // Act
        userService.updateProfileInfo(profileInfo(null, true, "v2"), CALLER_KC_ID);

        // Assert
        assertEquals("v2", user.getMarketplaceAgreementVersion());
        assertTrue(user.getMarketplaceAgreementAcceptedAt().isAfter(ACCEPTED_AT));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void updateProfileInfo_Rename_PublishesTheNewName() {
        // Arrange
        givenMyProfile(acceptedUser("Anna", "v1"));

        // Act
        userService.updateProfileInfo(profileInfo("  Anna Schmidt ", null, null), CALLER_KC_ID);

        // Assert
        assertEquals("Anna Schmidt", capturedProfileEvent().getDisplayName());
    }

    // ---- SEC-10: reserved display names ----

    @Test
    void updateProfileInfo_ReservedName_Is400() {
        // Arrange
        givenMyProfile(acceptedUser("Anna", "v1"));

        // Act & Assert
        assertThrows(InvalidProfileException.class,
                () -> userService.updateProfileInfo(profileInfo("Verborum Team", null, null), CALLER_KC_ID));
        verify(userRepository, never()).saveAndFlush(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void updateProfileInfo_JoiningWithAReservedName_Is400() {
        // Arrange — the Join screen sends the name and the agreement together
        givenMyProfile(user(null, UPDATED_AT));

        // Act & Assert
        assertThrows(InvalidProfileException.class,
                () -> userService.updateProfileInfo(profileInfo("4dm1n", true, "v1"), CALLER_KC_ID));
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateProfileInfo_NameUsedByAnotherUser_Succeeds() {
        // Arrange — names are deliberately not unique; nothing is looked up
        givenMyProfile(acceptedUser("Anna", "v1"));

        // Act
        userService.updateProfileInfo(profileInfo("Anna Bauer", null, null), CALLER_KC_ID);

        // Assert
        assertEquals("Anna Bauer", capturedProfileEvent().getDisplayName());
        verify(userRepository, never()).findAll();
    }

    @Test
    void saveUser_ReservedName_Is400() {
        // Arrange
        UserRequestDTO requestDTO = requestDTO(CALLER_KC_ID);
        requestDTO.setDisplayName("Official Admin");
        when(userMapper.toUser(requestDTO)).thenReturn(user("Official Admin", null));

        // Act & Assert
        assertThrows(InvalidProfileException.class, () -> userService.saveUser(requestDTO, CALLER_KC_ID));
        verify(userRepository, never()).saveAndFlush(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void saveUser_StoredNamePredatingTheRule_IsKept() {
        // Arrange — a full-profile PUT re-sending a name stored before SEC-10 must not start failing
        UserRequestDTO requestDTO = requestDTO(CALLER_KC_ID);
        requestDTO.setUserId("user-1");
        requestDTO.setDisplayName("Support Desk");
        User existing = user("Support Desk", UPDATED_AT);
        User incoming = user("Support Desk", null);
        when(userRepository.findById("user-1")).thenReturn(Optional.of(existing));
        when(userMapper.toUser(requestDTO)).thenReturn(incoming);
        when(userRepository.saveAndFlush(incoming)).thenReturn(incoming);

        // Act
        userService.saveUser(requestDTO, CALLER_KC_ID);

        // Assert
        verify(userRepository).saveAndFlush(incoming);
    }

    @Test
    void updateProfileInfo_EmptyBody_ChangesNothing() {
        // Arrange — absent fields are left as they are
        User user = givenMyProfile(acceptedUser("Anna", "v1"));

        // Act
        userService.updateProfileInfo(new ProfileInfoRequestDTO(), CALLER_KC_ID);

        // Assert
        assertEquals("Anna", user.getDisplayName());
        assertTrue(user.getMarketplaceAgreementAccepted());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void updateProfileInfo_NoProfile_Is404() {
        // Arrange
        when(userRepository.findByKeycloakId(CALLER_KC_ID)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(RecordNotFoundException.class,
                () -> userService.updateProfileInfo(profileInfo("Anna", null, null), CALLER_KC_ID));
    }

    private User givenMyProfile(User user) {
        when(userRepository.findByKeycloakId(CALLER_KC_ID)).thenReturn(Optional.of(user));
        when(userRepository.saveAndFlush(user)).thenReturn(user);
        when(userMapper.toProfileResponseDTO(user)).thenReturn(ProfileResponseDTO.builder().id("user-1").build());
        return user;
    }

    private UserProfileUpdatedEvent capturedProfileEvent() {
        ArgumentCaptor<OutboundEvent> captor = ArgumentCaptor.forClass(OutboundEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertEquals(ROUTING_KEY_USER_PROFILE_UPDATED, captor.getValue().routingKey());
        return (UserProfileUpdatedEvent) captor.getValue().payload();
    }

    private static ProfileInfoRequestDTO profileInfo(String displayName, Boolean accepted, String version) {
        return ProfileInfoRequestDTO.builder()
                .displayName(displayName)
                .marketplaceAgreementAccepted(accepted)
                .marketplaceAgreementVersion(version)
                .build();
    }

    private static User acceptedUser(String displayName, String version) {
        User user = user(displayName, UPDATED_AT);
        user.setMarketplaceAgreementAccepted(true);
        user.setMarketplaceAgreementVersion(version);
        user.setMarketplaceAgreementAcceptedAt(ACCEPTED_AT);
        return user;
    }

    private static User user(String displayName, OffsetDateTime updatedAt) {
        User user = new User();
        user.setUserId("user-1");
        user.setKeycloakId(CALLER_KC_ID);
        user.setDisplayName(displayName);
        user.setMarketplaceAgreementAccepted(false);
        user.setUpdatedAt(updatedAt);
        return user;
    }

    // ---- duplicate profile data → 409 ----

    @Test
    void saveUser_SecondProfileForTheSameAccount_Is409() {
        // Arrange — e.g. a reinstall that lost its userId and generated a new one
        UserRequestDTO requestDTO = requestDTO(CALLER_KC_ID);
        requestDTO.setUserId("new-user-id");
        when(userRepository.findById("new-user-id")).thenReturn(Optional.empty());
        when(userRepository.findByKeycloakId(CALLER_KC_ID)).thenReturn(Optional.of(user("Anna", UPDATED_AT)));

        // Act & Assert
        assertThrows(ProfileConflictException.class, () -> userService.saveUser(requestDTO, CALLER_KC_ID));
        verify(userRepository, never()).saveAndFlush(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void saveUser_EmailUsedByAnotherProfile_Is409() {
        // Arrange
        UserRequestDTO requestDTO = requestDTO(CALLER_KC_ID);
        requestDTO.setUserId("user-1");
        requestDTO.setEmail("taken@example.com");
        User other = user("Someone", UPDATED_AT);
        other.setUserId("someone-else");
        when(userRepository.findById("user-1")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("taken@example.com")).thenReturn(Optional.of(other));

        // Act & Assert
        assertThrows(ProfileConflictException.class, () -> userService.saveUser(requestDTO, CALLER_KC_ID));
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void saveUser_UpdateKeepingItsOwnEmail_IsNoConflict() {
        // Arrange — the email belongs to the very profile being saved
        UserRequestDTO requestDTO = requestDTO(CALLER_KC_ID);
        requestDTO.setUserId("user-1");
        requestDTO.setEmail("anna@example.com");
        User existing = user("Anna", UPDATED_AT);
        when(userRepository.findById("user-1")).thenReturn(Optional.of(existing));
        when(userRepository.findByEmail("anna@example.com")).thenReturn(Optional.of(existing));
        User incoming = user("Anna", UPDATED_AT);
        when(userMapper.toUser(requestDTO)).thenReturn(incoming);
        when(userRepository.saveAndFlush(incoming)).thenReturn(incoming);

        // Act
        userService.saveUser(requestDTO, CALLER_KC_ID);

        // Assert
        verify(userRepository).saveAndFlush(incoming);
    }
}
