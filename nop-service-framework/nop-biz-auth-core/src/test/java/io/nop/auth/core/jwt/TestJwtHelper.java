package io.nop.auth.core.jwt;

import io.nop.api.core.exceptions.NopException;
import io.nop.auth.core.login.AuthToken;
import org.junit.jupiter.api.Test;

import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;

import static io.nop.auth.core.AuthCoreErrors.ERR_JWT_INVALID_TOKEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

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

    @Test
    public void testHMAC() {
        Key key = JwtHelper.hmacKey("test", "nop");
        String token = JwtHelper.genToken(key, "my", "abc", "2", 20000);
        AuthToken parsed = JwtHelper.parseToken(key, token);
        assertEquals("abc", parsed.getUserName());

        try {
            JwtHelper.parseToken(JwtHelper.hmacKey("invalid", "abc"), token);
            fail();
        } catch (NopException e) {
            assertEquals(ERR_JWT_INVALID_TOKEN.getErrorCode(), e.getErrorCode());
        }
    }
}
