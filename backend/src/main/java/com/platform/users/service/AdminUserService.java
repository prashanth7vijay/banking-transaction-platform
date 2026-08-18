package com.platform.users.service;

import com.platform.shared.exception.ConflictException;
import com.platform.shared.exception.ResourceNotFoundException;
import com.platform.users.domain.Role;
import com.platform.users.domain.User;
import com.platform.users.domain.UserStatus;
import com.platform.users.repository.RoleRepository;
import com.platform.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Separated from {@link UserService} (which handles self-service profile
 * operations) because these are a different responsibility with a different
 * caller: an admin acting on someone else's account, not a user acting on their
 * own.
 */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @Transactional(readOnly = true)
    public List<User> search(String search) {
        return userRepository.search(search);
    }

    @Transactional
    public User updateStatus(UUID actingAdminId, UUID targetUserId, UserStatus newStatus) {
        assertNotSelf(actingAdminId, targetUserId, "You cannot change your own account status");
        User user = getOrThrow(targetUserId);
        user.setStatus(newStatus);
        return userRepository.save(user);
    }

    @Transactional
    public User updateRoles(UUID actingAdminId, UUID targetUserId, Set<String> roleNames) {
        assertNotSelf(actingAdminId, targetUserId, "You cannot change your own roles");
        User user = getOrThrow(targetUserId);

        Set<Role> resolvedRoles = roleNames.stream()
                .map(name -> roleRepository.findByName(name)
                        .orElseThrow(() -> new ConflictException("Unknown role: " + name)))
                .collect(Collectors.toCollection(HashSet::new));

        user.setRoles(resolvedRoles);
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public Map<String, Long> countUsersByRole() {
        return userRepository.countUsersByRole().stream()
                .collect(Collectors.toMap(UserRepository.RoleCount::getRoleName, UserRepository.RoleCount::getUserCount));
    }

    private void assertNotSelf(UUID actingAdminId, UUID targetUserId, String message) {
        if (actingAdminId.equals(targetUserId)) {
            throw new ConflictException(message);
        }
    }

    private User getOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
