/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.resource;

import io.nop.commons.mutable.MutableInt;
import io.nop.core.resource.cache.IResourceCacheEntryLoader;
import io.nop.core.resource.cache.ResourceLoadingCache;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestResourceLoadingCache {
    @Test
    public void testCache() {
        MutableInt count = new MutableInt();
        ResourceLoadingCache cache = new ResourceLoadingCache("a", new IResourceCacheEntryLoader() {
            @Override
            public boolean isResourceChanged(String path, long lastLoadTime, Object object) {
                return false;
            }

            @Override
            public Object loadObjectFromPath(String path) {
                count.addAndGet(1);
                return "s" + count;
            }
        }, null);
        String value = (String) cache.get("p");
        assertEquals(value, cache.get("p"));
        assertEquals(1, count.get());
    }
}
