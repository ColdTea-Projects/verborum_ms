package de.coldtea.verborum.msuser.user.controller;

import de.coldtea.verborum.msuser.common.response.Response;
import de.coldtea.verborum.msuser.user.dto.UserRequestDTO;
import de.coldtea.verborum.msuser.user.dto.UserResponseDTO;
import de.coldtea.verborum.msuser.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;

import static de.coldtea.verborum.msuser.common.constants.ResponseMessageConstants.*;
import static de.coldtea.verborum.msuser.common.utils.ResponseUtils.buildResponse;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/")
    public ResponseEntity<Response> createUser(@Valid @RequestBody UserRequestDTO user, WebRequest request) {
        UserResponseDTO userResponseDTO = userService.saveUser(user);
        return buildResponse(HttpStatus.CREATED, USER_SAVED_SUCCESSFULLY, userResponseDTO.getUserId(), request);
    }

    @PutMapping("/")
    public ResponseEntity<Response> updateUser(@Valid @RequestBody UserRequestDTO user, WebRequest request) {
        userService.saveUser(user);
        return buildResponse(HttpStatus.CREATED, USER_UPDATED_SUCCESSFULLY, user.getUserId(), request);
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserResponseDTO> getUserById(@PathVariable String userId) {
        return new ResponseEntity<>(userService.getUserById(userId), HttpStatus.OK);
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Response> deleteUser(@PathVariable String userId, WebRequest request) {
        userService.deleteUser(userId);
        return buildResponse(HttpStatus.OK, USER_DELETED_SUCCESSFULLY, userId, request);
    }

}
