/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.credential.kms.vault;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.autotest.NopTestProperty;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.credential.api.crypto.ICredentialKeyProvider;
import io.nop.credential.crypto.CredentialCipher;
import io.nop.credential.crypto.DefaultCredentialKeyProvider;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W10 Phase 1 门控反向证明：KMS 模块在 classpath 上但 {@code key-provider}
 * 未配置（缺省 local）时，门控 bean 不注册、缺省实现照常生效——
 * <b>未选用 KMS 的部署对模块存在零感知（local 行为与一期完全一致）</b>。
 */
@NopTestConfig(localDb = true)
@NopTestProperty(name = "nop.credential.master-keys", value = "keyA:test-pass-a")
public class TestLocalKeyProviderWithKmsModulePresent extends JunitBaseTestCase {

    @Inject
    ICredentialKeyProvider keyProvider;

    @Inject
    CredentialCipher credentialCipher;

    @Test
    public void defaultProviderWinsWhenKeyProviderUnset() {
        assertTrue(keyProvider instanceof DefaultCredentialKeyProvider,
                "key-provider 未配置时必须是 DefaultCredentialKeyProvider, got: " + keyProvider.getClass());
        assertFalse(keyProvider instanceof VaultCredentialKeyProvider,
                "门控 bean（if-property key-provider=vault）不得注册");
        assertEquals("keyA", keyProvider.getActiveKeyId());
        assertTrue(keyProvider.getKeyIds().contains("keyA"));
    }

    @Test
    public void localCipherBehaviorUnchanged() {
        String plain = "{\"apiKey\":\"sk-local-path\"}";
        String ct = credentialCipher.encrypt(plain);
        assertTrue(ct.startsWith(CredentialCipher.CV1_MARKER + "keyA:"),
                "local 路径加密前缀不变, got: " + ct);
        assertEquals(plain, credentialCipher.decrypt(ct));
    }
}
