package com.example.orders.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    @Test
    void generatesParseableTokenWithRoles() {
        AppSecurityProperties props = new AppSecurityProperties();
        props.getJwt().setSecret("0123456789-0123456789-0123456789-0123456789");
        props.getJwt().setExpirationSeconds(60);

        JwtService service = new JwtService(props);

        String token = service.generateToken("admin", List.of("USER", "ADMIN"));

        Claims claims = Jwts.parser().verifyWith(service.getKey()).build().parseSignedClaims(token).getPayload();
        assertThat(claims.getSubject()).isEqualTo("admin");
        assertThat(claims.get("roles", List.class)).containsExactlyInAnyOrder("USER", "ADMIN");
    }
}
