package io.nop.security.utils;

import io.nop.api.core.exceptions.NopException;
import io.nop.security.rsa.CertInfo;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.sql.Date;

public class BcCertHelper extends BcHelper {

    public static X509Certificate genSelfSignedCert(CertInfo certInfo, KeyPair keyPair) {
        try {
            // 生成自签名证书
            // 设置证书的基本信息
            X500Name dnName = new X500Name(certInfo.toX500Name());
            BigInteger certSerialNumber = BigInteger.valueOf(System.currentTimeMillis());

            X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                    dnName, certSerialNumber, Date.valueOf(certInfo.getBeginDate()), Date.valueOf(certInfo.getEndDate()),
                    dnName, keyPair.getPublic());

            // 使用私钥进行签名
            ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256WithRSAEncryption").build(keyPair.getPrivate());
            X509Certificate cert = new JcaX509CertificateConverter().setProvider("BC")
                    .getCertificate(certBuilder.build(contentSigner));

            return cert;
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }
}
