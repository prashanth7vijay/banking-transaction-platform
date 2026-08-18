package com.platform.users.controller;

import com.platform.auth.security.CurrentUserProvider;
import com.platform.users.dto.UpdateUserRolesRequest;
import com.platform.users.dto.UpdateUserStatusRequest;
import com.platform.users.dto.UserResponse;
import com.platform.users.mapper.UserMapper;
import com.platform.users.service.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final UserMapper userMapper;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public List<UserResponse> search(@RequestParam(required = false) String search) {
        return adminUserService.search(search).stream().map(userMapper::toResponse).toList();
    }

    @PatchMapping("/{id}/status")
    public UserResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateUserStatusRequest request) {
        UUID actingAdminId = currentUserProvider.get().userId();
        return userMapper.toResponse(adminUserService.updateStatus(actingAdminId, id, request.status()));
    }

    @PatchMapping("/{id}/roles")
    public UserResponse updateRoles(@PathVariable UUID id, @Valid @RequestBody UpdateUserRolesRequest request) {
        UUID actingAdminId = currentUserProvider.get().userId();
        return userMapper.toResponse(adminUserService.updateRoles(actingAdminId, id, request.roles()));
    }
}
