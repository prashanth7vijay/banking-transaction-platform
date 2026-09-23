package com.platform.users.service;

import com.platform.users.domain.Role;
import com.platform.users.port.UserLookupPort;
import com.platform.users.port.UserSummary;
import com.platform.users.repository.UserRepository;
import com.platform.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserLookupPortImpl implements UserLookupPort {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserSummary getById(UUID userId) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        var roles = user.getRoles().stream().map(Role::getName).collect(Collectors.toSet());
        return new UserSummary(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
                user.getStatus().name(), user.getCreatedAt(), roles);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, String> getFirstNamesByIds(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findFirstNamesByIdIn(userIds).stream()
                .collect(Collectors.toMap(UserRepository.UserIdName::getId, UserRepository.UserIdName::getFirstName));
    }
}
