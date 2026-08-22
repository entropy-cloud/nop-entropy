/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:   https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.store;

import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestDeltaResourceStore {

    /**
     * 形如 /_tenant/abc 的租户路径没有子路径，应报非法路径错误而不是数组越界
     */
    @Test
    public void testGetRawResourceTenantPathWithoutSubPath() {
        DeltaResourceStore store = new DeltaResourceStore();
        store.setStore(new InMemoryResourceStore());

        NopException e = assertThrows(NopException.class, () -> store.getRawResource("/_tenant/abc"));
        assertEquals("nop.err.core.resource.invalid-path", e.getErrorCode());
    }
}
