package com.analytics.security.service;

import com.analytics.security.dto.AuthResponse;
import com.analytics.security.dto.LoginRequest;
import com.analytics.security.dto.RegisterRequest;
import com.analytics.security.dto.UserProfileResponse;
import com.analytics.security.entity.Role;
import com.analytics.security.entity.User;
import com.analytics.security.jwt.JwtService;
import com.analytics.security.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String cleanUsername = request.username().trim();
        String cleanEmail = request.email().trim().toLowerCase();

        if (userRepository.existsByUsernameIgnoreCase(cleanUsername)) {
            throw new IllegalArgumentException("Username '" + cleanUsername + "' is already registered.");
        }

        if (userRepository.existsByEmailIgnoreCase(cleanEmail)) {
            throw new IllegalArgumentException("Email '" + cleanEmail + "' is already registered.");
        }

        validatePasswordStrength(request.password());

        String encodedPassword = passwordEncoder.encode(request.password());

        // Always assign ANALYST role by default for new registrations
        User user = new User(cleanUsername, cleanEmail, encodedPassword, Role.ANALYST);
        User savedUser = userRepository.save(user);

        log.info("New user registered successfully: id={}, username='{}', role={}",
                savedUser.getId(), savedUser.getUsername(), savedUser.getRole());

        String token = jwtService.generateToken(savedUser);
        return AuthResponse.of(token, jwtService.getExpirationMs(), UserProfileResponse.fromEntity(savedUser));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String identifier = request.getIdentifier();

        User user = userRepository.findByUsernameIgnoreCase(identifier)
                .or(() -> userRepository.findByEmailIgnoreCase(identifier))
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password."));

        if (!user.isEnabled()) {
            throw new DisabledException("Account is disabled. Please contact an administrator.");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid username or password.");
        }

        log.info("User logged in successfully: id={}, username='{}', role={}",
                user.getId(), user.getUsername(), user.getRole());

        String token = jwtService.generateToken(user);
        return AuthResponse.of(token, jwtService.getExpirationMs(), UserProfileResponse.fromEntity(user));
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BadCredentialsException("User is not authenticated.");
        }

        String username = authentication.getName();
        User user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        return UserProfileResponse.fromEntity(user);
    }

    @Transactional(readOnly = true)
    public User getUserEntity(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BadCredentialsException("User is not authenticated.");
        }
        String username = authentication.getName();
        return userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    private void validatePasswordStrength(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters long.");
        }
        // At least one letter and at least one digit
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new IllegalArgumentException("Password must contain at least one letter and one number.");
        }
    }
}
