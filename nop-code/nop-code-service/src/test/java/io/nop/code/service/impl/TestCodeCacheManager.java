package io.nop.code.service.impl;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestCodeCacheManager {

    @Test
    void testLruEvictionOrder() {
        CodeCacheManager mgr = new CodeCacheManager();
        int max = CodeCacheManager.MAX_CACHE_ENTRIES;

        for (int i = 0; i < max + 5; i++) {
            mgr.getOrCreateEntry("idx-" + i);
        }

        assertEquals(max, mgr.cacheSize());

        assertNull(mgr.getValidEntry("idx-0"));
        assertNull(mgr.getValidEntry("idx-4"));
        assertNotNull(mgr.getValidEntry("idx-5"));
        assertNotNull(mgr.getValidEntry("idx-" + (max + 4)));
    }

    @Test
    void testLruAccessOrderReordersEntries() {
        CodeCacheManager mgr = new CodeCacheManager();
        int max = CodeCacheManager.MAX_CACHE_ENTRIES;

        mgr.getOrCreateEntry("first");
        for (int i = 1; i < max; i++) {
            mgr.getOrCreateEntry("idx-" + i);
        }
        assertEquals(max, mgr.cacheSize());

        mgr.getOrCreateEntry("first");

        mgr.getOrCreateEntry("evict-trigger");
        assertEquals(max, mgr.cacheSize());

        assertNull(mgr.getValidEntry("idx-1"));
        assertNotNull(mgr.getValidEntry("first"));
    }

    @Test
    void testTtlExpiry() throws Exception {
        CodeCacheManager mgr = new CodeCacheManager();
        CodeCacheManager.CacheEntry entry = mgr.getOrCreateEntry("ttl-test");
        assertNotNull(entry);

        Field lastAccessTimeField = CodeCacheManager.CacheEntry.class.getDeclaredField("lastAccessTime");
        lastAccessTimeField.setAccessible(true);
        lastAccessTimeField.setLong(entry, System.currentTimeMillis() - CodeCacheManager.CACHE_TTL_MS - 1000);

        assertNull(mgr.getValidEntry("ttl-test"));
    }

    @Test
    void testInvalidateRemovesEntry() {
        CodeCacheManager mgr = new CodeCacheManager();
        mgr.getOrCreateEntry("to-remove");
        assertNotNull(mgr.getValidEntry("to-remove"));

        mgr.invalidateAnalysisCache("to-remove", null);
        assertNull(mgr.getValidEntry("to-remove"));
    }
}
