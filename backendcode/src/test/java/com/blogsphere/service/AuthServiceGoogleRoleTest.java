package com.blogsphere.service;

import com.blogsphere.entity.Role;
import com.blogsphere.entity.User;
import com.blogsphere.repository.UserRepository;
import com.blogsphere.security.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceGoogleRoleTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtils jwtUtils;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        when(jwtUtils.generateJwtToken(any(Authentication.class))).thenReturn("test-jwt");
    }

    @Test
    void newGoogleAccountsReceiveReaderRole() {
        when(userRepository.findByEmail("new.google@example.test")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(42L);
            return user;
        });

        var response = authService.authenticateGoogleUser(
                "new.google@example.test", "New Google User", "https://example.test/avatar.png");

        assertEquals(Role.ROLE_READER.name(), response.getRole());
        var savedUser = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertEquals(Role.ROLE_READER, savedUser.getValue().getRole());
        assertFalse(savedUser.getValue().isLocalPasswordEnabled());
    }

    @Test
    void existingGoogleLinkedAccountKeepsItsAssignedRole() {
        User existingAuthor = new User(
                "Existing Author", "existing.google@example.test", "encoded-password", Role.ROLE_AUTHOR);
        existingAuthor.setId(43L);
        when(userRepository.findByEmail("existing.google@example.test")).thenReturn(Optional.of(existingAuthor));

        var response = authService.authenticateGoogleUser(
                "existing.google@example.test", "Updated Name", "https://example.test/avatar.png");

        assertEquals(Role.ROLE_AUTHOR.name(), response.getRole());
        verify(userRepository, never()).save(any(User.class));
    }
}
