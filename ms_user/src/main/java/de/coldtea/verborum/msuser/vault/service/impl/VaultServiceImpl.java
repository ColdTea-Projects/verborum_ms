package de.coldtea.verborum.msuser.vault.service.impl;

import de.coldtea.verborum.msuser.common.event.OutboundEvent;
import de.coldtea.verborum.msuser.common.event.VaultChangedEvent;
import de.coldtea.verborum.msuser.common.exception.ForbiddenOperationException;
import de.coldtea.verborum.msuser.common.exception.RecordNotFoundException;
import de.coldtea.verborum.msuser.common.mapper.VaultEntryMapper;
import de.coldtea.verborum.msuser.common.utils.RequestDevice;
import de.coldtea.verborum.msuser.user.entity.User;
import de.coldtea.verborum.msuser.user.repository.UserRepository;
import de.coldtea.verborum.msuser.vault.dto.VaultEntryRequestDTO;
import de.coldtea.verborum.msuser.vault.dto.VaultEntryResponseDTO;
import de.coldtea.verborum.msuser.vault.entity.VaultEntry;
import de.coldtea.verborum.msuser.vault.repository.VaultEntryRepository;
import de.coldtea.verborum.msuser.vault.service.VaultService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static de.coldtea.verborum.msuser.common.config.RabbitMQConfig.ROUTING_KEY_VAULT_CHANGED;
import static de.coldtea.verborum.msuser.common.constants.ErrorMessageConstants.NOT_THE_OWNER;
import static de.coldtea.verborum.msuser.common.constants.ErrorMessageConstants.USER_WAS_NOT_FOUND_ID;
import static de.coldtea.verborum.msuser.common.constants.ErrorMessageConstants.USER_WAS_NOT_FOUND_KEYCLOAK_ID;

@Service
@RequiredArgsConstructor
public class VaultServiceImpl implements VaultService {

    private final VaultEntryRepository vaultEntryRepository;

    private final UserRepository userRepository;

    private final VaultEntryMapper vaultEntryMapper;

    // Not RabbitTemplate: OutboundEventPublisher sends after this transaction commits (rule 1)
    private final ApplicationEventPublisher eventPublisher;

    // Which install is making an HTTP request (P4N-14) — decoration for the sync push, not ownership
    private final RequestDevice requestDevice;

    @Override
    public List<VaultEntryResponseDTO> getVaultEntriesByUser(String userId, String callerKeycloakId) {
        // 404 for an unknown OR unowned profile: a read by id, so a 403 would confirm the id exists
        // (SEC-14 — until 2026-10-05 the unowned case was a 403 despite this rule). Writes keep the 403
        // of requireOwnProfile
        User user = userRepository.findById(userId)
                .filter(profile -> callerKeycloakId.equals(profile.getKeycloakId()))
                .orElseThrow(() -> new RecordNotFoundException(USER_WAS_NOT_FOUND_ID + userId));

        // The vault belongs to the Forum: a user who is not a member (never joined, or withdrew) sees
        // it empty. Nothing is deleted — the rows stay, so re-joining restores the library at once,
        // and the entries keep matching dictionary_imports in ms_marketplace.
        if (!Boolean.TRUE.equals(user.getMarketplaceAgreementAccepted())) {
            return List.of();
        }

        return vaultEntryRepository.findByUserId(userId)
                .stream()
                .map(vaultEntryMapper::toVaultEntryResponseDTO)
                .toList();
    }

    /**
     * Event-driven entry point (P2-09): a marketplace import identifies the user by `keycloakId`,
     * because that is the only user id ms_marketplace sees (the JWT subject). `fk_user_id` is a
     * real FK to users(user_id), so resolve one to the other here rather than in the listener.
     * <p>
     * The only way into a vault (SEC-09 removed the public add — see VaultController). No ownership
     * check, because the actor is ms_marketplace, which already applied the Forum gate and the "not
     * your own" rule.
     * <p>
     * Idempotent by design: a vault is a set, enforced by UNIQUE (fk_user_id, fk_dictionary_id), so
     * re-importing returns the existing entry instead of failing — which is also what P2-09 needs when
     * RabbitMQ redelivers a dictionary.imported event. An unknown keycloakId throws; the listener lets
     * that propagate so the message is retried and finally dead-lettered instead of being dropped as if
     * it had been handled.
     */
    @Transactional
    @Override
    public VaultEntryResponseDTO importDictionary(String keycloakId, String dictionaryId, String deviceId) {
        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new RecordNotFoundException(USER_WAS_NOT_FOUND_KEYCLOAK_ID + keycloakId));

        Optional<VaultEntry> existing = vaultEntryRepository.findByUserIdAndDictionaryId(user.getUserId(), dictionaryId);
        if (existing.isPresent()) {
            return vaultEntryMapper.toVaultEntryResponseDTO(existing.get());
        }

        VaultEntryResponseDTO created = createVaultEntry(user.getUserId(), VaultEntryRequestDTO.builder()
                .dictionaryId(dictionaryId)
                .build());

        // Only a real addition: the user's other devices learn their vault changed. Published from
        // here, once the entry exists, because the import event can outrun this write
        publishVaultChanged(keycloakId, dictionaryId, deviceId);
        return created;
    }

    private void publishVaultChanged(String keycloakId, String dictionaryId, String deviceId) {
        eventPublisher.publishEvent(new OutboundEvent(
                ROUTING_KEY_VAULT_CHANGED,
                VaultChangedEvent.builder()
                        .keycloakId(keycloakId)
                        .dictionaryId(dictionaryId)
                        .deviceId(deviceId)
                        .eventTimestamp(OffsetDateTime.now())
                        .build()));
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
        // DictionaryServiceImpl.deleteDictionary and UserServiceImpl.deleteUser — and says nothing.
        boolean existed = vaultEntryRepository.findByUserIdAndDictionaryId(userId, dictionaryId).isPresent();
        vaultEntryRepository.deleteByUserIdAndDictionaryId(userId, dictionaryId);

        if (existed) {
            // The user's other devices drop it too (P4N-14); this one is skipped by its X-Device-Id
            publishVaultChanged(callerKeycloakId, dictionaryId, requestDevice.currentDeviceId());
        }
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
