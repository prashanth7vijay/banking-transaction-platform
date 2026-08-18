package com.platform.auth.service;

import com.platform.auth.event.AuthActionEvent;
import com.platform.shared.exception.ResourceNotFoundException;
import com.platform.shared.exception.UnauthorizedException;
import com.platform.users.domain.Role;
import com.platform.users.domain.User;
import com.platform.users.domain.UserStatus;
import com.platform.users.service.UserService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserService userService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private TokenDenylistService tokenDenylistService;
    @Mock
    private PasswordResetTokenService passwordResetTokenService;
    @Mock
    private JavaMailSender mailSender;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private SimpleMeterRegistry meterRegistry;
    private AuthService authService;

    private User activeUser;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        authService = new AuthService(
                userService, passwordEncoder, jwtService, refreshTokenService,
                tokenDenylistService, passwordResetTokenService, mailSender,
                eventPublisher, meterRegistry
        );

        activeUser = new User();
        activeUser.setEmail("customer@platform.local");
        activeUser.setPasswordHash("hashed");
        activeUser.setStatus(UserStatus.ACTIVE);
        activeUser.getRoles().add(new Role("CUSTOMER"));
    }

    @Test
    void loginSucceedsAndRecordsSuccessMetricAndEvent() {
        when(userService.getByEmail("customer@platform.local")).thenReturn(activeUser);
        when(passwordEncoder.matches("correct-password", "hashed")).thenReturn(true);
        when(jwtService.generateAccessToken(any(), any(), any()))
                .thenReturn(new JwtService.AccessToken("token", java.time.Instant.now().plusSeconds(900), "jti-1"));
        when(refreshTokenService.issue(any()))
                .thenReturn(new RefreshTokenService.IssuedRefreshToken("refresh-raw", java.time.Instant.now().plusSeconds(604800)));

        AuthService.LoginResult result = authService.login("customer@platform.local", "correct-password");

        assertThat(result.accessToken().token()).isEqualTo("token");
        assertThat(meterRegistry.get("platform.auth.login").tag("result", "success").counter().count()).isEqualTo(1.0);
        verify(eventPublisher).publishEvent(any(AuthActionEvent.class));
    }

    @Test
    void loginFailsOnWrongPasswordAndRecordsFailureMetric() {
        when(userService.getByEmail("customer@platform.local")).thenReturn(activeUser);
        when(passwordEncoder.matches("wrong-password", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login("customer@platform.local", "wrong-password"))
                .isInstanceOf(UnauthorizedException.class);

        assertThat(meterRegistry.get("platform.auth.login").tag("result", "failure").counter().count()).isEqualTo(1.0);
        verify(eventPublisher, never()).publishEvent(any(AuthActionEvent.class));
    }

    @Test
    void loginFailsWhenAccountNotActiveAndRecordsFailureMetric() {
        activeUser.setStatus(UserStatus.DISABLED);
        when(userService.getByEmail("customer@platform.local")).thenReturn(activeUser);

        assertThatThrownBy(() -> authService.login("customer@platform.local", "any-password"))
                .isInstanceOf(UnauthorizedException.class);

        assertThat(meterRegistry.get("platform.auth.login").tag("result", "failure").counter().count()).isEqualTo(1.0);
    }

    @Test
    void loginFailsWhenUserDoesNotExistAndRecordsFailureMetric() {
        when(userService.getByEmail("nobody@platform.local")).thenThrow(new ResourceNotFoundException("User not found"));

        assertThatThrownBy(() -> authService.login("nobody@platform.local", "any-password"))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThat(meterRegistry.get("platform.auth.login").tag("result", "failure").counter().count()).isEqualTo(1.0);
    }
}
