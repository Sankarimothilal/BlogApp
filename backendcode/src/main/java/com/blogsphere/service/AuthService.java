package com.blogsphere.service;

import com.blogsphere.dto.request.LoginRequest;
import com.blogsphere.dto.request.RegisterRequest;
import com.blogsphere.dto.response.JwtResponse;
import com.blogsphere.entity.Role;
import com.blogsphere.entity.User;
import com.blogsphere.exception.ResourceNotFoundException;
import com.blogsphere.repository.UserRepository;
import com.blogsphere.security.JwtUtils;
import com.blogsphere.security.UserDetailsImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    private static final Duration RESET_CODE_LIFETIME = Duration.ofMinutes(5);
    private static final Duration RESET_AUTHORIZATION_LIFETIME = Duration.ofMinutes(10);
    private static final Duration RESET_RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final Duration RESET_RATE_WINDOW = Duration.ofHours(1);
    private static final int MAX_RESET_REQUESTS_PER_EMAIL = 3;
    private static final int MAX_RESET_REQUESTS_PER_IP = 20;
    private static final int MAX_RESET_VERIFICATION_ATTEMPTS = 5;

    private final Map<String, ResetChallenge> resetChallenges = new ConcurrentHashMap<>();
    private final Map<String, String> resetAuthorizationOwners = new ConcurrentHashMap<>();
    private final Map<String, ResetRequestLimit> resetRequestLimits = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private EmailService emailService;

    @Transactional
    public JwtResponse registerUser(RegisterRequest registerRequest) {
        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            throw new IllegalArgumentException("Error: Email is already in use!");
        }

        User user = new User(
            registerRequest.getFullName(),
            registerRequest.getEmail(),
            passwordEncoder.encode(registerRequest.getPassword()),
            Role.ROLE_READER
        );
        user.setAvatarUrl("https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=200&q=80");
        userRepository.save(user);

        return authenticateUser(new LoginRequest(registerRequest.getEmail(), registerRequest.getPassword()));
    }

    public JwtResponse authenticateUser(LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword())
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtils.generateJwtToken(authentication);

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return new JwtResponse(
            jwt,
            user.getId(),
            user.getFullName(),
            user.getEmail(),
            user.getRole().name(),
            user.getAvatarUrl()
        );
    }

    public User getCurrentAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        String email = auth.getName();
        return userRepository.findByEmail(email).orElse(null);
    }

    @Transactional
    public JwtResponse authenticateGoogleUser(String email, String fullName, String avatarUrl) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Google did not provide an email address");
        }
        String normalizedEmail = email.trim().toLowerCase();
        User user = userRepository.findByEmail(normalizedEmail).orElse(null);
        if (user == null) {
            user = new User(
                fullName != null && !fullName.isBlank() ? fullName : "Google User",
                normalizedEmail,
                passwordEncoder.encode(UUID.randomUUID().toString()),
                Role.ROLE_READER
            );
            user.setLocalPasswordEnabled(false);
            user.setAvatarUrl(avatarUrl != null && !avatarUrl.isBlank() ? avatarUrl : "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=200&q=80");
            user = userRepository.save(user);
        }

        UserDetailsImpl userDetails = UserDetailsImpl.build(user);
        Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtils.generateJwtToken(authentication);

        return new JwtResponse(
            jwt,
            user.getId(),
            user.getFullName(),
            user.getEmail(),
            user.getRole().name(),
            user.getAvatarUrl()
        );
    }

    public void requestPasswordReset(String email, String sourceAddress) {
        if (!emailService.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Password recovery email is not configured. Set MAIL_USERNAME and MAIL_PASSWORD.");
        }

        cleanExpiredResetState();
        String normalizedEmail = normalizeEmail(email);
        enforceResetRequestLimit("email:" + normalizedEmail, MAX_RESET_REQUESTS_PER_EMAIL,
                RESET_RATE_WINDOW, RESET_RESEND_COOLDOWN);
        enforceResetRequestLimit("ip:" + (sourceAddress == null ? "unknown" : sourceAddress),
                MAX_RESET_REQUESTS_PER_IP, RESET_RATE_WINDOW, Duration.ZERO);

        User user = userRepository.findByEmail(normalizedEmail).orElse(null);
        if (user == null || !user.isLocalPasswordEnabled()) {
            return;
        }

        byte[] salt = new byte[16];
        secureRandom.nextBytes(salt);
        String code = String.format(Locale.ROOT, "%06d", secureRandom.nextInt(1_000_000));
        ResetChallenge challenge = new ResetChallenge(
                salt,
                hashResetCode(salt, code),
                Instant.now().plus(RESET_CODE_LIFETIME)
        );

        ResetChallenge previous = resetChallenges.put(normalizedEmail, challenge);
        removeResetAuthorization(previous, normalizedEmail);
        if (!emailService.sendPasswordResetEmail(normalizedEmail, code)) {
            resetChallenges.remove(normalizedEmail, challenge);
        }
    }

    public String verifyResetCode(String email, String code) {
        cleanExpiredResetState();
        String normalizedEmail = normalizeEmail(email);
        ResetChallenge challenge = resetChallenges.get(normalizedEmail);
        if (challenge == null) {
            throw invalidResetCode();
        }

        synchronized (challenge) {
            Instant now = Instant.now();
            if (challenge.expiresAt.isBefore(now)
                    || challenge.failedAttempts >= MAX_RESET_VERIFICATION_ATTEMPTS) {
                removeResetChallenge(normalizedEmail, challenge);
                throw invalidResetCode();
            }
            if (challenge.otpHash == null) {
                throw invalidResetCode();
            }

            challenge.failedAttempts++;
            byte[] candidateHash = hashResetCode(challenge.salt, code);
            if (!MessageDigest.isEqual(challenge.otpHash, candidateHash)) {
                if (challenge.failedAttempts >= MAX_RESET_VERIFICATION_ATTEMPTS) {
                    removeResetChallenge(normalizedEmail, challenge);
                }
                throw invalidResetCode();
            }

            challenge.otpHash = null;
            byte[] tokenBytes = new byte[32];
            secureRandom.nextBytes(tokenBytes);
            String resetToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
            String tokenHash = hashResetToken(resetToken);
            challenge.resetTokenHash = tokenHash;
            challenge.resetTokenExpiresAt = now.plus(RESET_AUTHORIZATION_LIFETIME);
            resetAuthorizationOwners.put(tokenHash, normalizedEmail);
            return resetToken;
        }
    }

    @Transactional
    public void completePasswordReset(String resetToken, String newPassword) {
        validateResetPassword(newPassword);
        String tokenHash = hashResetToken(resetToken);
        String email = resetAuthorizationOwners.get(tokenHash);
        ResetChallenge challenge = email == null ? null : resetChallenges.get(email);
        if (challenge == null) {
            throw invalidResetAuthorization();
        }

        synchronized (challenge) {
            if (!tokenHash.equals(challenge.resetTokenHash)
                    || challenge.resetTokenExpiresAt == null
                    || challenge.resetTokenExpiresAt.isBefore(Instant.now())) {
                removeResetChallenge(email, challenge);
                throw invalidResetAuthorization();
            }

            User user = userRepository.findByEmail(email).orElse(null);
            if (user == null || !user.isLocalPasswordEnabled()) {
                removeResetChallenge(email, challenge);
                throw invalidResetAuthorization();
            }

            user.setPassword(passwordEncoder.encode(newPassword));
            userRepository.save(user);
            removeResetChallenge(email, challenge);
        }
    }

    private void enforceResetRequestLimit(String key, int maximum, Duration window, Duration cooldown) {
        ResetRequestLimit limit = resetRequestLimits.computeIfAbsent(key, ignored -> new ResetRequestLimit());
        synchronized (limit) {
            Instant now = Instant.now();
            if (limit.windowStartedAt == null || !now.isBefore(limit.windowStartedAt.plus(window))) {
                limit.windowStartedAt = now;
                limit.lastRequestAt = null;
                limit.requestCount = 0;
            }

            if (limit.requestCount >= maximum
                    || (limit.lastRequestAt != null && !cooldown.isZero()
                        && now.isBefore(limit.lastRequestAt.plus(cooldown)))) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "Too many password recovery attempts. Please try again later.");
            }

            limit.requestCount++;
            limit.lastRequestAt = now;
        }
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid email address is required.");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private byte[] hashResetCode(byte[] salt, String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            return digest.digest(code.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String hashResetToken(String token) {
        if (token == null || token.isBlank()) {
            throw invalidResetAuthorization();
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private void validateResetPassword(String password) {
        if (password == null || password.length() < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password must be at least 8 characters and no more than 72 UTF-8 bytes.");
        }

        boolean hasLower = false;
        boolean hasUpper = false;
        boolean hasDigit = false;
        for (char character : password.toCharArray()) {
            hasLower |= Character.isLowerCase(character);
            hasUpper |= Character.isUpperCase(character);
            hasDigit |= Character.isDigit(character);
        }
        if (!hasLower || !hasUpper || !hasDigit) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password must include an uppercase letter, a lowercase letter, and a number.");
        }
    }

    private ResponseStatusException invalidResetCode() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired verification code.");
    }

    private ResponseStatusException invalidResetAuthorization() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password reset authorization is invalid or expired.");
    }

    private void removeResetChallenge(String email, ResetChallenge challenge) {
        resetChallenges.remove(email, challenge);
        removeResetAuthorization(challenge, email);
    }

    private void removeResetAuthorization(ResetChallenge challenge, String email) {
        if (challenge != null && challenge.resetTokenHash != null) {
            resetAuthorizationOwners.remove(challenge.resetTokenHash, email);
        }
    }

    private void cleanExpiredResetState() {
        Instant now = Instant.now();
        resetChallenges.forEach((email, challenge) -> {
            synchronized (challenge) {
                Instant expiresAt = challenge.resetTokenExpiresAt != null
                        ? challenge.resetTokenExpiresAt : challenge.expiresAt;
                if (expiresAt.isBefore(now)) {
                    removeResetChallenge(email, challenge);
                }
            }
        });

        if (resetRequestLimits.size() > 10_000) {
            resetRequestLimits.forEach((key, limit) -> {
                synchronized (limit) {
                    if (limit.windowStartedAt == null
                            || limit.windowStartedAt.plus(RESET_RATE_WINDOW.multipliedBy(2)).isBefore(now)) {
                        resetRequestLimits.remove(key, limit);
                    }
                }
            });
        }
    }

    private static class ResetChallenge {
        private final byte[] salt;
        private byte[] otpHash;
        private final Instant expiresAt;
        private int failedAttempts;
        private String resetTokenHash;
        private Instant resetTokenExpiresAt;

        private ResetChallenge(byte[] salt, byte[] otpHash, Instant expiresAt) {
            this.salt = salt;
            this.otpHash = otpHash;
            this.expiresAt = expiresAt;
        }
    }

    private static class ResetRequestLimit {
        private Instant windowStartedAt;
        private Instant lastRequestAt;
        private int requestCount;
    }
}
