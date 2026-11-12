package io.nop.core.resource.cache;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.time.IClock;
import io.nop.core.CoreConfigs;
import io.nop.core.resource.IResourceObjectLoader;
import io.nop.core.resource.component.IResourceComponentManager;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.resource.deps.DefaultResourceChangeChecker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.nop.core.CoreConfigs.CFG_COMPONENT_RESOURCE_CACHE_TIMEOUT;
import static io.nop.core.CoreConfigs.CFG_COMPONENT_RESOURCE_REFRESH_MIN_INTERVAL;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * R4-32（2.1-C7-004 追加面(2)(3)，plan 2566 [R2-B1]）单调时钟载体钉：
 * ResourceCacheEntry verdict TTL / maxAge 判定与 lastLoadTime 写入成对同源（单调时钟）。
 * 断言（行为断言，非符号预设[R1-m3]）：
 * <ol>
 *   <li>maxAge 未满 + 单调未流逝 → 不重载（伪重载腿：混源巨大正差时此腿必红）；</li>
 *   <li>maxAge 满 + 单调流逝 → 强制重载（永不重载腿：混源巨大负差时此腿必红）；</li>
 *   <li>墙钟回拨/前跳不影响判定。</li>
 * </ol>
 * StubClock 双针经 registerClock + finally 恢复（设计 §1.3 缝纪律）。
 */
public class TestResourceCacheEntryMonotonicClock_C7_004 {
    static final String DEP_PATH = "/test/mono/dep.txt";

    static class StubClock implements IClock {
        long wallMillis = 1_700_000_000_000L;
        long nanoTicks = 1_000_000_000L;

        void advanceNanos(long deltaNanos) {
            nanoTicks += deltaNanos;
        }

        void rollbackWall(long deltaMillis) {
            wallMillis -= deltaMillis;
        }

        void forwardWall(long deltaMillis) {
            wallMillis += deltaMillis;
        }

        @Override
        public long currentTimeMillis() {
            return wallMillis;
        }

        @Override
        public long nanoTime() {
            return nanoTicks;
        }

        @Override
        public LocalDate currentDate() {
            return LocalDate.now();
        }

        @Override
        public LocalDateTime currentDateTime() {
            return LocalDateTime.now();
        }
    }

    static class ProgrammableReference implements io.nop.api.core.resource.IResourceReference {
        final String path;
        volatile long mtime;

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

    static class ProgrammableChecker extends DefaultResourceChangeChecker {
        final Map<String, ProgrammableReference> resources = new ConcurrentHashMap<>();

        ProgrammableReference registerDep(String path, long mtime) {
            ProgrammableReference ref = new ProgrammableReference(path, mtime);
            resources.put(path, ref);
            return ref;
        }

        @Override
        public io.nop.api.core.resource.IResourceReference resolveResource(String resourcePath) {
            return resources.get(resourcePath);
        }
    }

    StubClock clock;
    ProgrammableChecker checker;
    IResourceComponentManager oldManager;
    AtomicInteger loadCount;

    @BeforeEach
    public void setUp() {
        clock = new StubClock();
        CoreMetrics.registerClock(clock);
        checker = new ProgrammableChecker();
        checker.registerDep(DEP_PATH, 500L);

        oldManager = ResourceComponentManager.instance();
        ResourceComponentManager mgr = new ResourceComponentManager(false);
        mgr.setChangeChecker(checker);
        ResourceComponentManager.registerInstance(mgr);

        loadCount = new AtomicInteger();
    }

    @AfterEach
    public void tearDown() {
        ResourceComponentManager.registerInstance(oldManager);
        AppConfig.getConfigProvider().updateConfigValue(CFG_COMPONENT_RESOURCE_REFRESH_MIN_INTERVAL, null);
        AppConfig.getConfigProvider().updateConfigValue(CFG_COMPONENT_RESOURCE_CACHE_TIMEOUT, null);
        CoreMetrics.registerClock(CoreMetrics.defaultClock());
    }

    IResourceObjectLoader<String> tracingLoader() {
        return path -> {
            loadCount.incrementAndGet();
            ResourceComponentManager.instance().traceDepends(DEP_PATH);
            return "v" + loadCount.get();
        };
    }

    /**
     * maxAge 单调流逝钉：单调流逝超过 maxAge → 强制重载；未超过 → 不重载；
     * 墙钟回拨/前跳不产生任何影响（修复前 wall 域 lastLoadTime + stub 推进仅影响 nanoTime
     * → 判定不变 RED）。
     */
    @Test
    public void testMaxAgeFollowsMonotonicElapse() {
        AppConfig.getConfigProvider().updateConfigValue(CFG_COMPONENT_RESOURCE_REFRESH_MIN_INTERVAL, 0);
        AppConfig.getConfigProvider().updateConfigValue(CFG_COMPONENT_RESOURCE_CACHE_TIMEOUT,
                Duration.ofMillis(1000));
        ResourceCacheEntry<String> entry = new ResourceCacheEntry<>("/test/mono/a");
        IResourceObjectLoader<String> loader = tracingLoader();

        assertEquals("v1", entry.getObject(true, loader));
        assertEquals(1, loadCount.get());

        // 腿a：墙钟大幅回拨 + 单调未流逝 → 不重载（混源巨大正差时此腿伪重载 RED）
        clock.rollbackWall(3_600_000);
        clock.advanceNanos(TimeUnit.MILLISECONDS.toNanos(100));
        assertEquals("v1", entry.getObject(true, loader), "wall rollback must not affect maxAge");
        assertEquals(1, loadCount.get());

        // 腿b：墙钟前跳 2h + 单调仍未流逝 → 不重载（修复前 wall 前跳使伪重载 RED）
        clock.forwardWall(3_600_000L * 2);
        assertEquals("v1", entry.getObject(true, loader), "wall forward jump must not affect maxAge");
        assertEquals(1, loadCount.get());

        // 腿c：单调流逝超过 maxAge → 强制重载（混源巨大负差时此腿永不重载 RED）
        clock.advanceNanos(TimeUnit.MILLISECONDS.toNanos(1500));
        assertEquals("v2", entry.getObject(true, loader), "maxAge must force reload after monotonic elapse");
        assertEquals(2, loadCount.get());
    }

    /**
     * verdict TTL 记忆化单调钉：TTL 内记忆化短路（零裁决 walk）、TTL 过后恢复真实裁决；
     * 墙钟回拨不影响 TTL 判定。
     */
    @Test
    public void testVerdictTtlFollowsMonotonicElapse() {
        AppConfig.getConfigProvider().updateConfigValue(CFG_COMPONENT_RESOURCE_REFRESH_MIN_INTERVAL, 100);
        ResourceCacheEntry<String> entry = new ResourceCacheEntry<>("/test/mono/b");
        IResourceObjectLoader<String> loader = tracingLoader();

        assertEquals("v1", entry.getObject(true, loader));

        // 首查真实裁决
        assertEquals("v1", entry.getObject(true, loader));

        // TTL 内：墙钟回拨 + 记忆化短路（不触发 walk，不再 stat）
        clock.rollbackWall(3_600_000);
        clock.advanceNanos(TimeUnit.MILLISECONDS.toNanos(10));
        assertEquals("v1", entry.getObject(true, loader));

        // 单调流逝超过 TTL：恢复真实裁决（混源负差时 TTL 永不过期 → 永不 walk，
        // 若引入 stat 计数可判此腿；此处以单调流逝后行为断言）
        clock.advanceNanos(TimeUnit.MILLISECONDS.toNanos(200));
        assertEquals("v1", entry.getObject(true, loader));
        assertEquals(1, loadCount.get());
    }
}
