/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.cache;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2548 R4-14（C5-003）CacheConfig 拷贝构造载体钉。
 */
public class TestCacheConfigCopy {

    /** pin5003：拷贝构造必须保留 destroyOnRemove / refreshAfterWrite；executor（transient）不拷贝。 */
    @Test
    public void pin5003_copyKeepsLifecycleFields() {
        CacheConfig source = CacheConfig.newConfig(10);
        source.setDestroyOnRemove(true);
        source.setRefreshAfterWrite(Duration.ofSeconds(30));

        CacheConfig copy = source.cloneInstance();
        assertTrue(copy.isDestroyOnRemove(),
                "缺陷态：克隆路径 destroyOnRemove 恒 false → 驱逐回调结构性失效");
        assertEquals(Duration.ofSeconds(30), copy.getRefreshAfterWrite());
        assertNull(copy.getExecutor(), "executor 为 transient 池引用，显式不拷贝");

        assertFalse(CacheConfig.newConfig(10).cloneInstance().isDestroyOnRemove(),
                "默认值不翻转");
    }
}
