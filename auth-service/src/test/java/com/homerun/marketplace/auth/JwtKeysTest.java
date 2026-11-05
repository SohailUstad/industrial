package com.homerun.marketplace.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class JwtKeysTest {
    @TempDir Path temp;

    @Test
    void loadsPersistentPemPairAndRejectsMismatchedKeys() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        KeyPair other = generator.generateKeyPair();
        Path privateKey = temp.resolve("private.pem");
        Path publicKey = temp.resolve("public.pem");
        Files.writeString(privateKey, pem("PRIVATE KEY", pair.getPrivate().getEncoded()));
        Files.writeString(publicKey, pem("PUBLIC KEY", pair.getPublic().getEncoded()));

        var loaded = new JwtKeys(privateKey.toString(), publicKey.toString(), false);
        assertNotNull(loaded.signingKey().toRSAPrivateKey());
        assertFalse(loaded.publicKey().toJSONObject().containsKey("d"));

        Files.writeString(publicKey, pem("PUBLIC KEY", other.getPublic().getEncoded()));
        assertThrows(IllegalStateException.class,
                () -> new JwtKeys(privateKey.toString(), publicKey.toString(), false));
    }

    private static String pem(String label, byte[] der) {
        return "-----BEGIN " + label + "-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(der)
                + "\n-----END " + label + "-----\n";
    }
}
