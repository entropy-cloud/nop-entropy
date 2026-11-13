package io.nop.core.resource.cache;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.resource.IResourceReference;
import io.nop.api.core.time.CoreMetrics;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static io.nop.core.CoreConfigs.CFG_COMPONENT_RESOURCE_CACHE_CHANGE_GRACE_PERIOD;
import static io.nop.core.CoreConfigs.CFG_COMPONENT_RESOURCE_CACHE_TIMEOUT;
import static io.nop.core.CoreConfigs.CFG_COMPONENT_RESOURCE_REFRESH_MIN_INTERVAL;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ResourceCacheEntry直连路径内建门单测（plan 2500裁定③④）：verdict记忆化节流、TRUE侧同源键重载限速、
 * maxAge强制重载自愈、deps-空豁免（GraphQLEngine反例零误修）。全断言以可编程mtime注入+忙等驱动，零sleep。
 */
public class TestResourceCacheEntryGate {
    static final String DEP_PATH = "/test/gate/dep.txt";

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
     * 继承载体获得唯一判据实现，仅覆写resolve注入可编程资源（确定性测试缝）
     */
    static class ProgrammableChecker extends DefaultResourceChangeChecker {
        final Map<String, ProgrammableReference> resources = new ConcurrentHashMap<>();

        ProgrammableReference registerDep(String path, long mtime) {
            ProgrammableReference ref = new ProgrammableReference(path, mtime);
            resources.put(path, ref);
            return ref;
        }

        @Override
        public IResourceReference resolveResource(String resourcePath) {
            return resources.get(resourcePath);
        }
    }

    ProgrammableChecker checker;
    ProgrammableReference depRef;
    IResourceComponentManager oldManager;
    AtomicInteger loadCount;
    AtomicInteger statCount;

    @BeforeEach
    public void setUp() {
        checker = new ProgrammableChecker();
        depRef = checker.registerDep(DEP_PATH, 500L);
        statCount = depRef.statCount;

        oldManager = ResourceComponentManager.instance();
        ResourceComponentManager mgr = new ResourceComponentManager(false);
        mgr.setChangeChecker(checker);
        ResourceComponentManager.registerInstance(mgr);

        loadCount = new AtomicInteger();
    }

    @AfterEach
    public void tearDown() {
        ResourceComponentManager.registerInstance(oldManager);
        AppConfig.getConfigProvider().updateConfigValue(CFG_COMPONENT_RESOURCE_CACHE_CHANGE_GRACE_PERIOD, null);
        AppConfig.getConfigProvider().updateConfigValue(CFG_COMPONENT_RESOURCE_REFRESH_MIN_INTERVAL, null);
        AppConfig.getConfigProvider().updateConfigValue(CFG_COMPONENT_RESOURCE_CACHE_TIMEOUT, null);
    }

    static void setGrace(Duration d) {
        AppConfig.getConfigProvider().updateConfigValue(CFG_COMPONENT_RESOURCE_CACHE_CHANGE_GRACE_PERIOD, d);
    }

    static void setRefreshMinInterval(Integer millis) {
        AppConfig.getConfigProvider().updateConfigValue(CFG_COMPONENT_RESOURCE_REFRESH_MIN_INTERVAL, millis);
    }

    static void setCacheTimeout(Duration d) {
        AppConfig.getConfigProvider().updateConfigValue(CFG_COMPONENT_RESOURCE_CACHE_TIMEOUT, d);
    }

    /**
     * 忙等替代sleep：确定性测试缝（裁定⑥(c)零sleep）
     */
    static void spinAtLeast(long durationMillis) {
        long start = CoreMetrics.currentTimeMillis();
        while (CoreMetrics.currentTimeMillis() - start < durationMillis) {
            // busy-wait
        }
    }

    IResourceObjectLoader<String> tracingLoader() {
        return path -> {
            loadCount.incrementAndGet();
            ResourceComponentManager.instance().traceDepends(DEP_PATH);
            return "v" + loadCount.get();
        };
    }

    IResourceObjectLoader<String> plainLoader() {
        return path -> "v" + loadCount.incrementAndGet();
    }

    @Test
    public void testMemoizationThrottlesDepWalkStatTax() {
        setGrace(Duration.ZERO);
        setRefreshMinInterval(100);
        ResourceCacheEntry<String> entry = new ResourceCacheEntry<>("/test/gate/a");
        IResourceObjectLoader<String> loader = tracingLoader();

        // 首次装载：trace登记依赖（dep set构造读取mtime计1次stat），不触发walk
        assertEquals("v1", entry.getObject(true, loader));
        assertEquals(1, loadCount.get());
        assertEquals(1, statCount.get());

        // 首查走真实裁决：再1次dep stat
        assertEquals("v1", entry.getObject(true, loader));
        assertEquals(2, statCount.get());

        // 记忆化FALSE短路：20次直连调用零walk零stat（C9-001 stat税面收敛钉）
        for (int i = 0; i < 20; i++) {
            assertEquals("v1", entry.getObject(true, loader));
        }
        assertEquals(2, statCount.get());
        assertEquals(1, loadCount.get());

        // 记忆化TTL过期后恢复真实裁决
        spinAtLeast(130);
        assertEquals("v1", entry.getObject(true, loader));
        assertEquals(3, statCount.get());
    }

