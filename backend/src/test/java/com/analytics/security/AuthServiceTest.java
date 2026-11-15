package com.analytics.security;

import com.analytics.security.dto.AuthResponse;
import com.analytics.security.dto.LoginRequest;
import com.analytics.security.dto.RegisterRequest;
import com.analytics.security.dto.UserProfileResponse;
import com.analytics.security.entity.Role;
import com.analytics.security.entity.User;
import com.analytics.security.jwt.JwtService;
import com.analytics.security.repository.UserRepository;
import com.analytics.security.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private Authentication authentication;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService);
    }

    @Test
    void register_ValidUser_SavesUserAndReturnsAuthResponse() {
        RegisterRequest req = new RegisterRequest("analyst1", "analyst1@example.com", "Password123!");

        when(userRepository.existsByUsernameIgnoreCase("analyst1")).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase("analyst1@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123!")).thenReturn("$2a$10$encodedHash123");
        when(jwtService.generateToken(any(User.class))).thenReturn("mock-jwt-token");
        when(jwtService.getExpirationMs()).thenReturn(86400000L);

        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(10L);
            return u;
        });

        AuthResponse resp = authService.register(req);

        assertNotNull(resp);
        assertEquals("mock-jwt-token", resp.token());
        assertEquals(86400000L, resp.expiresIn());
        assertEquals("analyst1", resp.user().username());
        assertEquals("analyst1@example.com", resp.user().email());
        assertEquals(Role.ANALYST, resp.user().role());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertEquals("analyst1", savedUser.getUsername());
        assertEquals("analyst1@example.com", savedUser.getEmail());
        assertEquals("$2a$10$encodedHash123", savedUser.getPasswordHash());
        assertEquals(Role.ANALYST, savedUser.getRole());
        assertTrue(savedUser.isEnabled());
    }

    @Test
    void register_DuplicateUsername_ThrowsIllegalArgumentException() {
        RegisterRequest req = new RegisterRequest("existinguser", "new@example.com", "Password123!");
        when(userRepository.existsByUsernameIgnoreCase("existinguser")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> authService.register(req));
        assertTrue(ex.getMessage().contains("already registered"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_DuplicateEmail_ThrowsIllegalArgumentException() {
        RegisterRequest req = new RegisterRequest("newuser", "existing@example.com", "Password123!");
        when(userRepository.existsByUsernameIgnoreCase("newuser")).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase("existing@example.com")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> authService.register(req));
        assertTrue(ex.getMessage().contains("already registered"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_ShortPassword_ThrowsIllegalArgumentException() {
        RegisterRequest req = new RegisterRequest("user1", "user1@example.com", "Pass1");
        when(userRepository.existsByUsernameIgnoreCase("user1")).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase("user1@example.com")).thenReturn(false);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> authService.register(req));
        assertTrue(ex.getMessage().contains("at least 8 characters"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_PasswordWithoutDigit_ThrowsIllegalArgumentException() {
        RegisterRequest req = new RegisterRequest("user1", "user1@example.com", "PasswordOnly");
        when(userRepository.existsByUsernameIgnoreCase("user1")).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase("user1@example.com")).thenReturn(false);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> authService.register(req));
        assertTrue(ex.getMessage().contains("at least one letter and one number"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_PasswordWithoutLetter_ThrowsIllegalArgumentException() {
        RegisterRequest req = new RegisterRequest("user1", "user1@example.com", "1234567890");
        when(userRepository.existsByUsernameIgnoreCase("user1")).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase("user1@example.com")).thenReturn(false);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> authService.register(req));
        assertTrue(ex.getMessage().contains("at least one letter and one number"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void login_ValidUsername_Success() {
        LoginRequest req = new LoginRequest("alice", "SecretPass123");
        User user = new User("alice", "alice@example.com", "$2a$10$hashedPass", Role.ADMIN);
        user.setId(1L);

        when(userRepository.findByUsernameIgnoreCase("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("SecretPass123", "$2a$10$hashedPass")).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("mock-jwt-token-admin");
        when(jwtService.getExpirationMs()).thenReturn(86400000L);

        AuthResponse resp = authService.login(req);

        assertNotNull(resp);
        assertEquals("mock-jwt-token-admin", resp.token());
        assertEquals("alice", resp.user().username());
        assertEquals(Role.ADMIN, resp.user().role());
    }

    @Test
    void login_ValidEmail_Success() {
        LoginRequest req = new LoginRequest("bob@example.com", "SecretPass123");
        User user = new User("bob", "bob@example.com", "$2a$10$hashedPass", Role.VIEWER);
        user.setId(2L);

        when(userRepository.findByUsernameIgnoreCase("bob@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("bob@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("SecretPass123", "$2a$10$hashedPass")).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("mock-jwt-token-viewer");
        when(jwtService.getExpirationMs()).thenReturn(86400000L);

        AuthResponse resp = authService.login(req);

        assertNotNull(resp);
        assertEquals("mock-jwt-token-viewer", resp.token());
        assertEquals("bob", resp.user().username());
        assertEquals(Role.VIEWER, resp.user().role());
    }

    @Test
    void login_UserNotFound_ThrowsBadCredentialsException() {
        LoginRequest req = new LoginRequest("unknown", "SecretPass123");
        when(userRepository.findByUsernameIgnoreCase("unknown")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("unknown")).thenReturn(Optional.empty());

        BadCredentialsException ex = assertThrows(BadCredentialsException.class, () -> authService.login(req));
        assertEquals("Invalid username or password.", ex.getMessage());
    }

    @Test
    void login_WrongPassword_ThrowsBadCredentialsException() {
        LoginRequest req = new LoginRequest("alice", "WrongPass");
        User user = new User("alice", "alice@example.com", "$2a$10$hashedPass", Role.ADMIN);

        when(userRepository.findByUsernameIgnoreCase("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPass", "$2a$10$hashedPass")).thenReturn(false);

        BadCredentialsException ex = assertThrows(BadCredentialsException.class, () -> authService.login(req));
        assertEquals("Invalid username or password.", ex.getMessage());
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void login_DisabledUser_ThrowsDisabledException() {
        LoginRequest req = new LoginRequest("alice", "SecretPass123");
        User user = new User("alice", "alice@example.com", "$2a$10$hashedPass", Role.ADMIN);
        user.setEnabled(false);

        when(userRepository.findByUsernameIgnoreCase("alice")).thenReturn(Optional.of(user));

        DisabledException ex = assertThrows(DisabledException.class, () -> authService.login(req));
        assertTrue(ex.getMessage().contains("Account is disabled"));
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void getCurrentUser_Authenticated_ReturnsProfile() {
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("alice");

        User user = new User("alice", "alice@example.com", "$2a$10$hashedPass", Role.ADMIN);
        user.setId(5L);
        when(userRepository.findByUsernameIgnoreCase("alice")).thenReturn(Optional.of(user));

        UserProfileResponse profile = authService.getCurrentUser(authentication);

        assertNotNull(profile);
        assertEquals(5L, profile.id());
        assertEquals("alice", profile.username());
        assertEquals(Role.ADMIN, profile.role());
    }

    @Test
    void getCurrentUser_Unauthenticated_ThrowsBadCredentialsException() {
        when(authentication.isAuthenticated()).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> authService.getCurrentUser(authentication));
    }

    @Test
    void getCurrentUser_UserNotFound_ThrowsUsernameNotFoundException() {
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("ghost");
        when(userRepository.findByUsernameIgnoreCase("ghost")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> authService.getCurrentUser(authentication));
    }
}
