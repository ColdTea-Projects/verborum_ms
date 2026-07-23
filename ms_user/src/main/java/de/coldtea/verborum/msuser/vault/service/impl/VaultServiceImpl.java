package de.coldtea.verborum.msuser.vault.service.impl;

import de.coldtea.verborum.msuser.common.exception.RecordNotFoundException;
import de.coldtea.verborum.msuser.common.mapper.VaultEntryMapper;
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

import static de.coldtea.verborum.msuser.common.constants.ErrorMessageConstants.USER_WAS_NOT_FOUND_ID;

@Service
@RequiredArgsConstructor
public class VaultServiceImpl implements VaultService {

    private final VaultEntryRepository vaultEntryRepository;

    private final UserRepository userRepository;

    private final VaultEntryMapper vaultEntryMapper;

    @Override
    public List<VaultEntryResponseDTO> getVaultEntriesByUser(String userId) {
        // No user-existence check: an unknown user simply has an empty vault, mirroring
        // ms_dictionary's GET /dictionaries/{userId}, which also returns a plain list.
        return vaultEntryRepository.findByUserId(userId)
                .stream()
                .map(vaultEntryMapper::toVaultEntryResponseDTO)
                .toList();
    }

    @Transactional
    @Override
    public VaultEntryResponseDTO addVaultEntry(String userId, VaultEntryRequestDTO vaultEntryRequestDTO) {
        // Idempotent by design: a vault is a set, enforced by UNIQUE (fk_user_id, fk_dictionary_id).
        // Re-importing the same dictionary returns the existing entry instead of failing, which is
        // also the behaviour P2-09 needs when RabbitMQ redelivers a dictionary.imported event.
        return vaultEntryRepository.findByUserIdAndDictionaryId(userId, vaultEntryRequestDTO.getDictionaryId())
                .map(vaultEntryMapper::toVaultEntryResponseDTO)
                .orElseGet(() -> createVaultEntry(userId, vaultEntryRequestDTO));
    }

    @Transactional
    @Override
    public void deleteVaultEntry(String userId, String dictionaryId) {
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
