package io.nop.security.key;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.security.SecurityConstants;
import io.nop.security.utils.SecurityHelper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class DefaultKeyManager implements IKeyManager {
    private String storeType = SecurityConstants.KEY_STORE_JKS;
    private String storePath;
    private char[] storePassword;
    private KeyStore keyStore;

    public void setStorePassword(String storePassword) {
        this.storePassword = storePassword.toCharArray();
    }

    public void setStorePath(String storePath) {
        this.storePath = storePath;
    }

    @PostConstruct
    public void init() {
        IResource resource = VirtualFileSystem.instance().getResource(storePath);
        this.keyStore = SecurityHelper.loadKeyStore(storeType, resource, storePassword);
    }

    @PreDestroy
    public void destroy() {
        if (keyStore != null) {
            keyStore = null;
        }
    }

    @Override
    public Certificate getCertificate(String certId) {
        try {
            return keyStore.getCertificate(certId);
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    @Override
    public PrivateKey getPrivateKey(String keyId) {
        try {
            return (PrivateKey) keyStore.getKey(keyId, storePassword);
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    @Override
    public List<String> getCertificateIds() {
        List<String> ret = new ArrayList<>();
        try {
            Enumeration<String> it = keyStore.aliases();
            while (it.hasMoreElements()) {
                String alias = it.nextElement();
                if (keyStore.isCertificateEntry(alias))
                    ret.add(alias);
            }
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
        return ret;
    }
}
