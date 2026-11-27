package com.blogsphere.security;

import com.blogsphere.dto.response.JwtResponse;
import com.blogsphere.service.AuthService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class GoogleOAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final AuthService authService;
    private final String dashboardUrl;

    public GoogleOAuth2AuthenticationSuccessHandler(
            AuthService authService,
            @Value("${blogsphere.oauth2.dashboard-url}") String dashboardUrl) {
        this.authService = authService;
        this.dashboardUrl = dashboardUrl;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        try {
            OAuth2User googleUser = (OAuth2User) authentication.getPrincipal();
            String email = googleUser.getAttribute("email");
            Boolean emailVerified = googleUser.getAttribute("email_verified");
            if (email == null || !Boolean.TRUE.equals(emailVerified)) {
                response.sendRedirect(dashboardUrl + "?error=google_email_unverified");
                return;
            }

            JwtResponse jwt = authService.authenticateGoogleUser(
                email,
                googleUser.getAttribute("name"),
                googleUser.getAttribute("picture")
            );

            // A URL fragment is not sent to the server in HTTP requests or referrer headers.
            String redirectUrl = dashboardUrl + "#access_token=" + jwt.getToken();
            response.sendRedirect(redirectUrl);
        } catch (RuntimeException exception) {
            response.sendRedirect(dashboardUrl + "?error=google_login_failed");
        }
    }
}
