/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.mfa;

import io.nop.auth.core.mfa.store.LocalMfaChallengeStore;
import io.nop.auth.core.mfa.store.LocalSmsCodeStore;
import io.nop.auth.service.mfa.store.RedisMfaChallengeStore;
import io.nop.auth.service.mfa.store.RedisSmsCodeStore;
import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * W4 Phase 3: verifies the store assembly point ({@link MfaStoreProvider}) selection logic
 * and the fail-closed behavior when Redis is requested but no backend is available.
 */
public class TestMfaStoreProvider {

    @Test
    public void testDefaultIsLocal() {
        MfaStoreProvider provider = new MfaStoreProvider();
        assertInstanceOf(LocalMfaChallengeStore.class, provider.getMfaChallengeStore());
        assertInstanceOf(LocalSmsCodeStore.class, provider.getSmsCodeStore());
    }

    @Test
    public void testExplicitLocal() {
        MfaStoreProvider provider = new MfaStoreProvider();
        provider.setStoreType(MfaStoreProvider.STORE_TYPE_LOCAL);
        assertInstanceOf(LocalMfaChallengeStore.class, provider.getMfaChallengeStore());
    }

    @Test
    public void testRedisWithoutBackendFailsClosed() {
        MfaStoreProvider provider = new MfaStoreProvider();
        provider.setStoreType(MfaStoreProvider.STORE_TYPE_REDIS);
        // nosqlService not set -> fail-closed (no silent fallback to Local)
        assertThrows(NopException.class, provider::getMfaChallengeStore,
                "redis requested without backend must throw, not silently fall back to Local");
        assertThrows(NopException.class, provider::getSmsCodeStore);
    }
}
