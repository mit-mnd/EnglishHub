package com.englishhub.security;

import com.englishhub.common.config.AppProperties;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = Base64.getEncoder().encodeToString(new byte[48]);
    private static final Instant NOW = Instant.parse("2026-10-14T08:00:00Z");

    @Test
    void generatedTokenCanBeParsedBack() {
        JwtService service = service(SECRET, Clock.fixed(NOW, ZoneOffset.UTC));
        String token = service.generateAccessToken(7L, "a@b.com", List.of("LEARNER", "ADMIN"));

        UserPrincipal principal = service.parse(token);

        assertThat(principal.id()).isEqualTo(7L);
        assertThat(principal.email()).isEqualTo("a@b.com");
        assertThat(principal.roles()).isEqualTo(Set.of("LEARNER", "ADMIN"));
        assertThat(service.accessTokenTtlSeconds()).isEqualTo(900);
    }

    @Test
    void expiredTokenIsRejected() {
        String token = service(SECRET, Clock.fixed(NOW, ZoneOffset.UTC))
                .generateAccessToken(1L, "a@b.com", List.of("LEARNER"));
        JwtService later = service(SECRET, Clock.fixed(NOW.plus(Duration.ofMinutes(16)), ZoneOffset.UTC));

        assertThatThrownBy(() -> later.parse(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        String otherSecret = Base64.getEncoder().encodeToString("x".repeat(48).getBytes());
        String token = service(otherSecret, Clock.fixed(NOW, ZoneOffset.UTC))
                .generateAccessToken(1L, "a@b.com", List.of("ADMIN"));

        assertThatThrownBy(() -> service(SECRET, Clock.fixed(NOW, ZoneOffset.UTC)).parse(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void shortSecretFailsFast() {
        String shortSecret = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> service(shortSecret, Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("256 bit");
    }

    private static JwtService service(String secret, Clock clock) {
        AppProperties props = new AppProperties("http://localhost:5173",
                new AppProperties.Cors(List.of("http://localhost:5173")),
                new AppProperties.Jwt(secret, "englishhub", Duration.ofMinutes(15), Duration.ofDays(7)),
                new AppProperties.Security(Duration.ofMinutes(30)),
                new AppProperties.Placement(Duration.ofSeconds(30)),
                new AppProperties.Dictionary("none", "http://localhost", Duration.ofSeconds(1)),
                new AppProperties.Storage("./target/test-uploads"),
                new AppProperties.Ai(new AppProperties.Ai.Speech("mock", null, null, Duration.ofSeconds(1)),
                        new AppProperties.Ai.Writing("rule-based")),
                new AppProperties.Srs(20));
        return new JwtService(props, clock);
    }
}
