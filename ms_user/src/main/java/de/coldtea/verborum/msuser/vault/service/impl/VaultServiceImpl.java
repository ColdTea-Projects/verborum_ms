package de.coldtea.verborum.msuser.vault.service.impl;

import de.coldtea.verborum.msuser.common.exception.ForbiddenOperationException;
import de.coldtea.verborum.msuser.common.exception.RecordNotFoundException;
import de.coldtea.verborum.msuser.common.mapper.VaultEntryMapper;
import de.coldtea.verborum.msuser.user.entity.User;
import de.coldtea.verborum.msuser.user.repository.UserRepository;
import de.coldtea.verborum.msuser.vault.dto.VaultEntryRequestDTO;
import de.coldtea.verborum.msuser.vault.dto.VaultEntryResponseDTO;
import de.coldtea.verborum.msuser.vault.entity.VaultEntry;
import de.coldtea.verborum.msuser.vault.repository.VaultEntryRepository;
import de.coldtea.verborum.msuser.vault.service.VaultService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

import static de.coldtea.verborum.msuser.common.constants.ErrorMessageConstants.NOT_THE_OWNER;
import static de.coldtea.verborum.msuser.common.constants.ErrorMessageConstants.USER_WAS_NOT_FOUND_ID;
import static de.coldtea.verborum.msuser.common.constants.ErrorMessageConstants.USER_WAS_NOT_FOUND_KEYCLOAK_ID;

@Service
@RequiredArgsConstructor
public class VaultServiceImpl implements VaultService {

    private final VaultEntryRepository vaultEntryRepository;

    private final UserRepository userRepository;

    private final VaultEntryMapper vaultEntryMapper;

    @Override
    public List<VaultEntryResponseDTO> getVaultEntriesByUser(String userId, String callerKeycloakId) {
        requireOwnProfile(userId, callerKeycloakId);

        // No user-existence check beyond the ownership guard: an unknown user simply has an empty
        // vault, mirroring ms_dictionary's GET /dictionaries/{userId}.
        return vaultEntryRepository.findByUserId(userId)
                .stream()
                .map(vaultEntryMapper::toVaultEntryResponseDTO)
                .toList();
    }

    @Transactional
    @Override
    public VaultEntryResponseDTO addVaultEntry(String userId, VaultEntryRequestDTO vaultEntryRequestDTO, String callerKeycloakId) {
        requireOwnProfile(userId, callerKeycloakId);
        return addVaultEntry(userId, vaultEntryRequestDTO);
    }

    /**
     * Ownership-free overload for the two callers that are not a logged-in user: the ownership guard
     * above, and importDictionary (driven by a marketplace event, where the actor is another
     * service).
     */
    private VaultEntryResponseDTO addVaultEntry(String userId, VaultEntryRequestDTO vaultEntryRequestDTO) {
        // Idempotent by design: a vault is a set, enforced by UNIQUE (fk_user_id, fk_dictionary_id).
        // Re-importing the same dictionary returns the existing entry instead of failing, which is
        // also the behaviour P2-09 needs when RabbitMQ redelivers a dictionary.imported event.
        return vaultEntryRepository.findByUserIdAndDictionaryId(userId, vaultEntryRequestDTO.getDictionaryId())
                .map(vaultEntryMapper::toVaultEntryResponseDTO)
                .orElseGet(() -> createVaultEntry(userId, vaultEntryRequestDTO));
    }

    /**
     * Event-driven entry point (P2-09): a marketplace import identifies the user by `keycloakId`,
     * because that is the only user id ms_marketplace ever sees (the JWT subject). `fk_user_id` is a
     * real FK to users(user_id), so resolve one to the other here rather than in the listener.
     * <p>
     * Delegates to addVaultEntry, so a redelivered event is harmless. An unknown keycloakId throws —
     * the listener lets that propagate so the message is retried and finally dead-lettered instead
     * of being dropped as if it had been handled.
     */
    @Transactional
    @Override
    public VaultEntryResponseDTO importDictionary(String keycloakId, String dictionaryId) {
        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new RecordNotFoundException(USER_WAS_NOT_FOUND_KEYCLOAK_ID + keycloakId));

        return addVaultEntry(user.getUserId(), VaultEntryRequestDTO.builder()
                .dictionaryId(dictionaryId)
                .build());
    }

    /**
     * A vault belongs to a profile, and the token identifies that profile by `keycloakId` — so the
     * guard resolves the profile and compares, rather than comparing the path's userId directly
     * (the JWT subject is never ms_user's own primary key).
     */
    private void requireOwnProfile(String userId, String callerKeycloakId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RecordNotFoundException(USER_WAS_NOT_FOUND_ID + userId));

        if (!callerKeycloakId.equals(user.getKeycloakId())) {
            throw new ForbiddenOperationException(NOT_THE_OWNER);
        }
    }

    @Transactional
    @Override
    public void deleteVaultEntry(String userId, String dictionaryId, String callerKeycloakId) {
        requireOwnProfile(userId, callerKeycloakId);
        // A delete of an entry that is not in the vault is a silent no-op (200), matching
        // DictionaryServiceImpl.deleteDictionary and UserServiceImpl.deleteUser.
        vaultEntryRepository.deleteByUserIdAndDictionaryId(userId, dictionaryId);
    }

    private VaultEntryResponseDTO createVaultEntry(String userId, VaultEntryRequestDTO vaultEntryRequestDTO) {
        // fk_user_id is a real FK to users(user_id), so an unknown user would fail at the DB with a
        // constraint violation (500). Check first and report the honest 404 instead.
        if (!userRepository.existsById(userId)) {
            throw new RecordNotFoundException(USER_WAS_NOT_FOUND_ID + userId);
        }

        // The id is generated here rather than supplied by the client: a vault entry is a
        // system-owned row (P2-09 creates identical rows from a marketplace event).
        VaultEntry vaultEntry = vaultEntryMapper.toVaultEntry(
                UUID.randomUUID().toString(), userId, vaultEntryRequestDTO);
        return vaultEntryMapper.toVaultEntryResponseDTO(vaultEntryRepository.saveAndFlush(vaultEntry));
    }
}
