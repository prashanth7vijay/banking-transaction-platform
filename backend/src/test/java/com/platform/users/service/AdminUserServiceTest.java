package com.platform.users.service;

import com.platform.shared.exception.ConflictException;
import com.platform.users.domain.Role;
import com.platform.users.domain.User;
import com.platform.users.domain.UserStatus;
import com.platform.users.repository.RoleRepository;
import com.platform.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;

    private AdminUserService adminUserService;

    private final UUID adminId = UUID.randomUUID();
    private final UUID targetUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        adminUserService = new AdminUserService(userRepository, roleRepository);
    }

    @Test
    void adminCannotChangeTheirOwnStatus() {
        assertThatThrownBy(() -> adminUserService.updateStatus(adminId, adminId, UserStatus.DISABLED))
                .isInstanceOf(ConflictException.class);
        verifyNoInteractions(userRepository);
    }

    @Test
    void adminCannotChangeTheirOwnRoles() {
        assertThatThrownBy(() -> adminUserService.updateRoles(adminId, adminId, Set.of("CUSTOMER")))
                .isInstanceOf(ConflictException.class);
        verifyNoInteractions(userRepository);
    }

    @Test
    void adminCanChangeAnotherUsersStatus() {
        User target = new User();
        target.setStatus(UserStatus.ACTIVE);
        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(target));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = adminUserService.updateStatus(adminId, targetUserId, UserStatus.LOCKED);

        assertThat(result.getStatus()).isEqualTo(UserStatus.LOCKED);
    }

    @Test
    void adminCanReassignAnotherUsersRoles() {
        User target = new User();
        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(target));
        when(roleRepository.findByName("EMPLOYEE")).thenReturn(Optional.of(new Role("EMPLOYEE")));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = adminUserService.updateRoles(adminId, targetUserId, Set.of("EMPLOYEE"));

        assertThat(result.getRoles()).extracting(Role::getName).containsExactly("EMPLOYEE");
    }

    @Test
    void reassigningAnUnknownRoleFails() {
        User target = new User();
        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(target));
        when(roleRepository.findByName("SUPERUSER")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminUserService.updateRoles(adminId, targetUserId, Set.of("SUPERUSER")))
                .isInstanceOf(ConflictException.class);
    }
}
