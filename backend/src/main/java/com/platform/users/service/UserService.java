package com.platform.users.service;

import com.platform.shared.exception.ConflictException;
import com.platform.shared.exception.ResourceNotFoundException;
import com.platform.shared.exception.UnauthorizedException;
import com.platform.users.domain.Role;
import com.platform.users.domain.RoleName;
import com.platform.users.domain.User;
import com.platform.users.domain.UserStatus;
import com.platform.users.dto.UpdateProfileRequest;
import com.platform.users.event.UserActionEvent;
import com.platform.users.repository.RoleRepository;
import com.platform.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public User createCustomer(String email, String rawPassword, String firstName, String lastName) {
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists");
        }
        Role customerRole = roleRepository.findByName(RoleName.CUSTOMER)
                .orElseThrow(() -> new IllegalStateException("CUSTOMER role is not seeded"));

        User user = new User();
        user.setEmail(email.toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setStatus(UserStatus.ACTIVE);
        user.getRoles().add(customerRole);

        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User getById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    @Transactional(readOnly = true)
    public User getByEmail(String email) {
        return userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    @Transactional
    public User updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = getById(userId);
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        return userRepository.save(user);
    }

    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        User user = getById(userId);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new UnauthorizedException("Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        eventPublisher.publishEvent(new UserActionEvent(userId, UserActionEvent.PASSWORD_CHANGED));
    }

    @Transactional
    public void resetPassword(UUID userId, String newPassword) {
        User user = getById(userId);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }
}
