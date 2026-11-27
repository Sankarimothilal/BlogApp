package com.blogsphere.controller;

import com.blogsphere.dto.request.LoginRequest;
import com.blogsphere.dto.request.PasswordResetCompletionRequest;
import com.blogsphere.dto.request.PasswordResetRequest;
import com.blogsphere.dto.request.PasswordResetVerificationRequest;
import com.blogsphere.dto.request.RegisterRequest;
import com.blogsphere.dto.response.ApiResponse;
import com.blogsphere.dto.response.JwtResponse;
import com.blogsphere.entity.User;
import com.blogsphere.service.AuthService;
import com.blogsphere.security.JwtUtils;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtUtils jwtUtils;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<JwtResponse>> register(@Valid @RequestBody RegisterRequest registerRequest) {
        JwtResponse response = authService.registerUser(registerRequest);
        return ResponseEntity.ok(ApiResponse.ok("User registered successfully", response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<JwtResponse>> login(@Valid @RequestBody LoginRequest loginRequest) {
        JwtResponse response = authService.authenticateUser(loginRequest);
        return ResponseEntity.ok(ApiResponse.ok("Login successful", response));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body(ApiResponse.error("Not authenticated"));
        }

        String token = authorization.substring(7);
        if (!jwtUtils.validateJwtToken(token)) {
            return ResponseEntity.status(401).body(ApiResponse.error("Not authenticated"));
        }

        jwtUtils.revokeJwtToken(token);
        return ResponseEntity.ok(ApiResponse.ok("Logout successful", null));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody PasswordResetRequest body, HttpServletRequest request) {
        authService.requestPasswordReset(body.getEmail(), request.getRemoteAddr());
        return ResponseEntity.accepted().body(ApiResponse.ok(
                "If this address is eligible for password recovery, follow the email instructions if received.", null));
    }

    @PostMapping("/verify-code")
    public ResponseEntity<ApiResponse<Map<String, String>>> verifyCode(
            @Valid @RequestBody PasswordResetVerificationRequest body) {
        String resetToken = authService.verifyResetCode(body.getEmail(), body.getCode());
        return ResponseEntity.ok(ApiResponse.ok("Verification successful", Map.of("resetToken", resetToken)));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<String>> resetPassword(
            @Valid @RequestBody PasswordResetCompletionRequest body) {
        authService.completePasswordReset(body.getResetToken(), body.getNewPassword());
        return ResponseEntity.ok(ApiResponse.ok("Password has been reset successfully. You can now login."));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<User>> getCurrentUser() {
        User user = authService.getCurrentAuthenticatedUser();
        if (user == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Not authenticated"));
        }
        return ResponseEntity.ok(ApiResponse.ok(user));
    }
}
