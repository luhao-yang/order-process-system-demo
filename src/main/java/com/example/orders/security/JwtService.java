package com.example.orders.security;

import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationSeconds;

    public JwtService(AppSecurityProperties props) {
        // Force HS256 by truncating/extending the key spec to a 256-bit (32-byte) HMAC key
        // so the issuer (JJWT) and the resource-server (Nimbus, defaults to HS256) agree.
        byte[] raw = props.getJwt().getSecret().getBytes(StandardCharsets.UTF_8);
        if (raw.length < 32) {
            throw new IllegalStateException("app.security.jwt.secret must be at least 32 bytes for HS256");
        }
        this.key = new SecretKeySpec(raw, "HmacSHA256");
        this.expirationSeconds = props.getJwt().getExpirationSeconds();
    }

    public String generateToken(String username, List<String> roles) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public SecretKey getKey() { return key; }

    public long getExpirationSeconds() { return expirationSeconds; }
}
