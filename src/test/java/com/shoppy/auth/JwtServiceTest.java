package com.shoppy.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shoppy.config.AppProperties;
import io.jsonwebtoken.security.WeakKeyException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-that-is-at-least-32-chars";

    @Test
    void tokenRoundTripsTheUserId() {
        JwtService jwt = new JwtService(props(SECRET, "1d"));

        String token = jwt.generate(42L);

        assertThat(jwt.parseUserId(token)).contains(42L);
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String forged = new JwtService(props("another-secret-that-is-also-32-chars-long", "1d")).generate(42L);

        assertThat(new JwtService(props(SECRET, "1d")).parseUserId(forged)).isEmpty();
    }

    @Test
    void expiredTokenIsRejected() throws InterruptedException {
        JwtService jwt = new JwtService(props(SECRET, "1ms"));
        String token = jwt.generate(42L);

        Thread.sleep(20);

        assertThat(jwt.parseUserId(token)).isEmpty();
    }

    @Test
    void garbageIsRejectedRatherThanThrowing() {
        JwtService jwt = new JwtService(props(SECRET, "1d"));

        assertThat(jwt.parseUserId("not-a-jwt")).isEmpty();
        assertThat(jwt.parseUserId("")).isEmpty();
    }

    @Test
    void shortSecretFailsAtStartup() {
        // HS256 needs a 256-bit key; failing fast beats signing tokens with a guessable key.
        assertThatThrownBy(() -> new JwtService(props("too-short", "1d")))
                .isInstanceOf(WeakKeyException.class);
    }

    @Test
    void expirationIsParsedFromConfig() {
        assertThat(new JwtService(props(SECRET, "10d")).getExpiration()).isEqualTo(Duration.ofDays(10));
    }

    private static AppProperties props(String secret, String expiration) {
        return new AppProperties(
                new AppProperties.JwtProps(secret, expiration),
                new AppProperties.CorsProps(List.of()),
                new AppProperties.StripeProps("sk_test_dummy", "whsec_dummy", "inr"),
                new AppProperties.AwsProps("us-east-1", "bucket", "", "", "", ""));
    }
}
