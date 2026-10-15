package com.englishhub.security;

import com.englishhub.common.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

/** Phát hành và kiểm tra access token JWT (HS256). */
@Service
public class JwtService {

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLES = "roles";
    private static final int MIN_KEY_BYTES = 32;

    private final SecretKey key;
    private final AppProperties.Jwt props;
    private final Clock clock;

    public JwtService(AppProperties appProperties, Clock clock) {
        this.props = appProperties.jwt();
        this.clock = clock;
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(props.secret());
        } catch (RuntimeException e) {
            throw new IllegalStateException("JWT_SECRET phải là chuỗi base64 hợp lệ", e);
        }
        if (keyBytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException("JWT_SECRET phải dài tối thiểu 256 bit (32 byte sau khi decode base64)");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(Long userId, String email, Collection<String> roles) {
        Instant now = clock.instant();
        return Jwts.builder()
                .issuer(props.issuer())
                .subject(String.valueOf(userId))
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_ROLES, List.copyOf(roles))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(props.accessTokenTtl())))
                .signWith(key)
                .compact();
    }

    /**
     * Giải mã và xác thực token.
     *
     * @throws io.jsonwebtoken.ExpiredJwtException nếu token hết hạn
     * @throws JwtException                        nếu chữ ký/định dạng không hợp lệ
     */
    public UserPrincipal parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(props.issuer())
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();

        List<?> roles = claims.get(CLAIM_ROLES, List.class);
        HashSet<String> roleSet = new HashSet<>();
        if (roles != null) {
            roles.forEach(r -> roleSet.add(String.valueOf(r)));
        }
        try {
            return new UserPrincipal(Long.valueOf(claims.getSubject()), claims.get(CLAIM_EMAIL, String.class), roleSet);
        } catch (NumberFormatException e) {
            throw new JwtException("Subject không hợp lệ");
        }
    }

    public long accessTokenTtlSeconds() {
        return props.accessTokenTtl().toSeconds();
    }
}
