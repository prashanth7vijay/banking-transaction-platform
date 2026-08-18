package com.platform.auth.service;

import com.platform.auth.event.AuthActionEvent;
import com.platform.shared.exception.UnauthorizedException;
import com.platform.users.domain.Role;
import com.platform.users.domain.User;
import com.platform.users.domain.UserStatus;
import com.platform.users.service.UserService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final TokenDenylistService tokenDenylistService;
    private final PasswordResetTokenService passwordResetTokenService;
    private final JavaMailSender mailSender;
    private final ApplicationEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;

    public record LoginResult(User user, JwtService.AccessToken accessToken, RefreshTokenService.IssuedRefreshToken refreshToken) {
    }

    public User register(String email, String password, String firstName, String lastName) {
        return userService.createCustomer(email, password, firstName, lastName);
    }

    public LoginResult login(String email, String rawPassword) {
        User user;
        try {
            user = userService.getByEmail(email);
        } catch (RuntimeException e) {
            recordLoginAttempt("failure");
            throw e;
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            recordLoginAttempt("failure");
            throw new UnauthorizedException("Account is not active");
        }
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            recordLoginAttempt("failure");
            throw new UnauthorizedException("Invalid email or password");
        }

        Set<String> roleNames = user.getRoles().stream().map(Role::getName).collect(Collectors.toSet());
        JwtService.AccessToken accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), roleNames);
        RefreshTokenService.IssuedRefreshToken refreshToken = refreshTokenService.issue(user.getId());

        recordLoginAttempt("success");
        eventPublisher.publishEvent(new AuthActionEvent(user.getId(), user.getEmail(), AuthActionEvent.LOGIN));

        return new LoginResult(user, accessToken, refreshToken);
    }

    public LoginResult refresh(String rawRefreshToken) {
        RefreshTokenService.RotationResult rotation = refreshTokenService.rotate(rawRefreshToken);
        User user = userService.getById(rotation.userId());

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new UnauthorizedException("Account is not active");
        }

        Set<String> roleNames = user.getRoles().stream().map(Role::getName).collect(Collectors.toSet());
        JwtService.AccessToken accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), roleNames);

        return new LoginResult(user, accessToken, rotation.next());
    }

    public void logout(String rawRefreshToken, String currentAccessTokenJti, Instant currentAccessTokenExpiry,
                        java.util.UUID userId, String email) {
        if (rawRefreshToken != null) {
            refreshTokenService.revoke(rawRefreshToken);
        }
        if (currentAccessTokenJti != null && currentAccessTokenExpiry != null) {
            tokenDenylistService.deny(currentAccessTokenJti, currentAccessTokenExpiry);
        }
        if (userId != null) {
            eventPublisher.publishEvent(new AuthActionEvent(userId, email, AuthActionEvent.LOGOUT));
        }
    }

    public void forgotPassword(String email) {
        // Deliberately do not reveal whether the email exists - always behave the same.
        try {
            User user = userService.getByEmail(email);
            String token = passwordResetTokenService.issue(user.getEmail());
            sendResetEmail(user.getEmail(), token);
        } catch (Exception ignored) {
            // swallow - do not leak account existence via timing/error differences beyond this try/catch
        }
    }

    public void resetPassword(String token, String newPassword) {
        String email = passwordResetTokenService.consume(token)
                .orElseThrow(() -> new UnauthorizedException("Reset token is invalid or expired"));
        User user = userService.getByEmail(email);
        userService.resetPassword(user.getId(), newPassword);

        eventPublisher.publishEvent(new AuthActionEvent(user.getId(), user.getEmail(), AuthActionEvent.PASSWORD_RESET));
    }

    private void recordLoginAttempt(String result) {
        meterRegistry.counter("platform.auth.login", "result", result).increment();
    }

    private void sendResetEmail(String email, String token) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("Reset your Transaction Platform password");
        message.setText("Use this token to reset your password (valid 15 minutes): " + token);
        mailSender.send(message);
    }
}
