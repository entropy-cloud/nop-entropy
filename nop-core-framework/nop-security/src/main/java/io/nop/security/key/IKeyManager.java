package io.nop.security.key;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.util.List;

public interface IKeyManager {
    Certificate getCertificate(String certId);

    default PublicKey getPublicKey(String keyId) {
        Certificate cert = getCertificate(keyId);
        return cert == null ? null : cert.getPublicKey();
    }

    PrivateKey getPrivateKey(String keyId);

    List<String> getCertificateIds();
}