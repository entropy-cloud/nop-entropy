package io.nop.core.resource.tenant;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.resource.IResourceReference;
import io.nop.api.core.time.CoreMetrics;
import io.nop.core.resource.IResourceObjectLoader;
import io.nop.core.resource.component.IResourceComponentManager;
import io.nop.core.resource.cache.ResourceCacheEntryWithLoader;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.resource.deps.DefaultResourceChangeChecker;
import io.nop.core.resource.deps.VirtualResourceDependencySet;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 同链复制面委托穿透单测（plan 2500裁定⑤）：TenantAware三包装层+ResourceCacheEntryWithLoader的自动受益
 * 不得只凭类型存在断言，必须以非继承委托穿透行为断言验证。可编程mtime注入+忙等，零sleep，G双档（0/默认）。
 */
public class TestTenantAwareCacheDelegation {
    static final String DEP_PATH = "/test/tenant-gate/dep.txt";
    static final String TENANT = "t-test";

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

    ProgrammableChecker checker;
    IResourceComponentManager oldManager;
    AtomicInteger loadCount;

    @BeforeEach
    public void setUp() {
        checker = new ProgrammableChecker();
        checker.register(DEP_PATH, 500L);

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

    static void spinAtLeast(long durationMillis) {
        long start = CoreMetrics.currentTimeMillis();
        while (CoreMetrics.currentTimeMillis() - start < durationMillis) {
            // busy-wait（零sleep确定性）
        }
    }

    IResourceObjectLoader<String> tracingLoader() {
        return path -> {
            loadCount.incrementAndGet();
            ResourceComponentManager.instance().traceDepends(DEP_PATH);
            return "v" + loadCount.get();
        };
    }

    /**
     * 装饰器穿透：getObject经getCacheEntry()到达内层per-tenant ResourceCacheEntry的直连门
     * （记忆化+TRUE侧限速+maxAge）
     */
    @Test
    public void testTenantAwareResourceCacheEntryPenetratesToEntryGate() {
        setGrace(Duration.ZERO);
        setRefreshMinInterval(100);
        TenantAwareResourceCacheEntry<String> wrapper = new TenantAwareResourceCacheEntry<>("test-entry-gate", null);

        ContextProvider.runWithTenant(TENANT, () -> {
            IResourceObjectLoader<String> loader = tracingLoader();

            assertEquals("v1", wrapper.getObject(true, loader));
            assertEquals(1, loadCount.get());
            assertEquals(1, checker.resources.get(DEP_PATH).statCount.get());

            // 首查真实裁决再1次stat，随后记忆化短路：节流穿透装饰器到达内层entry门
            assertEquals("v1", wrapper.getObject(true, loader));
            assertEquals(2, checker.resources.get(DEP_PATH).statCount.get());
            for (int i = 0; i < 20; i++) {
                assertEquals("v1", wrapper.getObject(true, loader));
            }
            assertEquals(2, checker.resources.get(DEP_PATH).statCount.get());
            assertEquals(1, loadCount.get());

            // maxAge强制重载穿透装饰器（deps非空条目）
            AppConfig.getConfigProvider().updateConfigValue(CFG_COMPONENT_RESOURCE_CACHE_TIMEOUT, Duration.ofMillis(1));
            setRefreshMinInterval(0);
            spinAtLeast(5);
            assertEquals("v2", wrapper.getObject(true, loader));
            assertEquals(2, loadCount.get());
            return null;
        });
    }

    /**
     * 组合+委托穿透：get(path) → getCache(path).get → 门路径 + entry门；
     * G>0宽限保守重载经包装层可达，G=0退化为现行语义（双档钉）
     */
    @Test
    public void testTenantAwareResourceLoadingCacheGraceReloadReachable() {
        TenantAwareResourceLoadingCache<String> wrapper = new TenantAwareResourceLoadingCache<>(
                "test-loading-cache", null, null);

        // G>0档：宽限期内保守CHANGED经包装层触发保守重载
        setGrace(Duration.ofSeconds(2));
        setRefreshMinInterval(100);
        ContextProvider.runWithTenant(TENANT, () -> {
            IResourceObjectLoader<String> loader = tracingLoader();

            assertEquals("v1", wrapper.get("/test/tenant-gate/a", loader));
            assertEquals(1, loadCount.get());

            // 100ms门内：isRefreshEnabled先置门短路
            assertEquals("v1", wrapper.get("/test/tenant-gate/a", loader));
            assertEquals(1, loadCount.get());

            // 超过门间隔：真实walk得宽限保守CHANGED → 保守重载经包装层可达（重载重读内容）
            spinAtLeast(130);
            assertEquals("v2", wrapper.get("/test/tenant-gate/a", loader));
            assertEquals(2, loadCount.get());

            // 重载后立即再查：TRUE侧限速吸收，无逐请求重载放大
            assertEquals("v2", wrapper.get("/test/tenant-gate/a", loader));
            assertEquals(2, loadCount.get());
            return null;
        });

        // G=0关闭档：退化为现行语义（等值mtime永不触发重载，缓存值维持）
        setGrace(Duration.ZERO);
        ContextProvider.runWithTenant(TENANT, () -> {
            IResourceObjectLoader<String> loader = tracingLoader();

            assertEquals("v2", wrapper.get("/test/tenant-gate/a", loader));
            assertEquals(2, loadCount.get());
            for (int i = 0; i < 10; i++) {
                spinAtLeast(5);
                assertEquals("v2", wrapper.get("/test/tenant-gate/a", loader));
            }
            assertEquals(2, loadCount.get());
            return null;
        });
    }

    /**
     * 组合+委托穿透：getImpl() → per-tenant ResourceDependsManager，宽限裁决到达判据消费层；
     * G=0等价性钉 + G>0保守判定钉（同tick双写注入 → 判已变更）
     */
    @Test
    public void testTenantAwareDependencyManagerGraceVerdictPenetration() {
        String rootPath = "/test/tenant-gate/root.model";
        ProgrammableChecker localChecker = new ProgrammableChecker();
        localChecker.register(rootPath, 800L);
        localChecker.register(DEP_PATH, 500L);
        TenantAwareDependencyManager wrapper = new TenantAwareDependencyManager(localChecker);

        ContextProvider.runWithTenant(TENANT, () -> {
            // collectDepends(String)语义要求外层collect帧（嵌套编译形态），此处以collectDependsTo提供
            wrapper.collectDependsTo(new VirtualResourceDependencySet("/test/tenant-gate/outer"), () -> {
                wrapper.collectDepends(rootPath, () -> {
                    wrapper.traceDepends(DEP_PATH);
                    return "v";
                });
                return null;
            });

            // G=0关闭档：等价于既有纯相等比较语义
            setGrace(Duration.ZERO);
            assertFalse(wrapper.isDependencyChanged(rootPath));

            // G>0：mtime未变但判据值刚记录——同tick双写注入形态保守判已变更
            setGrace(Duration.ofSeconds(2));
            assertTrue(wrapper.isDependencyChanged(rootPath));
            return null;
        });
    }

    /**
     * 继承+1-arg纯委托穿透：cachedCompiledTag使用形态（XplLibTagCompiler）的1-arg调用到达2-arg entry门
     */
    @Test
    public void testResourceCacheEntryWithLoaderOneArgReachesGate() {
        setGrace(Duration.ZERO);
        setRefreshMinInterval(100);
        ResourceCacheEntryWithLoader<String> entry = new ResourceCacheEntryWithLoader<>("/test/tenant-gate/with-loader",
                tracingLoader());

        assertEquals("v1", entry.getObject(true));
        assertEquals(1, loadCount.get());
        assertEquals(1, checker.resources.get(DEP_PATH).statCount.get());

        assertEquals("v1", entry.getObject(true));
        assertEquals(2, checker.resources.get(DEP_PATH).statCount.get());
        for (int i = 0; i < 20; i++) {
            assertEquals("v1", entry.getObject(true));
        }
        // 1-arg调用到达2-arg门：记忆化节流生效
        assertEquals(2, checker.resources.get(DEP_PATH).statCount.get());
        assertEquals(1, loadCount.get());

        // maxAge自愈经1-arg委托可达
        AppConfig.getConfigProvider().updateConfigValue(CFG_COMPONENT_RESOURCE_CACHE_TIMEOUT, Duration.ofMillis(1));
        setRefreshMinInterval(0);
        spinAtLeast(5);
        assertEquals("v2", entry.getObject(true));
        assertEquals(2, loadCount.get());
    }
}