    /**
     * 门缺失必红对照：修复前直连路径无节流（宽限窗口内逐调用真实裁决+重载）；
     * 修复后TRUE侧同源键限速使宽限窗口内重载频度有界（风暴闭合钉）。
     * 宽限以写入年龄为锚（Phase 3修订）：依赖注入「最近被写过」的mtime使宽限裁决可达
     */
    @Test
    public void testTrueSideRateLimitBoundsGraceReloadStorm() {
        setGrace(Duration.ofSeconds(2));
        setRefreshMinInterval(100);
        depRef.mtime = CoreMetrics.currentTimeMillis();
        ResourceCacheEntry<String> entry = new ResourceCacheEntry<>("/test/gate/b");
        IResourceObjectLoader<String> loader = tracingLoader();

        assertEquals("v1", entry.getObject(true, loader));

        // 宽限期内首查真实裁决得CHANGED，但距lastLoadTime<100ms：TRUE侧限速吸收，不重载
        assertEquals("v1", entry.getObject(true, loader));
        assertEquals(1, loadCount.get());

        // 记忆化TRUE缓存期内：同样不重载
        assertEquals("v1", entry.getObject(true, loader));
        assertEquals(1, loadCount.get());

        // 超过同源间隔后：执行一次保守重载（宽限冗余重载，触发集=G窗口内），重载重读内容
        spinAtLeast(130);
        assertEquals("v2", entry.getObject(true, loader));
        assertEquals(2, loadCount.get());

        // 重载后记忆化重置、真实裁决再次CHANGED（mtime未变仍在宽限期），但再次被TRUE侧限速吸收——重载节奏=同源键间隔
        assertEquals("v2", entry.getObject(true, loader));
        assertEquals(2, loadCount.get());
    }

    @Test
    public void testMaxAgeForcedReloadBypassesVerdict() {
        setGrace(Duration.ZERO);
        setRefreshMinInterval(0);
        setCacheTimeout(Duration.ofMillis(1));
        ResourceCacheEntry<String> entry = new ResourceCacheEntry<>("/test/gate/c");
        IResourceObjectLoader<String> loader = tracingLoader();

        assertEquals("v1", entry.getObject(true, loader));
        assertEquals(1, loadCount.get());

        // 判据UNCHANGED（mtime未变+G=0），但超过maxAge：强制重载（裁定③直连路径自愈载体，不经判据裁决）
        spinAtLeast(5);
        assertEquals("v2", entry.getObject(true, loader));
        assertEquals(2, loadCount.get());

        // checkChanged=false不触发maxAge（尊重显式不检查语义）
        assertEquals("v2", entry.getObject(false, loader));
        assertEquals(2, loadCount.get());
    }

    @Test
    public void testMaxAgeDisabledByZeroOrNegative() {
        setGrace(Duration.ZERO);
        setRefreshMinInterval(0);
        ResourceCacheEntry<String> entry = new ResourceCacheEntry<>("/test/gate/d");
        IResourceObjectLoader<String> loader = tracingLoader();

        assertEquals("v1", entry.getObject(true, loader));

        setCacheTimeout(Duration.ZERO);
        spinAtLeast(5);
        assertEquals("v1", entry.getObject(true, loader));
        assertEquals(1, loadCount.get());

        setCacheTimeout(Duration.ofMillis(-1));
        assertEquals("v1", entry.getObject(true, loader));
        assertEquals(1, loadCount.get());
    }

    /**
     * GraphQLEngine documentCache反例零误修钉：deps恒空条目门全部旁路——零stat、不记忆化、maxAge不生效
     */
    @Test
    public void testDepsEmptyEntryExemptFromGate() {
        setGrace(Duration.ZERO);
        setRefreshMinInterval(100);
        setCacheTimeout(Duration.ofMillis(1));
        ResourceCacheEntry<String> entry = new ResourceCacheEntry<>("/test/gate/e");
        IResourceObjectLoader<String> loader = plainLoader();

        assertEquals("v1", entry.getObject(true, loader));
        assertEquals(1, loadCount.get());
        assertEquals(0, statCount.get());

        spinAtLeast(5);
        for (int i = 0; i < 20; i++) {
            assertEquals("v1", entry.getObject(true, loader));
        }
        // 零重载（maxAge豁免）+零stat（walk豁免）
        assertEquals(1, loadCount.get());
        assertEquals(0, statCount.get());
    }

    /**
     * 未加载态（deps==null，如反序列化恢复的entry）：保持既有isChanged()==true语义，必须重载
     */
    @Test
    public void testUnloadedDeserializedStateStillReloads() {
        setGrace(Duration.ZERO);
        setRefreshMinInterval(100);
        ResourceCacheEntry<String> source = new ResourceCacheEntry<>("/test/gate/f");
        assertEquals("v1", source.getObject(true, plainLoader()));

        ResourceCacheEntry.CacheEntryState<String> state = source.getCacheEntryState();
        ResourceCacheEntry<String> restored = new ResourceCacheEntry<>(state, null);

        AtomicInteger count = new AtomicInteger();
        assertEquals("v1", restored.getObject(true, p -> "v" + count.incrementAndGet()));
        assertEquals(1, count.get());
    }
}
