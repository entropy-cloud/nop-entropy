package io.nop.plugin.manager;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.plugin.api.IPlugin;
import io.nop.plugin.api.PluginState;
import io.nop.plugin.manager.impl.PluginManagerImpl;
import io.nop.plugin.manager.impl.VfsPluginDefinition;
import io.nop.plugin.test.BlockingActivator;
import io.nop.plugin.test.BlockingDisposeActivator;
import io.nop.plugin.test.ITool;
import io.nop.plugin.test.StateProbeActivator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.nop.plugin.api.PluginApiErrors.ERR_PLUGIN_INACTIVE;
import static io.nop.plugin.api.PluginApiErrors.ERR_PLUGIN_NOT_DEACTIVATED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 锁迁移统一回归（设计裁定①，C1-010 主案 + C1-011/C1-012 并案 + C2-004 镜像窗；plan 2506
 * Phase 2 成员 C1-010）——只断言修复后可观测行为，禁修饰符存在断言：
 * <ul>
 *     <li>unloadPlugin × 在途 activate 互斥（门闩对齐）：unloadPlugin 不得在激活在途时返回
 *     （reconcileLock 串行化）；激活完成后到达的 unload 被 ACTIVATED 守卫类型化拒绝（守卫语义
     *     保持，孤儿活容器交错不可达）。</li>
 *     <li>unloadPlugin × reconcile 压力交错：终态单一（注册表无条目 ⇔ 容器未激活/已停），
 *     不存在「unload 正常返回 + 激活已完成 + holder 已移除」的孤儿组合。</li>
 *     <li>deactivate 翻转窗消费方异常统一 INACTIVE（无混型）。</li>
 *     <li>start 路径回调时序固化（回调延迟契约受害点②）：回调在 activate 完成后派发；
 *     activate 抛错仍派发（finally 语义）。</li>
 * </ul>
 * AbstractPlugin 轨（C1-011 双轨对齐）冒烟见 nop-plugin-support 模块同名测试类。
 */
public class TestPluginLifecycleLockMigration {

    private static final String BLOCKING_ID = "/nop/plugin/test/blocking-gate.plugin.xml";
    private static final String PROBE_ID = "/nop/plugin/test/state-probe.plugin.xml";
    private static final String DISPOSE_ID = "/nop/plugin/test/dispose-gate.plugin.xml";
    private static final String FAIL_ID = "/nop/plugin/test/activate-fail.plugin.xml";
    private static final String BLOCKING_KEY = "test.blocking.enabled";
    private static final String PROBE_KEY = "state.probe.enabled";
    private static final String DISPOSE_KEY = "test.dispose.enabled";

    private PluginManagerImpl manager;
    private final Map<String, Object> priorConfig = new ConcurrentHashMap<>();

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    public void setUp() {
        manager = new PluginManagerImpl();
        StateProbeActivator.reset();
        BlockingActivator.reset();
        BlockingDisposeActivator.reset();
        closeGate(BLOCKING_KEY);
        closeGate(PROBE_KEY);
        closeGate(DISPOSE_KEY);
    }

    @AfterEach
    public void tearDown() {
        for (IPlugin plugin : manager.getLoadedPlugins()) {
            try {
                FutureHelper.syncGet(plugin.deactivate());
            } catch (RuntimeException ignore) {
                // 测试已自行去激活/失败态：忽略（清理容错）
            }
            try {
                plugin.unload();
            } catch (RuntimeException ignore) {
                // 中间态守卫拒绝：忽略（清理容错）
            }
        }
        restoreGate(BLOCKING_KEY);
        restoreGate(PROBE_KEY);
        restoreGate(DISPOSE_KEY);
    }

    private void closeGate(String key) {
        assign(key, "false");
    }

    private void openGate(String key) {
        assign(key, "true");
    }

    private void assign(String key, String value) {
        priorConfig.computeIfAbsent(key, k -> AppConfig.getConfigProvider().getConfigValue(k, null));
        AppConfig.getConfigProvider().assignConfigValue(key, value);
    }

    private void restoreGate(String key) {
        Object prior = priorConfig.get(key);
        AppConfig.getConfigProvider().assignConfigValue(key, prior);
    }

