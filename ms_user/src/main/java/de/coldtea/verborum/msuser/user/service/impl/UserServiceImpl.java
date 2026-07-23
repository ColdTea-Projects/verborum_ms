package de.coldtea.verborum.msuser.user.service.impl;

import de.coldtea.verborum.msuser.common.event.KeycloakUserDeletionRequested;
import de.coldtea.verborum.msuser.common.event.OutboundEvent;
import de.coldtea.verborum.msuser.common.event.UserDeletedEvent;
import de.coldtea.verborum.msuser.common.exception.ForbiddenOperationException;
import de.coldtea.verborum.msuser.common.exception.RecordNotFoundException;
import de.coldtea.verborum.msuser.common.mapper.UserMapper;
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

import static de.coldtea.verborum.msuser.common.config.RabbitMQConfig.ROUTING_KEY_USER_DELETED;
import static de.coldtea.verborum.msuser.common.constants.ErrorMessageConstants.NOT_THE_OWNER;
import static de.coldtea.verborum.msuser.common.constants.ErrorMessageConstants.USER_WAS_NOT_FOUND_ID;

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
        userRepository.findById(userRequestDTO.getUserId())
                .ifPresent(existing -> requireOwnProfile(existing, callerKeycloakId));

        User savedUser = userRepository.saveAndFlush(userMapper.toUser(userRequestDTO));
        return userMapper.toUserResponseDTO(savedUser);
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
