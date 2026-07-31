package io.nop.auth.sso;

import io.nop.auth.sso.jwk.ECPublicJWK;
import io.nop.auth.sso.jwk.JWK;
import io.nop.auth.sso.jwk.JWKSUtils;
import io.nop.auth.sso.jwk.JSONWebKeySet;
import io.nop.auth.sso.jwk.KeyType;
import io.nop.auth.sso.jwk.RSAPublicJWK;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

public class TestJwkKeyParsing {

    @Test
    public void testRsaJwkToPublicKey() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair kp = gen.generateKeyPair();
        RSAPublicKey original = (RSAPublicKey) kp.getPublic();

        RSAPublicJWK jwk = new RSAPublicJWK();
        jwk.setKeyType(KeyType.RSA);
        jwk.setPublicKeyUse(JWK.Use.SIG.asString());
        jwk.setOtherClaims(RSAPublicJWK.MODULUS,
                Base64.getUrlEncoder().withoutPadding().encodeToString(original.getModulus().toByteArray()));
        jwk.setOtherClaims(RSAPublicJWK.PUBLIC_EXPONENT,
                Base64.getUrlEncoder().withoutPadding().encodeToString(original.getPublicExponent().toByteArray()));

        PublicKey result = jwk.toPublicKey();
        assertTrue(result instanceof RSAPublicKey);
        RSAPublicKey rsaResult = (RSAPublicKey) result;
        assertEquals(original.getModulus(), rsaResult.getModulus());
        assertEquals(original.getPublicExponent(), rsaResult.getPublicExponent());
    }

    @Test
    public void testEcJwkToPublicKey() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair kp = gen.generateKeyPair();
        ECPublicKey original = (ECPublicKey) kp.getPublic();

        ECPublicJWK jwk = new ECPublicJWK();
        jwk.setKeyType(KeyType.EC);
        jwk.setPublicKeyUse(JWK.Use.SIG.asString());
        jwk.setOtherClaims(ECPublicJWK.CRV, "P-256");
        // JWK.createECPublicKey uses decodeBase64 (standard base64) for EC coordinates
        jwk.setOtherClaims(ECPublicJWK.X,
                Base64.getEncoder().encodeToString(original.getW().getAffineX().toByteArray()));
        jwk.setOtherClaims(ECPublicJWK.Y,
                Base64.getEncoder().encodeToString(original.getW().getAffineY().toByteArray()));

        PublicKey result = jwk.toPublicKey();
        assertTrue(result instanceof ECPublicKey);
        ECPublicKey ecResult = (ECPublicKey) result;
        assertEquals(original.getW().getAffineX(), ecResult.getW().getAffineX());
        assertEquals(original.getW().getAffineY(), ecResult.getW().getAffineY());
    }

    @Test
    public void testJwkUtilsGetKeysForUse() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair kp = gen.generateKeyPair();
        RSAPublicKey pubKey = (RSAPublicKey) kp.getPublic();

        String encodedModulus = Base64.getUrlEncoder().withoutPadding().encodeToString(pubKey.getModulus().toByteArray());
        String encodedExponent = Base64.getUrlEncoder().withoutPadding().encodeToString(pubKey.getPublicExponent().toByteArray());

        RSAPublicJWK sigKey = new RSAPublicJWK();
        sigKey.setKeyId("sig-key-1");
        sigKey.setKeyType(KeyType.RSA);
        sigKey.setPublicKeyUse(JWK.Use.SIG.asString());
        sigKey.setOtherClaims(RSAPublicJWK.MODULUS, encodedModulus);
        sigKey.setOtherClaims(RSAPublicJWK.PUBLIC_EXPONENT, encodedExponent);

        RSAPublicJWK encKey = new RSAPublicJWK();
        encKey.setKeyId("enc-key-1");
        encKey.setKeyType(KeyType.RSA);
        encKey.setPublicKeyUse(JWK.Use.ENCRYPTION.asString());
        encKey.setOtherClaims(RSAPublicJWK.MODULUS, encodedModulus);
        encKey.setOtherClaims(RSAPublicJWK.PUBLIC_EXPONENT, encodedExponent);

        JSONWebKeySet keySet = new JSONWebKeySet();
        keySet.setKeys(Arrays.asList(sigKey, encKey));

        // Should only return sig keys
        var sigKeys = JWKSUtils.getKeysForUse(keySet, JWK.Use.SIG);
        assertEquals(1, sigKeys.size());
        assertTrue(sigKeys.containsKey("sig-key-1"));

        // Should only return enc keys
        var encKeys = JWKSUtils.getKeysForUse(keySet, JWK.Use.ENCRYPTION);
        assertEquals(1, encKeys.size());
        assertTrue(encKeys.containsKey("enc-key-1"));
    }

    @Test
    public void testThumbprintGeneration() throws Exception {
        // A known test certificate (PEM)
        String certPem =
                "-----BEGIN CERTIFICATE-----\n" +
                "MIIDazCCAlMCFAjU7FkO8x6H/7kY1w0W2O+2mPcbMA0GCSqGSIb3DQEBCwUAMHgx\n" +
                "CzAJBgNVBAYTAlVTMRMwEQYDVQQIDApDYWxpZm9ybmlhMRIwEAYDVQQHDAlTYW4g\n" +
                "RGllZ28xEjAQBgNVBAoMCU5vcFNvZnR3YXJlMQswCQYDVQQLDAJJVDEfMB0GA1UE\n" +
                "AwwWdGVzdC5leGFtcGxlLm5vcC1haS5pbzAeFw0yNjA3MjgxMjAwMDBaFw0yNzA3\n" +
                "MjgxMjAwMDBaMHgxCzAJBgNVBAYTAlVTMRMwEQYDVQQIDApDYWxpZm9ybmlhMRIw\n" +
                "EAYDVQQHDAlTYW4gRGllZ28xEjAQBgNVBAoMCU5vcFNvZnR3YXJlMQswCQYDVQQL\n" +
                "DAJJVDEfMB0GA1UEAwwWdGVzdC5leGFtcGxlLm5vcC1haS5pbzCCASIwDQYJKoZI\n" +
                "hvcNAQEBBQADggEPADCCAQoCggEBAK+UInq2ug0wvJZLp0Opm7GLqTnJtJ0qk/he\n" +
                "XNmbFnVas5G5OVY9y7wrK9r+3Ll9FWc8vlpa7oBH5x23HmSqAqKBKiMSd202nRGm\n" +
                "EmdV+PEhmRRvBN413sc7MxSqHqB/rsMqSDpeFrY6fy7BkYLxGf8IPIGzhARFjVnx\n" +
                "gt78QuFAM5FLKat/+sB8r8o4Cc4O6T5BhSbQ6v/q9KL7CW8sDB1m3bsVG30gIoMA\n" +
                "JXYDKm/0tGJOkoWfW8ov4WMhrHiEJUCqPKJX2TaVBaAu8aOVkGAfSqHJD4YfwQey\n" +
                "zgktQhl6gqUKrG5v8lWRecjIqK8vz1AROR2n52S5KQ48oc6qPY8CAwEAATANBgkq\n" +
                "hkiG9w0BAQsFAAOCAQEAtvLzvCWhHZzFwwCAQU8AESPCSDOL0Nr9aUxc8fZC5obW\n" +
                "X2Rg6UUx+rnbA2+Ru2zHyEAmyM6ug0vVyqB4YTPKPbCtqwi6qTRq5Lgw/ppB3n71\n" +
                "oESzP0LTkFKFDpF/Y57VOY5tBAP227KNRMHSSPFyjxVBAeYXWwao3XzO/lQqJWNd\n" +
                "5M/UtVrd+45ph1T+ebK5YnnK4R+Gs9mc5iIpkFjHbLBaDj2rHKNMBzCMfptqTqnk\n" +
                "GurLMfmzg3Cm/W0ASYhAOARldWQxG4HFtBC6qEOFJoCIGkgfPs0+whmx/7mRYNZs\n" +
                "+MVsSXOiCwXSjB1bVx1VPD6qMF8v1j5s3wFp4pDX5Q==\n" +
                "-----END CERTIFICATE-----";

        String[] chain = new String[]{certPem};
        String sha1 = RSAPublicJWK.generateThumbprint(chain, "SHA-1");
        String sha256 = RSAPublicJWK.generateThumbprint(chain, "SHA-256");

        assertNotNull(sha1);
        assertNotNull(sha256);
        assertFalse(sha1.isEmpty());
        assertFalse(sha256.isEmpty());
        assertNotEquals(sha1, sha256);

        // Verify they look like valid base64url (no padding chars, only url-safe chars)
        assertFalse(sha1.contains("="));
        assertFalse(sha256.contains("="));
    }
}