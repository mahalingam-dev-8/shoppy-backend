package com.shoppy.auth;

import com.shoppy.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies the session JWT. The payload keeps the NestJS shape ({@code userid}) and
 * always carries {@code exp}, which the frontend reads with jwtDecode to set its own cookie expiry.
 */
@Service
public class JwtService {

    private static final String USER_ID_CLAIM = "userid";

    private final SecretKey key;
    private final Duration expiration;

    public JwtService(AppProperties props) {
        // Throws WeakKeyException at startup if JWT_SECRET is shorter than 32 characters.
        this.key = Keys.hmacShaKeyFor(props.jwt().secret().getBytes(StandardCharsets.UTF_8));
        this.expiration = DurationParser.parse(props.jwt().expiration());
    }

    public String generate(long userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .claim(USER_ID_CLAIM, userId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(key)
                .compact();
    }

    /** The user id from a valid, unexpired token; empty for anything else. */
    public Optional<Long> parseUserId(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return claims.get(USER_ID_CLAIM) instanceof Number id
                    ? Optional.of(id.longValue())
                    : Optional.empty();
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public Duration getExpiration() {
        return expiration;
    }
}
