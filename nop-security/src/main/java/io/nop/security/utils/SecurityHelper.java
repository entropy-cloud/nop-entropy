package io.nop.security.utils;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.security.SecurityConstants;
import io.nop.security.key.KeyBean;

import java.io.InputStream;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;

public class SecurityHelper {

    public static KeyStore loadKeyStore(String keyStoreType,
                                        IResource resource, char[] storePassword) {
        try {
            KeyStore keyStore = KeyStore.getInstance(keyStoreType);
            try (InputStream stream = resource.getInputStream()) {
                keyStore.load(stream, storePassword);
            }
            return keyStore;
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    public static String getPublicKeyId(String keyPrefix) {
        return keyPrefix + SecurityConstants.PUBLIC_KEY_POSTFIX;
    }

    public static String getPrivateKeyId(String keyPrefix) {
        return keyPrefix + SecurityConstants.PRIVATE_KEY_POSTFIX;
    }

    public static byte[] sign(String alg, PrivateKey key, byte[] data) {
        // 使用私钥进行签名
        try {
            Signature signature = Signature.getInstance(alg);
            signature.initSign(key);
            signature.update(data);
            return signature.sign();
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    public static boolean veritySign(String alg, PublicKey key, byte[] data, byte[] sign) {
        // 使用公钥进行验签
        try {
            Signature signature = Signature.getInstance(alg);
            signature.initVerify(key);
            signature.update(data);
            return signature.verify(sign);
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    public static PublicKey toRSAPublicKey(KeyBean keyBean) {
        byte[] modulusBytes = StringHelper.decodeBase64Url(keyBean.getRSAModulus());
        BigInteger modulus = new BigInteger(1, modulusBytes);
        BigInteger publicExponent = new BigInteger(1, StringHelper.decodeBase64Url(keyBean.getRSAExponent()));

        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePublic(new RSAPublicKeySpec(modulus, publicExponent));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}