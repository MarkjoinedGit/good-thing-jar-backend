package com.goodthingjar.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private static final String TOKEN_TYPE_CLAIM = "tokenType";
    private static final String USER_ID_CLAIM = "userId";
    private static final String EMAIL_CLAIM = "email";
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_TYPE = "refresh";

    private final SecretKey signingKey;
    private final long accessTokenExpirySeconds;
    private final long refreshTokenExpirySeconds;
    private final String issuer;
    private final Clock clock;

    @Autowired
    public JwtTokenProvider(
        @Value("${app.security.jwt.secret}") String jwtSecret,
        @Value("${app.security.jwt.access-token-expiry-seconds}") long accessTokenExpirySeconds,
        @Value("${app.security.jwt.refresh-token-expiry-seconds}") long refreshTokenExpirySeconds,
        @Value("${app.security.jwt.issuer}") String issuer
    ) {
        this(jwtSecret, accessTokenExpirySeconds, refreshTokenExpirySeconds, issuer, Clock.systemUTC());
    }

    public JwtTokenProvider(
        String jwtSecret,
        long accessTokenExpirySeconds,
        long refreshTokenExpirySeconds,
        String issuer,
        Clock clock
    ) {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalArgumentException("JWT secret must not be blank");
        }
        if (accessTokenExpirySeconds <= 0 || refreshTokenExpirySeconds <= 0) {
            throw new IllegalArgumentException("Token expiry values must be greater than zero");
        }

        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenExpirySeconds = accessTokenExpirySeconds;
        this.refreshTokenExpirySeconds = refreshTokenExpirySeconds;
        this.issuer = issuer;
        this.clock = clock;
    }

    public String generateAccessToken(UUID userId, String email) {
        return generateToken(userId, email, ACCESS_TOKEN_TYPE, accessTokenExpirySeconds);
    }

    public String generateRefreshToken(UUID userId, String email) {
        return generateToken(userId, email, REFRESH_TOKEN_TYPE, refreshTokenExpirySeconds);
    }

    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }

        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    public String extractSubject(String token) {
        return parseClaims(token).getSubject();
    }

    public UUID extractUserId(String token) {
        String userId = parseClaims(token).get(USER_ID_CLAIM, String.class);
        return UUID.fromString(userId);
    }

    public String extractEmail(String token) {
        return parseClaims(token).get(EMAIL_CLAIM, String.class);
    }

    public Instant extractExpiration(String token) {
        return parseClaims(token).getExpiration().toInstant();
    }

    public boolean isAccessToken(String token) {
        return ACCESS_TOKEN_TYPE.equals(parseClaims(token).get(TOKEN_TYPE_CLAIM, String.class));
    }

    public boolean isRefreshToken(String token) {
        return REFRESH_TOKEN_TYPE.equals(parseClaims(token).get(TOKEN_TYPE_CLAIM, String.class));
    }

    public boolean isTokenExpired(String token) {
        try {
            Instant expiresAt = extractExpiration(token);
            return expiresAt.isBefore(clock.instant());
        } catch (ExpiredJwtException ex) {
            return true;
        }
    }

    private String generateToken(UUID userId, String email, String tokenType, long expirySeconds) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID must not be null");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email must not be blank");
        }

        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plusSeconds(expirySeconds);

        return Jwts.builder()
            .issuer(issuer)
            .subject(userId.toString())
            .claim(USER_ID_CLAIM, userId.toString())
            .claim(EMAIL_CLAIM, email)
            .claim(TOKEN_TYPE_CLAIM, tokenType)
            .issuedAt(Date.from(issuedAt))
            .expiration(Date.from(expiresAt))
            .signWith(signingKey, Jwts.SIG.HS256)
            .compact();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
            .clock(() -> Date.from(clock.instant()))
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}
