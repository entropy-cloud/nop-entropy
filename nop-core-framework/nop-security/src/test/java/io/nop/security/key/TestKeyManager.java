package io.nop.security.key;

import io.nop.commons.util.FileHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.security.SecurityConstants;
import io.nop.security.rsa.CertInfo;
import io.nop.security.rsa.RsaHelper;
import io.nop.security.utils.SecurityHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestKeyManager extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testSign() {
        File store = getTargetFile("test.jks");
        String password = "test";

        CertInfo certInfo = new CertInfo();
        certInfo.setCommonName("www.example.com");
        certInfo.setOrganizationUnit("IT Department");
        certInfo.setOrganization("Example Inc.");
        certInfo.setLocality("New York");
        certInfo.setState("NY");
        certInfo.setCountry("US");
        certInfo.setBeginDate(LocalDate.now());
        certInfo.setEndDate(LocalDate.now().plusDays(365));

        RsaHelper.genKeyPair(certInfo, store, password, "my");

        DefaultKeyManager keyManager = new DefaultKeyManager();
        keyManager.setStorePassword(password);
        keyManager.setStorePath(FileHelper.getFileUrl(store));
        keyManager.init();

        //PublicKey originalKey = keyManager.getPublicKey("my_pub");
        PrivateKey privateKey = keyManager.getPrivateKey("my_pri");
        String data = "123";
        byte[] signed = SecurityHelper.sign(SecurityConstants.ALG_SHA256_WITH_RSA, privateKey, data.getBytes());

        KeySetBean keySet = KeySetHelper.getPublicKeySet(keyManager);
        KeyBean pubKeyBean = keySet.getKeyById("my_pub");
        PublicKey pubKey = SecurityHelper.toRSAPublicKey(pubKeyBean);

        assertTrue(SecurityHelper.veritySign(SecurityConstants.ALG_SHA256_WITH_RSA, pubKey, data.getBytes(), signed));
    }
}
