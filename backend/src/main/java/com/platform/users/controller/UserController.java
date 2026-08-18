package com.platform.users.controller;

import com.platform.auth.security.CurrentUserProvider;
import com.platform.users.dto.ChangePasswordRequest;
import com.platform.users.dto.UpdateProfileRequest;
import com.platform.users.dto.UserResponse;
import com.platform.users.mapper.UserMapper;
import com.platform.users.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserMapper userMapper;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/me")
    public UserResponse me() {
        var current = currentUserProvider.get();
        return userMapper.toResponse(userService.getById(current.userId()));
    }

    @PatchMapping("/me")
    public UserResponse updateMe(@Valid @RequestBody UpdateProfileRequest request) {
        var current = currentUserProvider.get();
        return userMapper.toResponse(userService.updateProfile(current.userId(), request));
    }

    @PostMapping("/me/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        var current = currentUserProvider.get();
        userService.changePassword(current.userId(), request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
