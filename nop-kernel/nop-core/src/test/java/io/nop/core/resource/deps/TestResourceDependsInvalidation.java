package io.nop.core.resource.deps;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.resource.IResourceReference;
import io.nop.api.core.time.CoreMetrics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static io.nop.core.CoreConfigs.CFG_COMPONENT_RESOURCE_CACHE_CHANGE_GRACE_PERIOD;
import static io.nop.core.CoreErrors.ERR_COMPONENT_RESOURCE_LOAD_MTIME_CHANGED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 编译路径（collectDepends）窗口二消除单测：task执行前后双mtime + 载体纯比较入口裁决 +
 * 有界重执行编译task（plan 2500裁定①，编辑点6）。全断言以可编程mtime注入驱动，零sleep。
 */
public class TestResourceDependsInvalidation {
    private static final String PATH = "/test/compile/a.model";
    private static final String SUB_PATH = "/test/compile/dep.txt";

    static class ProgrammableChecker extends DefaultResourceChangeChecker {
        final Map<String, IResourceReference> resources = new ConcurrentHashMap<>();

        @Override
        public IResourceReference resolveResource(String resourcePath) {
            return resources.get(resourcePath);
        }
    }

    /**
     * 可编程mtime引用：task执行期间的mtime变更由测试显式注入（确定性）
     */
    static class FlipReference implements IResourceReference {
        final String path;
        final AtomicLong mtime;
        final AtomicInteger statCount = new AtomicInteger();

        FlipReference(String path, long mtime) {
            this.path = path;
            this.mtime = new AtomicLong(mtime);
        }

        void flip() {
            mtime.incrementAndGet();
        }

        @Override
        public String getPath() {
            return path;
        }

        @Override
        public long lastModified() {
            statCount.incrementAndGet();
            return mtime.get();
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

    private FlipReference ref;

    @BeforeEach
    public void setUp() {
        ref = new FlipReference(PATH, 1000L);
    }

    @AfterEach
    public void tearDown() {
        // 清理测试期间可能写入的全局配置，防止跨测试类泄漏
        AppConfig.getConfigProvider().updateConfigValue(CFG_COMPONENT_RESOURCE_CACHE_CHANGE_GRACE_PERIOD, null);
    }

    private ResourceDependsManager newManager() {
        return new ResourceDependsManager(new DefaultResourceChangeChecker());
    }

    @Test
    public void testStableMtimeSingleExecution() {
        ResourceDependsManager mgr = newManager();
        AtomicInteger taskCount = new AtomicInteger();

        String ret = mgr.collectDepends(ref, () -> "v" + taskCount.incrementAndGet());

        assertEquals("v1", ret);
        assertEquals(1, taskCount.get());
        // 判据值记录为已核验的mtime（m2==m1）
        assertEquals(1000L, mgr.getResourceDepends(PATH).getLastModified());
        // 记录即锚定
        assertTrue(mgr.getResourceDepends(PATH).getRecordedAt() >= CoreMetrics.currentTimeMillis() - 1000);
    }

    @Test
    public void testMtimeFlipDuringTaskRetriggerCompilation() {
        ResourceDependsManager mgr = newManager();
        AtomicInteger taskCount = new AtomicInteger();

        // 第一次task执行期间整文件替换（mtime翻转），第二次执行期间保持稳定
        Supplier<String> task = () -> {
            int n = taskCount.incrementAndGet();
            if (n == 1)
                ref.flip();
            return "v" + n;
        };

        String ret = mgr.collectDepends(ref, task);

        // 保守重执行编译task：失配后重做整个load动作，不把失配读数记入判据值
        assertEquals(2, taskCount.get());
        assertEquals("v2", ret);
        assertEquals(1001L, mgr.getResourceDepends(PATH).getLastModified());
        // 失配读数（1000L）不得被记录
        assertTrue(mgr.getResourceDepends(PATH).getRecordedAt() <= CoreMetrics.currentTimeMillis());
    }

    @Test
    public void testPersistentMtimeChangeFailsExplicitlyAfterBoundedRetry() {
        ResourceDependsManager mgr = newManager();
        AtomicInteger taskCount = new AtomicInteger();

        // 每次task执行期间mtime都翻转：有界重试耗尽后显式失败（禁止静默固化旧内容+新mtime）
        NopException e = assertThrows(NopException.class,
                () -> mgr.collectDepends(ref, () -> {
                    taskCount.incrementAndGet();
                    ref.flip();
                    return "v";
                }));

        assertEquals(ERR_COMPONENT_RESOURCE_LOAD_MTIME_CHANGED.getErrorCode(), e.getErrorCode());
        // 初次执行+MAX_LOAD_MTIME_RETRY次重执行
        assertEquals(ResourceDependsManager.MAX_LOAD_MTIME_RETRY + 1, taskCount.get());
    }

    @Test
    public void testIsDependencyChangedGraceConservativeThroughWalk() {
        ProgrammableChecker checker = new ProgrammableChecker();
        checker.resources.put(SUB_PATH, new FlipReference(SUB_PATH, 500L));
        ResourceDependsManager mgr = new ResourceDependsManager(checker);

        // task执行时trace一个子依赖：walk会经载体stat型入口检查该子依赖的判据值
        mgr.collectDepends(ref, () -> {
            mgr.traceDepends(SUB_PATH);
            return "v";
        });
        ResourceDependencySet root = mgr.getResourceDepends(PATH);
        assertEquals(1, root.getDepends().size());

        // mtime未变，但判据值刚记录、仍在宽限期内：walk保守判已变更（窗口一闭合经真实walk链路）
        AppConfig.getConfigProvider().updateConfigValue(CFG_COMPONENT_RESOURCE_CACHE_CHANGE_GRACE_PERIOD,
                Duration.ofSeconds(2));
        assertTrue(mgr.isDependencyChanged(PATH));

        // G=0关闭档：等价于既有纯相等比较语义；UNCHANGED分支到达涂新点
        AppConfig.getConfigProvider().updateConfigValue(CFG_COMPONENT_RESOURCE_CACHE_CHANGE_GRACE_PERIOD,
                Duration.ZERO);
        ResourceDependencySet subDeps = root.getDepends().iterator().next();
        long anchorBefore = subDeps.getRecordedAt();
        assertFalse(mgr.isDependencyChanged(PATH));
        // 涂新走簿记入口：重读同值不重锚（宽限窗口不因反复walk而永续）
        assertEquals(anchorBefore, subDeps.getRecordedAt());
    }

    @Test
    public void testFrozenCopyInDependencyMapCarriesRecordedAt() {
        ResourceDependsManager mgr = newManager();
        mgr.collectDepends(ref, () -> "v");

        ResourceDependencySet mapped = mgr.getResourceDepends(PATH);
        assertNotNull(mapped);
        assertEquals(1000L, mapped.getLastModified());
        // 判据值二元组完整入dependencyMap（后续merge版本推进时copy携带recordedAt）
        assertTrue(mapped.getRecordedAt() > 0);
    }
}
