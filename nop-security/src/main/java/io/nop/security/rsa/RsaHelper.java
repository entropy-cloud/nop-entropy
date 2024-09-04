package io.nop.security.rsa;

import io.nop.api.core.exceptions.NopException;
import io.nop.security.SecurityConstants;
import io.nop.security.utils.BcCertHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

public class RsaHelper {

    public static void genKeyPair(CertInfo certInfo, File store, String storePassword, String keyPrefix) {
        try {
            // 生成RSA密钥对
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            KeyPair keyPair = keyGen.generateKeyPair();

            // 生成自签名证书
            X509Certificate cert = BcCertHelper.genSelfSignedCert(certInfo, keyPair);

            // 将密钥对和证书保存到JKS文件
            KeyStore keyStore = KeyStore.getInstance(SecurityConstants.KEY_STORE_JKS);
            keyStore.load(null, null);
            keyStore.setKeyEntry(keyPrefix + SecurityConstants.PRIVATE_KEY_POSTFIX, keyPair.getPrivate(),
                    storePassword.toCharArray(), new Certificate[]{cert});
            keyStore.setCertificateEntry(keyPrefix + SecurityConstants.PUBLIC_KEY_POSTFIX, cert);

            try (FileOutputStream fos = new FileOutputStream(store)) {
                keyStore.store(fos, storePassword.toCharArray());
            }

        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }
}
