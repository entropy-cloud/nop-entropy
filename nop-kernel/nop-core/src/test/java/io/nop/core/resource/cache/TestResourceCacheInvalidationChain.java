package io.nop.core.resource.cache;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.FileHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResourceObjectLoader;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.resource.deps.DefaultResourceChangeChecker;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static io.nop.core.CoreConfigs.CFG_COMPONENT_RESOURCE_CACHE_CHANGE_GRACE_PERIOD;
import static io.nop.core.CoreConfigs.CFG_COMPONENT_RESOURCE_REFRESH_MIN_INTERVAL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * check-changed失效链真实链路接线测试（plan 2500 rule 23 + C10-005冒烟）：
 * 真实VFS + 真实文件 + 生产默认接线（ResourceCacheEntry.loadObject → collectDependsTo →
 * ResourceDependsManager → DefaultResourceChangeChecker宽限判据），验证新判据在运行时真实生效。
 * 窗口一（同tick双写：内容替换但mtime相同）与窗口二（装载期间整文件替换）以显式setLastModified注入，零sleep。
 */
public class TestResourceCacheInvalidationChain extends BaseTestCase {
    static final File CHAIN_DIR = new File(new File(new File("target"), "test-classes"), "_vfs/test/chain");

    /**
     * fixture文件必须在CoreInitialization（VFS classpath _vfs 扫描快照）之前落盘：
     * 扫描只登记当时已存在的文件，测试方法内才创建的文件不会进入VFS索引，
     * 导致resolveResource回退UnknownResource(-1)——判据恒等、窗口二双mtime恒等，失效链静默失效。
     * 预置后测试方法内的写入经已登记的FileResource实时可见（mtime/内容均读真实文件）。
     */
    @BeforeAll
    public static void init() {
        CHAIN_DIR.mkdirs();
        for (String name : new String[]{"smoke-model.txt", "smoke-dep.txt",
                "win1-model.txt", "win1-dep.txt", "win2-model.txt", "win2-dep.txt", "ttl-object.txt"}) {
            writeFile(file(name), "");
        }
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @AfterEach
    public void restoreConfigs() {
        AppConfig.getConfigProvider().updateConfigValue(CFG_COMPONENT_RESOURCE_CACHE_CHANGE_GRACE_PERIOD, null);
        AppConfig.getConfigProvider().updateConfigValue(CFG_COMPONENT_RESOURCE_REFRESH_MIN_INTERVAL, null);
    }

    static File file(String name) {
        return new File(CHAIN_DIR, name);
    }

    static String vfsPath(File file) {
        return "/test/chain/" + file.getName();
    }

    static void writeFile(File file, String content) {
        FileHelper.writeText(file, content, null);
    }

    static void bumpMtime(File file, long offset) {
        assertTrue(file.setLastModified(file.lastModified() + offset), "setLastModified failed: " + file);
    }

    static void spinAtLeast(long durationMillis) {
        long start = CoreMetrics.currentTimeMillis();
        while (CoreMetrics.currentTimeMillis() - start < durationMillis) {
            // busy-wait（零sleep确定性）
        }
    }

    /**
     * 经编译路径collectDepends注册依赖并读取内容（真实模型依赖形态：重载后依赖判据值随编译推进）
     */
    static IResourceObjectLoader<String> compilingLoader(AtomicInteger count, File depFile) {
        return path -> {
            count.incrementAndGet();
            return ResourceComponentManager.instance().collectDepends(vfsPath(depFile),
                    () -> FileHelper.readText(depFile, null));
        };
    }

    /**
     * checkChanged默认开启冒烟（生产默认配置）：依赖文件变更（mtime不等）→ 真实walk判据检出 → 模型重载。
     * 接线验证：新判据经真实loadObject→collectDependsTo→checker链在运行时生效
     */
    @Test
    public void testRealChainInvalidatesOnDepFileChange() {
        File modelFile = file("smoke-model.txt");
        File depFile = file("smoke-dep.txt");
        writeFile(modelFile, "m1");
        writeFile(depFile, "d1");

        AtomicInteger count = new AtomicInteger();
        ResourceCacheEntry<String> entry = new ResourceCacheEntry<>(vfsPath(modelFile));
        IResourceObjectLoader<String> loader = compilingLoader(count, depFile);

        assertEquals("d1", entry.getObject(true, loader));
        assertEquals(1, count.get());

        // 依赖文件被修改（真实内容+mtime变更）
        spinAtLeast(130);
        writeFile(depFile, "d2");
        bumpMtime(depFile, 10000L);

        // 默认配置（check-changed=true、宽限2s）下walk检出变更并重载
        assertEquals("d2", entry.getObject(true, loader));
        assertEquals(2, count.get());

        // 重载后判据值已推进到新mtime，G=0档复核无重载（判定稳定，不固化旧内容也不抖动）
        AppConfig.getConfigProvider().updateConfigValue(CFG_COMPONENT_RESOURCE_CACHE_CHANGE_GRACE_PERIOD, Duration.ZERO);
        assertEquals("d2", entry.getObject(true, loader));
        assertEquals(2, count.get());
    }

    /**
     * 窗口二：装载期间根文件被整体替换（loader读旧内容，替换发生在m1与m2之间）→ 不固化「旧内容+新mtime」，保守重载
     */
    @Test
    public void testWindowTwoRootReplacedDuringLoadConservativeReload() {
        File modelFile = file("win2-model.txt");
        File depFile = file("win2-dep.txt");
        writeFile(modelFile, "v1");
        writeFile(depFile, "d1");
        // 稳定性复核走G=0档（mtime相等→UNCHANGED），使断言不依赖真实时间推进
        AppConfig.getConfigProvider().updateConfigValue(CFG_COMPONENT_RESOURCE_CACHE_CHANGE_GRACE_PERIOD, Duration.ZERO);

        AtomicInteger count = new AtomicInteger();
        ResourceCacheEntry<String> entry = new ResourceCacheEntry<>(vfsPath(modelFile));

        // 第一次装载读取v1，随后根文件被整体替换（内容v2+mtime前移）；第二次装载读到稳定v2
        IResourceObjectLoader<String> loader = p -> {
            int n = count.incrementAndGet();
            String content = FileHelper.readText(modelFile, null);
            if (n == 1) {
                writeFile(modelFile, "v2");
                bumpMtime(modelFile, 10000L);
            }
            ResourceComponentManager.instance().collectDepends(vfsPath(depFile),
                    () -> FileHelper.readText(depFile, null));
            return content;
        };

        // 保守重载：装载动作整体重做，服务内容为替换后的最新内容
        assertEquals("v2", entry.getObject(true, loader));
        assertEquals(2, count.get());

        // 重载后判据一致（不再抖动）
        assertEquals("v2", entry.getObject(true, loader));
        assertEquals(2, count.get());
    }

    /**
     * 窗口一：同tick双写（内容被替换但mtime相同，相等比较天然盲）→ 宽限期内保守判CHANGED触发重载
     */
    @Test
    public void testWindowOneSameTickDoubleWriteTriggersReload() {
        File modelFile = file("win1-model.txt");
        File depFile = file("win1-dep.txt");
        writeFile(modelFile, "m1");
        writeFile(depFile, "d1");

        AtomicInteger count = new AtomicInteger();
        ResourceCacheEntry<String> entry = new ResourceCacheEntry<>(vfsPath(modelFile));
        IResourceObjectLoader<String> loader = compilingLoader(count, depFile);

        assertEquals("d1", entry.getObject(true, loader));
        assertEquals(1, count.get());

        // 同tick双写模拟：内容替换后把mtime恢复为原值（第二次写入落在同一mtime粒度tick内）
        long originalMtime = depFile.lastModified();
        spinAtLeast(130);
        writeFile(depFile, "d2");
        assertTrue(depFile.setLastModified(originalMtime));

        // 宽限期内相等比较不可排除同tick双写：保守判CHANGED → 重载读到新内容
        assertEquals("d2", entry.getObject(true, loader));
        assertEquals(2, count.get());

        // G=0关闭档：同形态写入按既有纯相等比较语义不触发重载（等价性钉）
        AppConfig.getConfigProvider().updateConfigValue(CFG_COMPONENT_RESOURCE_CACHE_CHANGE_GRACE_PERIOD, Duration.ZERO);
        long mtime2 = depFile.lastModified();
        writeFile(depFile, "d3");
        assertTrue(depFile.setLastModified(mtime2));
        spinAtLeast(130);
        assertEquals("d2", entry.getObject(true, loader));
        assertEquals(2, count.get());
    }

    /**
     * 裁定③门路径自愈载体钉：expireAfterWrite整条目逐出冷加载（ResourceLoadingCache:81唯一消费点，
     * 显式timeout配置注入，负值=显式关闭档）
     */
    @Test
    public void testGatePathTtlExpireAfterWriteColdReload() {
        File cacheFile = file("ttl-object.txt");
        writeFile(cacheFile, "x1");

        AtomicInteger count = new AtomicInteger();
        IResourceObjectLoader<String> loader = p -> "x" + count.incrementAndGet();

        SourceLocation loc = SourceLocation.fromClass(TestResourceCacheInvalidationChain.class);
        ResourceLoadingCache<String> cache = new ResourceLoadingCache<>("test-chain-ttl", loader, null,
                AppConfig.varRef(loc, "test.chain.ttl.size", Integer.class, 100),
                AppConfig.varRef(loc, "test.chain.ttl.timeout", Duration.class, Duration.ofMillis(60)));

        assertEquals("x1", cache.get(vfsPath(cacheFile)));
        assertEquals(1, count.get());

        // TTL内：命中
        assertEquals("x1", cache.get(vfsPath(cacheFile)));
        assertEquals(1, count.get());

        // TTL过期：整条目逐出，下次访问冷加载重建
        spinAtLeast(100);
        assertEquals("x2", cache.get(vfsPath(cacheFile)));
        assertEquals(2, count.get());

        // 关闭档（负值=显式关闭，等价现行null语义）
        ResourceLoadingCache<String> noExpiry = new ResourceLoadingCache<>("test-chain-ttl-off", loader, null,
                AppConfig.varRef(loc, "test.chain.ttl.size2", Integer.class, 100),
                AppConfig.varRef(loc, "test.chain.ttl.timeout2", Duration.class, Duration.ofMillis(-1)));
        assertEquals("x3", noExpiry.get(vfsPath(cacheFile)));
        spinAtLeast(100);
        assertEquals("x3", noExpiry.get(vfsPath(cacheFile)));
        assertEquals(3, count.get());
    }
}
