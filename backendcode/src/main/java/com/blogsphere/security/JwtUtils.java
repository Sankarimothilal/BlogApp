package com.blogsphere.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class JwtUtils {

    private static final long REVOCATION_CLEANUP_INTERVAL_MS = 60_000L;
    private final Map<String, Long> revokedTokenExpirations = new ConcurrentHashMap<>();
    private final AtomicLong nextRevocationCleanupAt = new AtomicLong();

    @Value("${blogsphere.jwt.secret}")
    private String jwtSecret;

    @Value("${blogsphere.jwt.expiration-ms}")
    private int jwtExpirationMs;

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateJwtToken(Authentication authentication) {
        UserDetailsImpl userPrincipal = (UserDetailsImpl) authentication.getPrincipal();

        return Jwts.builder()
                .subject(userPrincipal.getUsername())
                .claim("id", userPrincipal.getId())
                .claim("name", userPrincipal.getFullName())
                .claim("role", userPrincipal.getRole())
                .issuedAt(new Date())
                .expiration(new Date((new Date()).getTime() + jwtExpirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    public String getUserNameFromJwtToken(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean validateJwtToken(String authToken) {
        try {
            Claims claims = parseClaims(authToken);
            Date expiration = claims.getExpiration();
            long now = System.currentTimeMillis();
            if (expiration == null || expiration.getTime() <= now) {
                return false;
            }

            removeExpiredRevocations(now);
            Long revokedUntil = revokedTokenExpirations.get(fingerprint(authToken));
            return revokedUntil == null || revokedUntil <= now;
        } catch (JwtException | IllegalArgumentException e) {
            // Invalid JWT token
        }
        return false;
    }

    public void revokeJwtToken(String token) {
        Claims claims = parseClaims(token);
        Date expiration = claims.getExpiration();
        long now = System.currentTimeMillis();
        if (expiration == null || expiration.getTime() <= now) {
            throw new IllegalArgumentException("Only an unexpired JWT can be revoked.");
        }

        removeExpiredRevocations(now);
        revokedTokenExpirations.put(fingerprint(token), expiration.getTime());
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private String fingerprint(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable.", e);
        }
    }

    private void removeExpiredRevocations(long now) {
        long nextCleanup = nextRevocationCleanupAt.get();
        if (now < nextCleanup || !nextRevocationCleanupAt.compareAndSet(
                nextCleanup, now + REVOCATION_CLEANUP_INTERVAL_MS)) {
            return;
        }
        revokedTokenExpirations.entrySet().removeIf(entry -> entry.getValue() <= now);
    }
}
