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
import de.coldtea.verborum.msuser.user.service.UserService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

import static de.coldtea.verborum.msuser.common.config.RabbitMQConfig.EXCHANGE;
import static de.coldtea.verborum.msuser.common.config.RabbitMQConfig.ROUTING_KEY_USER_DELETED;
import static de.coldtea.verborum.msuser.common.constants.ErrorMessageConstants.NOT_THE_OWNER;
import static de.coldtea.verborum.msuser.common.constants.ErrorMessageConstants.USER_WAS_NOT_FOUND_ID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    private final UserMapper userMapper;

    private final RabbitTemplate rabbitTemplate;

    private final KeycloakUserService keycloakUserService;

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

        // Published inside the transaction, as the last statement, exactly like ms_dictionary's
        // publishers: RabbitTemplate is not transactional, so anything throwing after the send
        // would roll the delete back while the event stays published. Keep it last.
        // The known flip side — the send happens before commit, so a consumer can beat it — is
        // tracked in roadmap P1-03/P4-03, where publishing moves to @TransactionalEventListener
        // (AFTER_COMMIT) for every publisher at once.
        rabbitTemplate.convertAndSend(
                EXCHANGE,
                ROUTING_KEY_USER_DELETED,
                UserDeletedEvent.builder()
                        .userId(user.getUserId())
                        .keycloakId(user.getKeycloakId())
                        .eventTimestamp(OffsetDateTime.now())
                        .build()
        );

        // Last, and non-throwing by contract (P3-04): without this the Keycloak account outlives the
        // profile, so the person can still log in and — since sign-up is hosted — simply re-register.
        // It runs after the publish because a failure here must not roll back a deletion that has
        // already been announced to the other services.
        keycloakUserService.deleteUser(user.getKeycloakId());
    }
}
