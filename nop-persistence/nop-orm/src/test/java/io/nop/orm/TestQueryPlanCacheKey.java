/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class TestQueryPlanCacheKey {

    /**
     * enableFilter影响编译上下文，必须参与缓存键。
     * 否则同一name+sqlText以不同enableFilter编译时会错误复用先编译的计划
     */
    @Test
    public void testEnableFilterParticipatesInKey() {
        QueryPlanCacheKey key1 = new QueryPlanCacheKey("a", "select o from A", false, false, false);
        QueryPlanCacheKey key2 = new QueryPlanCacheKey("a", "select o from A", false, false, true);

        assertNotEquals(key1, key2);
        assertNotEquals(key1.hashCode(), key2.hashCode());

        QueryPlanCacheKey key3 = new QueryPlanCacheKey("a", "select o from A", false, false, true);
        assertEquals(key2, key3);
        assertEquals(key2.hashCode(), key3.hashCode());

        // 其他维度仍然参与比较
        assertNotEquals(key1, new QueryPlanCacheKey("a", "select o from B", false, false, false));
        assertNotEquals(key1, new QueryPlanCacheKey("a", "select o from A", true, false, false));
        assertNotEquals(key1, new QueryPlanCacheKey("a", "select o from A", false, true, false));
    }
}
