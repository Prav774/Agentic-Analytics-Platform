package com.analytics.security;

import com.analytics.security.entity.Role;
import com.analytics.security.entity.User;
import com.analytics.security.jwt.JwtProperties;
import com.analytics.security.jwt.JwtService;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private static final String TEST_SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final long EXPIRATION_MS = 3600000L; // 1 hour

    private JwtService jwtService;
    private JwtProperties jwtProperties;
    private JwtEncoder encoder;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties(TEST_SECRET, EXPIRATION_MS);

        SecretKey secretKey = new SecretKeySpec(TEST_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JWKSource<SecurityContext> jwkSource = new ImmutableSecret<>(secretKey);
        encoder = new NimbusJwtEncoder(jwkSource);
        JwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        jwtService = new JwtService(encoder, decoder, jwtProperties);
    }

    @Test
    void generateTokenAndDecode_ValidClaims() {
        User user = new User("sarah_analyst", "sarah@example.com", "hash", Role.ANALYST);
        user.setId(42L);

        Instant before = Instant.now().minusSeconds(1);
        String token = jwtService.generateToken(user);
        Instant after = Instant.now().plusSeconds(1);

        assertNotNull(token);
        assertFalse(token.isBlank());
        assertEquals(3, token.split("\\.").length);

        Jwt decoded = jwtService.decode(token);
        assertEquals("sarah_analyst", decoded.getSubject());
        assertEquals(42L, ((Number) decoded.getClaim("userId")).longValue());
        assertEquals("sarah@example.com", decoded.getClaim("email"));
        assertEquals("ANALYST", decoded.getClaim("role"));
        assertEquals(List.of("ROLE_ANALYST"), decoded.getClaim("roles"));
        assertEquals("agentic-analytics-platform", decoded.getClaimAsString("iss"));

        Instant expiresAt = decoded.getExpiresAt();
        assertNotNull(expiresAt);
        assertTrue(expiresAt.isAfter(before.plusMillis(EXPIRATION_MS)));
        assertTrue(expiresAt.isBefore(after.plusMillis(EXPIRATION_MS).plusSeconds(2)));
    }

    @Test
    void decode_TamperedToken_ThrowsJwtException() {
        User user = new User("bob", "bob@example.com", "hash", Role.VIEWER);
        user.setId(1L);

        String token = jwtService.generateToken(user);
        String tampered = token.substring(0, token.lastIndexOf('.') + 1) + "invalidSignature";

        assertThrows(JwtException.class, () -> jwtService.decode(tampered));
    }

    @Test
    void decode_ExpiredToken_ThrowsJwtException() {
        Instant now = Instant.now();
        JwtClaimsSet expiredClaims = JwtClaimsSet.builder()
                .issuer("agentic-analytics-platform")
                .issuedAt(now.minusSeconds(3600))
                .expiresAt(now.minusSeconds(60)) // Expired 1 minute ago
                .subject("expired_user")
                .claim("userId", 99L)
                .claim("role", "VIEWER")
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String expiredToken = encoder.encode(JwtEncoderParameters.from(header, expiredClaims)).getTokenValue();

        assertThrows(JwtException.class, () -> jwtService.decode(expiredToken));
    }

    @Test
    void getExpirationMs_ReturnsConfiguredValue() {
        assertEquals(EXPIRATION_MS, jwtService.getExpirationMs());
    }
}