    /**
     * 互斥（门闩对齐）：T1 activatePlugin 在 activator 内停车（ACTIVATING 在途）期间，
     * T2 unloadPlugin 不得返回（修复前：unload 守卫锁外读 ACTIVATING 立即抛 NOT_DEACTIVATED
     * 返回——与在途激活交叠，红）；释放后激活完成（ACTIVATED），T2 才进入并被守卫类型化拒绝。
     */
    @Test
    @Timeout(60)
    public void testUnloadPluginDoesNotOverlapInFlightActivation() throws Exception {
        IPlugin plugin = manager.loadPlugin(BLOCKING_ID);
        assertEquals(PluginState.LOADED, plugin.getState(), "门控关闭：loadPlugin 只到 LOADED");
        openGate(BLOCKING_KEY);

        CountDownLatch unloadDone = new CountDownLatch(1);
        AtomicReference<Throwable> unloadError = new AtomicReference<>();
        Thread activateThread = new Thread(() -> manager.activatePlugin(BLOCKING_ID));
        activateThread.start();
        assertTrue(BlockingActivator.entered.await(20, TimeUnit.SECONDS),
                "激活在途（ACTIVATING，activator 停车）");

        Thread unloadThread = new Thread(() -> {
            try {
                manager.unloadPlugin(BLOCKING_ID);
            } catch (Throwable e) {
                unloadError.set(e);
            } finally {
                unloadDone.countDown();
            }
        });
        unloadThread.start();

        // 修复后可观测行为：unloadPlugin 持 reconcileLock 等待在途激活完成——不得在激活在途时返回
        boolean overlapped = unloadDone.await(800, TimeUnit.MILLISECONDS);
        assertFalse(overlapped,
                "unloadPlugin 不得与在途 activate 交叠返回（reconcileLock 串行化被破坏，孤儿活容器交错窗重现）");

        // 释放激活 → 激活完成后 unloadPlugin 才进入 → ACTIVATED 守卫类型化拒绝（可观测、可重试）
        BlockingActivator.release.countDown();
        assertTrue(BlockingActivator.done.await(20, TimeUnit.SECONDS), "激活完成");
        assertTrue(unloadDone.await(20, TimeUnit.SECONDS), "激活完成后 unloadPlugin 应返回");
        Throwable err = unloadError.get();
        assertNotNull(err, "激活完成后到达的 unload 被 ACTIVATED 守卫拒绝");
        assertInstanceOf(NopException.class, err);
        assertEquals(ERR_PLUGIN_NOT_DEACTIVATED.getErrorCode(), ((NopException) err).getErrorCode());
        assertEquals(PluginState.ACTIVATED.name(), ((NopException) err).getParam("pluginState"));
        assertEquals(PluginState.ACTIVATED, plugin.getState(), "终态单一：激活完成不被unload写覆盖");
        activateThread.join(20000);
        unloadThread.join(20000);
    }

    /**
     * 压力交错（unloadPlugin × reconcile 自动激活）：每轮终态单一断言——不存在「unload 正常
     * 返回 + 激活已发生 + holder 已移除」的孤儿组合（修复前正向交错产出孤儿活容器）；且失败
     * 只能是两类类型化守卫拒绝之一。
     */
    @Test
    @Timeout(180)
    public void testUnloadPluginConcurrentWithReconcileNoOrphan() throws Exception {
        int rounds = 150;
        AtomicInteger orphans = new AtomicInteger();
        for (int i = 0; i < rounds; i++) {
            StateProbeActivator.reset();
            IPlugin plugin = manager.loadPlugin(PROBE_ID);
            assertEquals(PluginState.LOADED, plugin.getState());
            openGate(PROBE_KEY);

            AtomicReference<Throwable> unloadError = new AtomicReference<>();
            AtomicReference<Throwable> reconcileError = new AtomicReference<>();
            Thread unloadThread = new Thread(() -> {
                try {
                    manager.unloadPlugin(PROBE_ID);
                } catch (Throwable t) {
                    unloadError.set(t);
                }
            });
            Thread reconcileThread = new Thread(() -> {
                try {
                    manager.reconcilePlugins();
                } catch (Throwable t) {
                    reconcileError.set(t);
                }
            });
            unloadThread.start();
            reconcileThread.start();
            unloadThread.join(30000);
            reconcileThread.join(30000);

            boolean activated = StateProbeActivator.stateAtActivate != null;
            boolean holderGone = manager.getPlugin(PROBE_ID) == null;
            if (unloadError.get() == null && activated && holderGone) {
                orphans.incrementAndGet();
            }
            if (unloadError.get() != null) {
                assertInstanceOf(NopException.class, unloadError.get());
                assertEquals(ERR_PLUGIN_NOT_DEACTIVATED.getErrorCode(),
                        ((NopException) unloadError.get()).getErrorCode(),
                        "unload 失败只能是 ACTIVATED 守卫类型化拒绝（无混型）");
            }
            if (reconcileError.get() != null) {
                throw (RuntimeException) reconcileError.get();
            }
            if (activated) {
                assertEquals(PluginState.ACTIVATED, plugin.getState(),
                        "激活发生后终态必须为 ACTIVATED（不被 unload 锁外写覆盖）");
                assertNotNull(manager.getPlugin(PROBE_ID),
                        "激活已发生时 holder 不得被交叠移除（孤儿活容器：注册表无条目 ⇔ 容器活跃）");
            }

            // 轮间清理：恢复 LOADED/UNLOADED 基线。门控无条件关回——unload 胜出（holder 已移除）
            // 的轮次若不关门，下一轮 loadPlugin 的尾部 reconcile 会直接自动激活，破坏 LOADED 起点。
            // 卸载走 manager 通道（deactivate + unloadPlugin 移除 holder）——直接 def.unload() 会把
            // UNLOADED holder 留在注册表，下一轮 loadPlugin 返回旧 holder 而非新 LOADED 定义
            closeGate(PROBE_KEY);
            IPlugin cur = manager.getPlugin(PROBE_ID);
            if (cur != null) {
                try {
                    FutureHelper.syncGet(cur.deactivate());
                } catch (RuntimeException ignore) {
                    // 失败态容错
                }
                manager.unloadPlugin(PROBE_ID);
            }
        }
        assertEquals(0, orphans.get(), "零孤儿：unload 正常返回 + 激活已发生 + holder 已移除的组合不可达");
    }

