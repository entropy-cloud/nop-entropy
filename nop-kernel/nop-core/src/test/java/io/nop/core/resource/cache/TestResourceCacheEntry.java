/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:   https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.cache;

import io.nop.commons.lang.ICreationListener;
import io.nop.core.resource.IResourceObjectLoader;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestResourceCacheEntry {

    static class RecordingListener implements ICreationListener<String> {
        final List<String> created = new ArrayList<>();

        @Override
        public void onCreated(String object) {
            created.add(object);
        }

        @Override
        public void onDestroyed(String object) {
        }
    }

    /**
     * reloadObject 刷新得到 null 时应与 getObject 一样记录 Null 占位并跳过 onCreated 回调，
     * 否则后续 getObject 会把缓存视为未加载而反复装载
     */
    @Test
    public void testReloadToNullUsesNullPlaceholder() {
        RecordingListener listener = new RecordingListener();
        ResourceCacheEntry<String> entry = new ResourceCacheEntry<>("/test/a", listener);

        AtomicInteger loadCount = new AtomicInteger();
        IResourceObjectLoader<String> loader = path -> {
            int n = loadCount.incrementAndGet();
            return n == 1 ? "v1" : null;
        };

        assertEquals("v1", entry.getObject(false, loader));
        assertEquals(1, loadCount.get());

        // 强制刷新，装载结果为 null
        assertTrue(entry.checkRefresh(true, loader));
        assertEquals(2, loadCount.get());

        // 刷新为 null 后缓存应记录 Null 占位，再次获取不应重复装载
        assertNull(entry.getObject(false, loader));
        assertEquals(2, loadCount.get());

        // onCreated 不应收到 null
        assertFalse(listener.created.contains(null));
        assertTrue(listener.created.contains("v1"));
    }
}
