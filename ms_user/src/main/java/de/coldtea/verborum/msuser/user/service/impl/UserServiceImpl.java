package de.coldtea.verborum.msuser.user.service.impl;

import de.coldtea.verborum.msuser.common.exception.RecordNotFoundException;
import de.coldtea.verborum.msuser.common.mapper.UserMapper;
import de.coldtea.verborum.msuser.user.dto.UserRequestDTO;
import de.coldtea.verborum.msuser.user.dto.UserResponseDTO;
import de.coldtea.verborum.msuser.user.entity.User;
import de.coldtea.verborum.msuser.user.repository.UserRepository;
import de.coldtea.verborum.msuser.user.service.UserService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static de.coldtea.verborum.msuser.common.constants.ErrorMessageConstants.USER_WAS_NOT_FOUND_ID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    private final UserMapper userMapper;

    @Transactional
    @Override
    public UserResponseDTO saveUser(UserRequestDTO userRequestDTO) {
        // Backs both POST and PUT — a client-generated userId that already exists is an update,
        // otherwise an insert (mirrors DictionaryServiceImpl.saveDictionary).
        User savedUser = userRepository.saveAndFlush(userMapper.toUser(userRequestDTO));
        return userMapper.toUserResponseDTO(savedUser);
    }

    @Override
    public UserResponseDTO getUserById(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RecordNotFoundException(USER_WAS_NOT_FOUND_ID + userId));
        return userMapper.toUserResponseDTO(user);
    }

    @Transactional
    @Override
    public void deleteUser(String userId) {
        // user_stats and vault_entries are removed by their DB FK ON DELETE CASCADE.
        // deleteById is a silent no-op on a missing row in Spring Data JPA 3.x, so this stays a 200
        // either way (same behaviour as DictionaryServiceImpl.deleteDictionary).
        // Publishing the user.deleted event (so ms_dictionary / ms_marketplace can cascade their
        // own data) is P2-08 — ms_user has no RabbitMQ wiring yet.
        userRepository.deleteById(userId);
    }
}
