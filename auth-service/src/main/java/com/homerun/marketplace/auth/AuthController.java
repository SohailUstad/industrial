package com.homerun.marketplace.auth;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@RestController
public class AuthController {
    private final String username;
    private final String passwordHash;
    private final String issuer;
    private final String audience;
    private final JwtKeys keys;
    private final NimbusJwtEncoder encoder;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();

    public AuthController(JwtKeys keys,
                          @Value("${app.admin.username}") String username,
                          @Value("${app.admin.password-hash}") String passwordHash,
                          @Value("${app.admin.dev-password}") String devPassword,
                          @Value("${app.jwt.issuer}") String issuer,
                          @Value("${app.jwt.audience}") String audience) {
        if (passwordHash.isBlank() && devPassword.isBlank())
            throw new IllegalStateException("Set ADMIN_PASSWORD_HASH, or ADMIN_PASSWORD for local development");
        this.keys = keys;
        this.username = username;
        this.passwordHash = passwordHash.isBlank() ? passwords.encode(devPassword) : passwordHash;
        this.issuer = issuer;
        this.audience = audience;
        this.encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(keys.signingKey())));
    }

    public record Login(@NotBlank String username, @NotBlank String password) {}
    public record Token(String accessToken, String tokenType, long expiresIn) {}

    @PostMapping("/api/auth/login")
    public Token login(@Valid @RequestBody Login input) {
        boolean passwordCorrect = passwords.matches(input.password(), passwordHash);
        if (!username.equals(input.username()) || !passwordCorrect)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer(issuer).subject(username)
                .audience(List.of(audience)).issuedAt(now).notBefore(now)
                .expiresAt(now.plus(15, ChronoUnit.MINUTES)).claim("scope", "admin").build();
        var header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(keys.signingKey().getKeyID()).build();
        String jwt = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new Token(jwt, "Bearer", 900);
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() { return new JWKSet(keys.publicKey()).toJSONObject(); }
}
