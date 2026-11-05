package com.homerun.marketplace.auth;

import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;

@Component
public class JwtKeys {
    private final RSAKey key;

    public JwtKeys(@Value("${app.jwt.private-key-path}") String privatePath,
                   @Value("${app.jwt.public-key-path}") String publicPath,
                   @Value("${app.jwt.dev-ephemeral-keys}") boolean allowEphemeral) throws Exception {
        if (privatePath.isBlank() != publicPath.isBlank())
            throw new IllegalStateException("Both JWT_PRIVATE_KEY_PATH and JWT_PUBLIC_KEY_PATH are required");
        RSAPrivateKey privateKey;
        RSAPublicKey publicKey;
        if (!privatePath.isBlank() && !publicPath.isBlank()) {
            var factory = KeyFactory.getInstance("RSA");
            privateKey = (RSAPrivateKey) factory.generatePrivate(new PKCS8EncodedKeySpec(decodePem(Files.readString(Path.of(privatePath)))));
            publicKey = (RSAPublicKey) factory.generatePublic(new X509EncodedKeySpec(decodePem(Files.readString(Path.of(publicPath)))));
        } else if (allowEphemeral) {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            var pair = generator.generateKeyPair();
            privateKey = (RSAPrivateKey) pair.getPrivate();
            publicKey = (RSAPublicKey) pair.getPublic();
        } else {
            throw new IllegalStateException("Set JWT_PRIVATE_KEY_PATH and JWT_PUBLIC_KEY_PATH, or AUTH_DEV_EPHEMERAL_KEYS=true for local development");
        }
        var signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update("homerun-key-check".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] signed = signature.sign();
        signature.initVerify(publicKey);
        signature.update("homerun-key-check".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (!signature.verify(signed)) throw new IllegalStateException("JWT private and public keys do not match");
        String id = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(publicKey.getEncoded())).substring(0, 16);
        this.key = new RSAKey.Builder(publicKey).privateKey(privateKey).keyID(id).build();
    }

    public RSAKey signingKey() { return key; }
    public RSAKey publicKey() { return key.toPublicJWK(); }

    private static byte[] decodePem(String pem) {
        String base64 = pem.replaceAll("-----BEGIN [^-]+-----", "")
                .replaceAll("-----END [^-]+-----", "").replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64);
    }
}
