package io.nop.auth.core.jwt;

import io.nop.auth.core.login.AuthToken;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestJwtHelper {
    @Test
    public void testPublicKey() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();

        String token = JwtHelper.genToken(keyPair.getPrivate(), "test", "abc", "3", 10000);
        AuthToken parsed = JwtHelper.parseToken(keyPair.getPublic(), token);
        assertEquals("abc", parsed.getUserName());
    }
}
