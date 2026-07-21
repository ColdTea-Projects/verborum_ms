package de.coldtea.verborum.msuser.user.service;

import de.coldtea.verborum.msuser.user.dto.UserRequestDTO;
import de.coldtea.verborum.msuser.user.dto.UserResponseDTO;

public interface UserService {
    UserResponseDTO saveUser(UserRequestDTO userDto);
    UserResponseDTO getUserById(String userId);
    void deleteUser(String userId);
}
