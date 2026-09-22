package io.nop.security.key;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 组合多个 {@link IKeyManager}，按构造时传入的列表顺序 first-match-wins 解析：
 * {@link #getCertificate}/{@link #getPrivateKey} 返回第一个能提供该 ID 的 manager
 * 的结果。不同 manager 暴露相同 ID 时不会报错——先注册者胜出（装配顺序即优先级），
 * {@link #getCertificateIds()} 会对此类重复 ID 输出 WARN（F-C1-1）。
 */
public class CompositeKeyManager implements IKeyManager {
    private static final Logger LOG = LoggerFactory.getLogger(CompositeKeyManager.class);

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
        warnDuplicateIds(ret);
        return ret;
    }

    private static void warnDuplicateIds(List<String> ids) {
        Set<String> seen = new HashSet<>();
        Set<String> duplicates = new LinkedHashSet<>();
        for (String id : ids) {
            if (!seen.add(id))
                duplicates.add(id);
        }
        if (!duplicates.isEmpty()) {
            LOG.warn("nop.security.duplicate-key-ids:key managers expose duplicate certificate ids;"
                    + " resolution is first-match-wins by manager order: duplicates={}", duplicates);
        }
    }
}