    /**
     * deactivate 翻转窗（DEACTIVATING，effect dispose 停车）消费方统一 INACTIVE：代理调用、
     * getService、invokeCommand 一律快速失败 INACTIVE（无 ISE/NPE 混型泄漏）；翻转结束回到
     * LOADED（定义保留）。
     */
    @Test
    @Timeout(60)
    public void testDeactivateWindowConsumersGetInactiveUniformly() throws Exception {
        VfsPluginDefinition def = (VfsPluginDefinition) manager.loadPlugin(DISPOSE_ID);
        assertEquals(PluginState.LOADED, def.getState());
        openGate(DISPOSE_KEY);
        assertTrue(manager.activatePlugin(DISPOSE_ID));
        assertEquals(PluginState.ACTIVATED, def.getState());
        ITool tool = def.getService(ITool.class);
        assertEquals("bash", tool.getName());

        Thread deactivateThread = new Thread(() -> FutureHelper.syncGet(def.deactivate()));
        deactivateThread.start();
        assertTrue(BlockingDisposeActivator.disposeEntered.await(20, TimeUnit.SECONDS),
                "进入 DEACTIVATING 翻转窗（effect dispose 停车）");

        NopException viaProxy = assertThrows(NopException.class, tool::getName);
        assertEquals(ERR_PLUGIN_INACTIVE.getErrorCode(), viaProxy.getErrorCode(), "代理调用统一 INACTIVE");
        NopException viaGetService = assertThrows(NopException.class, () -> def.getService(ITool.class));
        assertEquals(ERR_PLUGIN_INACTIVE.getErrorCode(), viaGetService.getErrorCode(), "getService 统一 INACTIVE");
        NopException viaCommand = assertThrows(NopException.class,
                () -> def.invokeCommand("hello", Map.of("who", "x"), null, null));
        assertEquals(ERR_PLUGIN_INACTIVE.getErrorCode(), viaCommand.getErrorCode(), "命令路由统一 INACTIVE");
        assertNull(def.getScope(), "翻转窗 getScope 返回 null（双检形态保持）");

        BlockingDisposeActivator.disposeRelease.countDown();
        deactivateThread.join(20000);
        assertEquals(PluginState.LOADED, def.getState(), "翻转结束回到 LOADED（定义保留）");
    }

    /**
     * start 路径回调时序固化（回调延迟契约受害点②）：内嵌 updateConfig 步进的回调必须在
     * activate 完成后派发（修复前回调先于 activate 执行，回调观察到 LOADED——红）。
     */
    @Test
    @Timeout(60)
    public void testStartDispatchesConfigCallbackAfterActivation() {
        VfsPluginDefinition def = (VfsPluginDefinition) manager.loadPlugin(PROBE_ID);
        assertEquals(PluginState.LOADED, def.getState());
        List<PluginState> observed = new ArrayList<>();
        def.setOnConfigChanged(() -> observed.add(def.getState()));
        openGate(PROBE_KEY);

        def.start("g", "a", "1.0", Map.of("probe.start.key", "v"));

        assertEquals(List.of(PluginState.ACTIVATED), observed,
                "start 内嵌 updateConfig 的回调必须在 activate 完成后派发（时序变化声明固化）");
        assertEquals(PluginState.ACTIVATED, def.getState());
        assertEquals("v", def.getDefinitionConfig().get("probe.start.key"), "start config 进入定义级配置域");
    }

    /**
     * start 路径失败派发保证（finally 语义）：activate 抛错时变更标志非空仍派发——失败路径
     * 不丢 reconcile 通知（缺陷形态下回调先于 activate 必然执行，修复不得让失败路径丢失通知）。
     */
    @Test
    @Timeout(60)
    public void testStartDispatchesCallbackEvenWhenActivationFails() {
        VfsPluginDefinition def = (VfsPluginDefinition) manager.loadPlugin(FAIL_ID);
        assertEquals(PluginState.FAILED, def.getState(), "activate-fail 定义 loadPlugin 即 FAILED");
        List<PluginState> observed = new ArrayList<>();
        def.setOnConfigChanged(() -> observed.add(def.getState()));

        assertThrows(NopException.class, () -> def.start("g", "a", "1.0", Map.of("k", "v")),
                "activate 失败显式上抛");

        assertEquals(1, observed.size(), "activate 抛错亦派发（失败路径不丢 reconcile 通知）");
        assertEquals(PluginState.FAILED, def.getState());
    }

    private static void assertInstanceOf(Class<?> type, Object obj) {
        assertTrue(type.isInstance(obj), "期望 " + type.getName() + " 实际 " + obj);
    }
}
