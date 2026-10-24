package io.nop.core.resource.deps;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.resource.IResourceReference;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.SourceLocation;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static io.nop.core.CoreConfigs.CFG_COMPONENT_RESOURCE_CACHE_CHANGE_GRACE_PERIOD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * check-changed判据载体单测：mtime相等比较+宽限期承诺双条件（plan 2500裁定①）。
 * 宽限G=0档必须等价于既有纯mtime相等比较语义（退化档钉）。
 */
public class TestDefaultResourceChangeChecker {

    static class ProgrammableReference implements IResourceReference {
        final String path;
        volatile long mtime;
        final AtomicInteger statCount = new AtomicInteger();

        ProgrammableReference(String path, long mtime) {
            this.path = path;
            this.mtime = mtime;
        }

        @Override
        public String getPath() {
            return path;
        }

        @Override
        public long lastModified() {
            statCount.incrementAndGet();
            return mtime;
        }

        @Override
        public long length() {
            return 0;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(new byte[0]);
        }
    }

    /**
     * 继承载体以获得唯一判据实现，仅覆写resolve注入可编程mtime（确定性测试缝，零sleep）
     */
    static class ProgrammableChecker extends DefaultResourceChangeChecker {
        final Map<String, ProgrammableReference> resources = new ConcurrentHashMap<>();

        ProgrammableReference register(String path, long mtime) {
            ProgrammableReference ref = new ProgrammableReference(path, mtime);
            resources.put(path, ref);
            return ref;
        }

        @Override
        public IResourceReference resolveResource(String resourcePath) {
            return resources.get(resourcePath);
        }
    }

    @Test
    public void testLegacyTwoArgEntryKeepsPureEqualitySemantics() {
        ProgrammableChecker checker = new ProgrammableChecker();
        ProgrammableReference ref = checker.register("/test/a.model", 1000L);

        ResourceChangeCheckResult result = checker.checkChanged("/test/a.model", 1000L);
        assertFalse(result.isChanged());
        assertEquals(1000L, result.getLastModified());

        result = checker.checkChanged("/test/a.model", 999L);
        assertTrue(result.isChanged());
        assertEquals(1000L, result.getLastModified());

        assertEquals(2, ref.statCount.get());
    }

    @Test
    public void testGraceWindowConservativeOnSameMtime() {
        ProgrammableChecker checker = new ProgrammableChecker();
        checker.register("/test/a.model", 1000L);

        long recordedAt = CoreMetrics.currentTimeMillis();
        // mtime相等但判据值刚记录，仍在宽限期内：保守判定CHANGED（窗口一同tick双写不可排除）
        ResourceChangeCheckResult result = checker.checkChanged("/test/a.model", 1000L, recordedAt);
        assertTrue(result.isChanged());
        // 裁决返回current=recorded，供调用方涂新同值（涂新同值不重锚）
        assertEquals(1000L, result.getLastModified());
    }

    @Test
    public void testGraceZeroDegeneratesToLegacyEquality() {
        AppConfig.getConfigProvider().updateConfigValue(CFG_COMPONENT_RESOURCE_CACHE_CHANGE_GRACE_PERIOD, Duration.ZERO);
        try {
            ProgrammableChecker checker = new ProgrammableChecker();
            checker.register("/test/a.model", 1000L);

            long recordedAt = CoreMetrics.currentTimeMillis();
            ResourceChangeCheckResult result = checker.checkChanged("/test/a.model", 1000L, recordedAt);
            assertFalse(result.isChanged());

            result = checker.checkChanged("/test/a.model", 999L, recordedAt);
            assertTrue(result.isChanged());
        } finally {
            AppConfig.getConfigProvider().updateConfigValue(CFG_COMPONENT_RESOURCE_CACHE_CHANGE_GRACE_PERIOD, null);
        }
    }

    @Test
    public void testGraceExpiredReturnsUnchanged() {
        ProgrammableChecker checker = new ProgrammableChecker();
        checker.register("/test/a.model", 1000L);

        long recordedAt = CoreMetrics.currentTimeMillis() - Duration.ofSeconds(5).toMillis();
        ResourceChangeCheckResult result = checker.checkChanged("/test/a.model", 1000L, recordedAt);
        assertFalse(result.isChanged());
    }

    @Test
    public void testGraceConfigDriven() {
        AppConfig.getConfigProvider().updateConfigValue(CFG_COMPONENT_RESOURCE_CACHE_CHANGE_GRACE_PERIOD,
                Duration.ofMillis(30));
        try {
            assertEquals(30L, DefaultResourceChangeChecker.graceMillis());

            AppConfig.getConfigProvider().updateConfigValue(CFG_COMPONENT_RESOURCE_CACHE_CHANGE_GRACE_PERIOD,
                    Duration.ofMillis(-1));
            // 负值按关闭档处理
            assertEquals(0L, DefaultResourceChangeChecker.graceMillis());
        } finally {
            AppConfig.getConfigProvider().updateConfigValue(CFG_COMPONENT_RESOURCE_CACHE_CHANGE_GRACE_PERIOD, null);
        }
    }

    @Test
    public void testNullResourceReturnsChanged() {
        ProgrammableChecker checker = new ProgrammableChecker();
        ResourceChangeCheckResult result = checker.checkChanged("/test/missing.model", 1000L);
        assertTrue(result.isChanged());
        assertEquals(-1L, result.getLastModified());
    }

    @Test
    public void testLoadMismatchPureComparisonEntry() {
        assertFalse(DefaultResourceChangeChecker.isLoadMismatch(1000L, 1000L));
        assertTrue(DefaultResourceChangeChecker.isLoadMismatch(1000L, 2000L));
    }

    @Test
    public void testChangeRecordVerdictImplementation() {
        long now = CoreMetrics.currentTimeMillis();
        // mtime不等：无条件CHANGED
        assertTrue(DefaultResourceChangeChecker.isChangeRecord(1000L, now, 2000L, 0L, now));
        // mtime相等+宽限关闭档：UNCHANGED
        assertFalse(DefaultResourceChangeChecker.isChangeRecord(1000L, now, 1000L, 0L, now));
        // mtime相等+宽限期内：保守CHANGED
        assertTrue(DefaultResourceChangeChecker.isChangeRecord(1000L, now, 1000L, 2000L, now + 1));
        // mtime相等+宽限已过：UNCHANGED
        assertFalse(DefaultResourceChangeChecker.isChangeRecord(1000L, now - 5000, 1000L, 2000L, now));
        // recordedAt=0（宽限0档锚）：不启用宽限
        assertFalse(DefaultResourceChangeChecker.isChangeRecord(1000L, 0L, 1000L, 2000L, now));
    }
}
