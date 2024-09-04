package io.nop.security.key;

import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.List;

public class CompositeKeyManager implements IKeyManager {
    private final List<IKeyManager> keyManagers;

    public CompositeKeyManager(List<IKeyManager> keyManagers) {
        this.keyManagers = keyManagers;
    }

    @Override
    public Certificate getCertificate(String certId) {
        for (IKeyManager keyManager : keyManagers) {
            Certificate cert = keyManager.getCertificate(certId);
            if (cert != null)
                return cert;
        }
        return null;
    }

    @Override
    public PrivateKey getPrivateKey(String keyId) {
        for (IKeyManager keyManager : keyManagers) {
            PrivateKey key = keyManager.getPrivateKey(keyId);
            if (key != null)
                return key;
        }
        return null;
    }

    @Override
    public List<String> getCertificateIds() {
        List<String> ret = new ArrayList<>();
        for (IKeyManager keyManager : keyManagers) {
            ret.addAll(keyManager.getCertificateIds());
        }
        return ret;
    }
}
