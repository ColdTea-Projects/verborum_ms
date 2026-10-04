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
import de.coldtea.verborum.msuser.user.service.UserService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Optional;

import static de.coldtea.verborum.msuser.common.config.RabbitMQConfig.ROUTING_KEY_USER_DELETED;
import static de.coldtea.verborum.msuser.common.config.RabbitMQConfig.ROUTING_KEY_USER_PROFILE_UPDATED;
import static de.coldtea.verborum.msuser.common.constants.ErrorMessageConstants.AGREEMENT_VERSION_REQUIRED;
import static de.coldtea.verborum.msuser.common.constants.ErrorMessageConstants.DISPLAY_NAME_REQUIRED_WHILE_AGREEMENT_ACCEPTED;
import static de.coldtea.verborum.msuser.common.constants.ErrorMessageConstants.EMAIL_ALREADY_IN_USE;
import static de.coldtea.verborum.msuser.common.constants.ErrorMessageConstants.NOT_THE_OWNER;
import static de.coldtea.verborum.msuser.common.constants.ErrorMessageConstants.PROFILE_ALREADY_EXISTS;
import static de.coldtea.verborum.msuser.common.constants.ErrorMessageConstants.USER_WAS_NOT_FOUND_ID;
import static de.coldtea.verborum.msuser.common.constants.ErrorMessageConstants.USER_WAS_NOT_FOUND_KEYCLOAK_ID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    private final UserMapper userMapper;

    // Not RabbitTemplate: the service raises application events and the after-commit listeners do
    // the sending (rules 1 and 7 in docs/agent/rabbitmq.md)
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    @Override
    public UserResponseDTO saveUser(UserRequestDTO userRequestDTO, String callerKeycloakId) {
        // P3-05: a profile may only be created or updated for the token's own subject. Without this
        // an authenticated caller could claim someone else's keycloakId — and since keycloak_id is
        // the cross-service join key, that would hand them the other user's dictionaries too
        if (!callerKeycloakId.equals(userRequestDTO.getKeycloakId())) {
            throw new ForbiddenOperationException(NOT_THE_OWNER);
        }

        // Backs both POST and PUT — a client-generated userId that already exists is an update,
        // otherwise an insert (mirrors DictionaryServiceImpl.saveDictionary). The client supplies
        // the userId, so an existing row must be checked too, or a PUT could overwrite a stranger's
        // profile with the caller's own keycloakId
        Optional<User> existing = userRepository.findById(userRequestDTO.getUserId());
        existing.ifPresent(user -> requireOwnProfile(user, callerKeycloakId));

        requireNoConflictingProfile(userRequestDTO, existing.isPresent());

        // Copied out now, not read from `existing` after the save: saveAndFlush merges the new values
        // onto that same managed instance, so afterwards it would always equal what was just saved
        ProfileState previous = existing.map(ProfileState::of).orElse(ProfileState.NONE);

        User user = userMapper.toUser(userRequestDTO);

        // Never clear by omission (P4-14 rule 3): a full-profile PUT from a client that does not send
        // displayName keeps the stored one. "" still removes it — subject to rule 2 below
        user.setDisplayName(userRequestDTO.getDisplayName() == null
                ? previous.displayName()
                : normalizeDisplayName(userRequestDTO.getDisplayName()));

        // The agreement is not part of this request at all; it changes only through profile-info
        user.setMarketplaceAgreementAccepted(previous.agreementAccepted());
        user.setMarketplaceAgreementVersion(existing.map(User::getMarketplaceAgreementVersion).orElse(null));
        user.setMarketplaceAgreementAcceptedAt(existing.map(User::getMarketplaceAgreementAcceptedAt).orElse(null));

        requireValidAgreementState(user);

        User savedUser = userRepository.saveAndFlush(user);

        publishProfileChange(savedUser, previous);

        return userMapper.toUserResponseDTO(savedUser);
    }

    /**
     * The two unique columns, checked up front so a duplicate is a 409 that says what to do rather than
     * a constraint violation (which used to surface as a 500). An account has one profile: a client
     * that lost its `userId` (e.g. after a reinstall) must load it with `GET /users/me`, not create a
     * second. The constraint handler still catches the race between this check and the insert.
     */
    private void requireNoConflictingProfile(UserRequestDTO userRequestDTO, boolean isUpdate) {
        if (!isUpdate && userRepository.findByKeycloakId(userRequestDTO.getKeycloakId()).isPresent()) {
            throw new ProfileConflictException(PROFILE_ALREADY_EXISTS);
        }

        userRepository.findByEmail(userRequestDTO.getEmail())
                .filter(other -> !other.getUserId().equals(userRequestDTO.getUserId()))
                .ifPresent(other -> {
                    throw new ProfileConflictException(EMAIL_ALREADY_IN_USE);
                });
    }

    /**
     * The caller's own profile (P4-14), found by the token subject — a client needs nothing stored to
     * load its profile page after login. No profile yet is a 404; the client creates one with
     * `POST /users/`.
     */
    @Override
    public ProfileResponseDTO getMyProfile(String callerKeycloakId) {
        return userMapper.toProfileResponseDTO(findByKeycloakId(callerKeycloakId));
    }

    /**
     * Partial update of the caller's display name and marketplace agreement (P4-14). A null field is
     * left as it is. Accepting needs a version in this request; re-accepting a new version, or
     * accepting after a withdrawal, stamps a new acceptance time. Withdrawing only clears the flag —
     * the version and time stay as the record of the last acceptance.
     */
    @Transactional
    @Override
    public ProfileResponseDTO updateProfileInfo(ProfileInfoRequestDTO profileInfo, String callerKeycloakId) {
        User user = findByKeycloakId(callerKeycloakId);
        ProfileState previous = ProfileState.of(user);

        if (profileInfo.getDisplayName() != null) {
            user.setDisplayName(normalizeDisplayName(profileInfo.getDisplayName()));
        }

        if (profileInfo.getMarketplaceAgreementAccepted() != null) {
            applyAgreement(user, previous, profileInfo.getMarketplaceAgreementAccepted(),
                    normalizeVersion(profileInfo.getMarketplaceAgreementVersion()));
        }

        requireValidAgreementState(user);

        User savedUser = userRepository.saveAndFlush(user);

        publishProfileChange(savedUser, previous);

        return userMapper.toProfileResponseDTO(savedUser);
    }

    private static void applyAgreement(User user, ProfileState previous, boolean accepted, String version) {
        if (!accepted) {
            user.setMarketplaceAgreementAccepted(false);
            return;
        }

        // Rule 1: accepting means accepting a specific terms text — the client must say which. Already
        // accepted and no version sent: nothing changes
        if (version == null) {
            if (previous.agreementAccepted()) {
                return;
            }
            throw new InvalidProfileException(AGREEMENT_VERSION_REQUIRED);
        }

        boolean newAcceptance = !previous.agreementAccepted() || !version.equals(user.getMarketplaceAgreementVersion());
        user.setMarketplaceAgreementAccepted(true);
        user.setMarketplaceAgreementVersion(version);
        if (newAcceptance) {
            user.setMarketplaceAgreementAcceptedAt(OffsetDateTime.now());
        }
    }

    /**
     * Rules 1 and 2 (P4-14) as one invariant on the resulting profile: accepted terms need a display
     * name. Checked on the outcome rather than on each field, so one request may withdraw and remove
     * the name together, but cannot remove the name while staying on the marketplace.
     */
    private static void requireValidAgreementState(User user) {
        if (Boolean.TRUE.equals(user.getMarketplaceAgreementAccepted()) && user.getDisplayName() == null) {
            throw new InvalidProfileException(DISPLAY_NAME_REQUIRED_WHILE_AGREEMENT_ACCEPTED);
        }
    }

    /**
     * Announces a change ms_marketplace cares about (P4-13, P4-14): the display name, which listings
     * show and search, or the agreement, which decides whether the user's listings are shown at all.
     * Only on an actual change — a re-save of the same values, or a new profile with neither a name nor
     * an acceptance, has nothing to announce: the marketplace treats an unknown publisher as hidden.
     */
    private void publishProfileChange(User user, ProfileState previous) {
        if (ProfileState.of(user).equals(previous)) {
            return;
        }

        eventPublisher.publishEvent(new OutboundEvent(
                ROUTING_KEY_USER_PROFILE_UPDATED,
                UserProfileUpdatedEvent.builder()
                        .keycloakId(user.getKeycloakId())
                        .displayName(user.getDisplayName())
                        .marketplaceAgreementAccepted(Boolean.TRUE.equals(user.getMarketplaceAgreementAccepted()))
                        // The ordering key (rule 4): something changed, so the row was dirty and
                        // @UpdateTimestamp moved this forward
                        .updatedAt(user.getUpdatedAt())
                        .eventTimestamp(OffsetDateTime.now())
                        .build()));
    }

    private User findByKeycloakId(String keycloakId) {
        return userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new RecordNotFoundException(USER_WAS_NOT_FOUND_KEYCLOAK_ID + keycloakId));
    }

    /** Trimmed; blank means "no name" and is stored as null, so "   " can never count as a name. */
    private static String normalizeDisplayName(String displayName) {
        return displayName.isBlank() ? null : displayName.trim();
    }

    private static String normalizeVersion(String version) {
        return version == null || version.isBlank() ? null : version.trim();
    }

    /** What ms_marketplace knows about a user — the before-image the change event compares against. */
    private record ProfileState(String displayName, boolean agreementAccepted) {
        static final ProfileState NONE = new ProfileState(null, false);

        static ProfileState of(User user) {
            return new ProfileState(user.getDisplayName(), Boolean.TRUE.equals(user.getMarketplaceAgreementAccepted()));
        }
    }

    @Override
    public UserResponseDTO getUserById(String userId, String callerKeycloakId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RecordNotFoundException(USER_WAS_NOT_FOUND_ID + userId));
        requireOwnProfile(user, callerKeycloakId);
        return userMapper.toUserResponseDTO(user);
    }

    /**
     * ms_user is the one service where the JWT subject is not the primary key: it is the profile's
     * `keycloakId`. Ownership is therefore a comparison against that column, never against `userId`.
     */
    private static void requireOwnProfile(User user, String callerKeycloakId) {
        if (!callerKeycloakId.equals(user.getKeycloakId())) {
            throw new ForbiddenOperationException(NOT_THE_OWNER);
        }
    }

    @Transactional
    @Override
    public void deleteUser(String userId, String callerKeycloakId) {
        // Read before deleting: the event carries keycloakId, and a user that is not there must not
        // announce a deletion that never happened. deleteById is a silent no-op on a missing row in
        // Spring Data JPA 3.x, so this stays a 200 either way (same shape as
        // DictionaryServiceImpl.deleteDictionary).
        User user = userRepository.findById(userId).orElse(null);

        // Deleting somebody else's account is refused; an unknown id stays a silent 200 rather than
        // revealing which ids exist (P3-05)
        if (user != null) {
            requireOwnProfile(user, callerKeycloakId);
        }

        // user_stats and vault_entries are removed by their DB FK ON DELETE CASCADE — this event is
        // what lets the OTHER services (ms_dictionary P2-10, ms_marketplace) drop their own rows.
        userRepository.deleteById(userId);

        if (user == null) {
            return;
        }

        // Both of these happen AFTER this transaction commits (rules 1 and 7 in rabbitmq.md).
        // Raising them here only queues them; OutboundEventPublisher and KeycloakUserDeletionListener
        // act once the delete is durable.
        //
        // This is not a cosmetic ordering fix. ms_dictionary consumes user.deleted by deleting that
        // user's dictionaries and words, and a Keycloak identity cannot be recreated with the same
        // subject — so under the old "publish as the last statement inside the transaction" pattern,
        // any rollback after this point destroyed live data for a user who still existed.
        eventPublisher.publishEvent(new OutboundEvent(
                ROUTING_KEY_USER_DELETED,
                UserDeletedEvent.builder()
                        .userId(user.getUserId())
                        .keycloakId(user.getKeycloakId())
                        .eventTimestamp(OffsetDateTime.now())
                        .build()));

        eventPublisher.publishEvent(new KeycloakUserDeletionRequested(user.getKeycloakId()));
    }
}
