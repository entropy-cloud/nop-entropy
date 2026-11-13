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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_FORCE_UNLOAD_NOT_ALLOWED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 状态机失败落定 + 恢复 API 回归（设计裁定②，C6-002；plan 2506 Phase 2 成员 C6-002）：
 * <ul>
 *     <li>deactivate 失败（bean destroy 抛错）落定 FAILED 非卡死（修复前停留 DEACTIVATING，
 *     unload/activate/reload 守卫全拒绝——红）；lastActivationError 记因；FAILED 态可 unload
 *     （残留字段幂等尽力清理）。</li>
 *     <li>activate rollback 清理失败落定 FAILED（修复前 rollback 抛错跳过 FAILED 落定——红）；
     *     失败路径残留引用保留（恢复入口处置的显式信号）。</li>
 *     <li>forceUnload 端到端链路（rule 22 链路①）：load → activate → 命令路由 → 失败注入 →
 *     FAILED → forceUnload → UNLOADED → 重 load/activate → 再失败 → FAILED → unload FAILED
 *     分支 → UNLOADED → 重载；经 manager 实例调用链真实可达（rule 23）。</li>
 *     <li>forceUnload 前置态契约：中间态（ACTIVATING 停车）受理、ACTIVATED 类型化拒绝
 *     （No Silent No-Op）、UNLOADED/不存在幂等。</li>
 * </ul>
 * AbstractPlugin 轨（autoInit destroy 独立尽力段 / public 恢复契约）见 nop-plugin-support
 * 模块同名测试类。
 */
public class TestPluginStateRecovery {

    private static final String FAILSTOP_ID = "/nop/plugin/test/fail-stop.plugin.xml";
    private static final String ROLLBACK_ID = "/nop/plugin/test/rollback-fail.plugin.xml";
    private static final String BLOCKING_ID = "/nop/plugin/test/blocking-gate.plugin.xml";
    private static final String FAILSTOP_KEY = "test.failstop.enabled";
    private static final String ROLLBACK_KEY = "test.rollbackfail.enabled";
    private static final String BLOCKING_KEY = "test.blocking.enabled";

    private PluginManagerImpl manager;

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
        BlockingActivator.reset();
        assign(FAILSTOP_KEY, "false");
        assign(ROLLBACK_KEY, "false");
        assign(BLOCKING_KEY, "false");
    }

    @AfterEach
    public void tearDown() {
        for (IPlugin plugin : manager.getLoadedPlugins()) {
            try {
                FutureHelper.syncGet(plugin.deactivate());
            } catch (RuntimeException ignore) {
                // 失败态/中间态容错
            }
            try {
                plugin.unload();
            } catch (RuntimeException ignore) {
                // 中间态守卫拒绝容错
            }
        }
        restore(FAILSTOP_KEY);
        restore(ROLLBACK_KEY);
        restore(BLOCKING_KEY);
    }

    private void assign(String key, String value) {
        AppConfig.getConfigProvider().assignConfigValue(key, value);
    }

    private void restore(String key) {
        AppConfig.getConfigProvider().assignConfigValue(key, null);
    }

    /**
     * 失败落定：deactivate 段 bean destroy 抛错 → FAILED（非卡死）+ lastActivationError 记因；
     * FAILED 态残留字段经 unload 幂等尽力清理后落 UNLOADED。
     */
    @Test
    @Timeout(60)
    public void testDeactivateFailureLandsFailedNotStuck() {
        VfsPluginDefinition def = (VfsPluginDefinition) manager.loadPlugin(FAILSTOP_ID);
        assertEquals(PluginState.LOADED, def.getState());
        assign(FAILSTOP_KEY, "true");
        assertTrue(def.activate(), "激活成功（失败点在 deactivate 段）");
        assertEquals(PluginState.ACTIVATED, def.getState());
        assign(FAILSTOP_KEY, "false");

        assertThrows(RuntimeException.class, () -> FutureHelper.syncGet(def.deactivate()),
                "destroy 抛错显式上抛（No Silent No-Op）");
        assertEquals(PluginState.FAILED, def.getState(),
                "deactivate 失败必须落定 FAILED（修复前停留 DEACTIVATING 卡死，unload/activate/reload 全守卫拒绝）");
        assertNotNull(def.getLastActivationError(), "失败记因可读");

        // FAILED 态残留字段幂等尽力清理后落 UNLOADED（半停容器不漏停）
        def.unload();
        assertEquals(PluginState.UNLOADED, def.getState());
        assertNull(def.getScope());
    }

    /**
     * 失败落定：activate 段 activator 抛错 + rollback 清理段（bean destroy）也抛错 →
     * FAILED 落定不被 rollback 抛错跳过（修复前停留 ACTIVATING 卡死——红）；失败路径残留
     * 引用保留（getScope 非 ACTIVATED 恒 null），随后 forceUnload 从 FAILED 恢复。
     */
    @Test
    @Timeout(60)
    public void testActivateRollbackFailureLandsFailedNotStuck() {
        VfsPluginDefinition def = (VfsPluginDefinition) manager.loadPlugin(ROLLBACK_ID);
        assertEquals(PluginState.LOADED, def.getState());
        assign(ROLLBACK_KEY, "true");

        assertThrows(NopException.class, def::activate, "激活失败显式上抛");
        assertEquals(PluginState.FAILED, def.getState(),
                "rollback 清理失败不得跳过 FAILED 落定（修复前停留 ACTIVATING 卡死）");
        assertNotNull(def.getLastActivationError(), "失败记因可读");
        assertNull(def.getScope(), "非 ACTIVATED 态 getScope 恒 null（残留引用不泄漏给消费方）");

        // forceUnload 从 FAILED 恢复（中间态唯一合法出口，manager 实例调用链可达）
        manager.forceUnload(ROLLBACK_ID);
        assertEquals(PluginState.UNLOADED, def.getState());
        assertNull(manager.getPlugin(ROLLBACK_ID), "holder 已移除");
        assertNotNull(def.getLastActivationError(), "恢复后 lastActivationError 保留供诊断");
    }

    /**
     * forceUnload 从在途中间态（ACTIVATING 停车）恢复：恢复调用受理前置态 ACTIVATING 后在定义层
     * lifecycleLock 上等待（Thread.State.BLOCKED 对齐，确定性），在途激活释放后**失败**
     * （activator 注入抛错 → rollback → FAILED），恢复随后原子清场落 UNLOADED、holder 移除。
     */
    @Test
    @Timeout(60)
    public void testForceUnloadRecoversFromInFlightActivatingThatFails() throws Exception {
        VfsPluginDefinition def = (VfsPluginDefinition) manager.loadPlugin(BLOCKING_ID);
        assertEquals(PluginState.LOADED, def.getState());
        assign(BLOCKING_KEY, "true");
        BlockingActivator.failOnRelease = true;

        Thread activateThread = new Thread(() -> assertThrows(NopException.class, def::activate,
                "注入失败后在途激活以类型化异常收尾"));
        activateThread.start();
        assertTrue(BlockingActivator.entered.await(20, TimeUnit.SECONDS), "ACTIVATING 在途（activator 停车）");
        assertEquals(PluginState.ACTIVATING, def.getState());

        AtomicReference<Throwable> recoveryError = new AtomicReference<>();
        Thread recoveryThread = new Thread(() -> {
            try {
                manager.forceUnload(BLOCKING_ID);
            } catch (Throwable t) {
                recoveryError.set(t);
            }
        });
        recoveryThread.start();
        // 对齐：恢复线程已受理 ACTIVATING 前置态并阻塞在定义层 lifecycleLock 上，再释放在途激活
        assertTrue(awaitBlocked(recoveryThread, 20000), "forceUnload 必须在 lifecycleLock 上等待在途激活落定");
        BlockingActivator.release.countDown();
        assertTrue(BlockingActivator.done.await(20, TimeUnit.SECONDS));
        activateThread.join(20000);
        recoveryThread.join(20000);

        assertNull(recoveryError.get(), "在途激活失败 → 恢复受理成功，不得抛错");
        assertEquals(PluginState.UNLOADED, def.getState(), "中间态经 forceUnload 原子落定 UNLOADED");
        assertNull(manager.getPlugin(BLOCKING_ID), "holder 已移除");
        assertNotNull(def.getLastActivationError(), "恢复后 lastActivationError 保留供诊断");
    }

    /**
     * forceUnload 锁内复核（不强杀健康活容器）：受理 ACTIVATING 前置态后等锁期间在途激活
     * **成功**落 ACTIVATED → 定义层复核无可恢复对象 → manager 类型化拒绝
     * （ERR_PLUGIN_FORCE_UNLOAD_NOT_ALLOWED，pluginState=ACTIVATED），holder 与状态保留。
     */
    @Test
    @Timeout(60)
    public void testForceUnloadDuringInFlightActivationThatSucceedsIsRejected() throws Exception {
        VfsPluginDefinition def = (VfsPluginDefinition) manager.loadPlugin(BLOCKING_ID);
        assign(BLOCKING_KEY, "true");

        Thread activateThread = new Thread(() -> assertTrue(def.activate()));
        activateThread.start();
        assertTrue(BlockingActivator.entered.await(20, TimeUnit.SECONDS));
        assertEquals(PluginState.ACTIVATING, def.getState());

        AtomicReference<Throwable> recoveryError = new AtomicReference<>();
        Thread recoveryThread = new Thread(() -> {
            try {
                manager.forceUnload(BLOCKING_ID);
            } catch (Throwable t) {
                recoveryError.set(t);
            }
        });
        recoveryThread.start();
        assertTrue(awaitBlocked(recoveryThread, 20000), "forceUnload 必须在 lifecycleLock 上等待在途激活落定");
        BlockingActivator.release.countDown();
        assertTrue(BlockingActivator.done.await(20, TimeUnit.SECONDS));
        activateThread.join(20000);
        recoveryThread.join(20000);

        assertNotNull(recoveryError.get(), "在途激活成功 → 锁内复核 ACTIVATED → 类型化拒绝（非静默 no-op）");
        NopException e = (NopException) recoveryError.get();
        assertEquals(ERR_PLUGIN_FORCE_UNLOAD_NOT_ALLOWED.getErrorCode(), e.getErrorCode());
        assertEquals(PluginState.ACTIVATED.name(), e.getParam("pluginState"));
        assertEquals(PluginState.ACTIVATED, def.getState(), "健康活容器不被强杀");
        assertNotNull(manager.getPlugin(BLOCKING_ID), "拒绝后 holder 保留");
    }

    /** 轮询直至线程阻塞在 monitor 上（Thread.State.BLOCKED），有界等待 */
    private static boolean awaitBlocked(Thread t, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (t.getState() == Thread.State.BLOCKED)
                return true;
            if (!t.isAlive())
                return false;
            Thread.sleep(5);
        }
        return false;
    }

    /**
     * 端到端恢复链路（rule 22 链路①全程，经 manager 实例调用链——rule 23 接线验证）：
     * load → activate → 命令路由 → 失败注入（destroy 抛错）→ FAILED 落定 → forceUnload →
     * UNLOADED → 重 load/activate → 命令路由 → 再失败注入 → FAILED → unload FAILED 分支 →
     * UNLOADED → 重载（完整状态机闭环）。
     */
    @Test
    @Timeout(120)
    public void testForceUnloadEndToEndRecoveryChain() {
        // 第一生命周期
        IPlugin plugin = manager.loadPlugin(FAILSTOP_ID);
        assertEquals(PluginState.LOADED, plugin.getState());
        assign(FAILSTOP_KEY, "true");
        assertTrue(manager.activatePlugin(FAILSTOP_ID));
        assertEquals(PluginState.ACTIVATED, plugin.getState());
        Map<String, Object> result = plugin.invokeCommand("hello", Map.of("who", "world"), null, null);
        assertEquals("hello:world", result.get("result"), "命令路由真实可达");

        // 失败注入 → FAILED 落定（非卡死）
        assign(FAILSTOP_KEY, "false");
        assertThrows(RuntimeException.class, () -> FutureHelper.syncGet(manager.deactivatePlugin(FAILSTOP_ID)));
        assertEquals(PluginState.FAILED, plugin.getState());

        // 恢复 API：FAILED → UNLOADED → 可重载
        manager.forceUnload(FAILSTOP_ID);
        assertEquals(PluginState.UNLOADED, plugin.getState());
        assertNull(manager.getPlugin(FAILSTOP_ID));

        // 第二生命周期：重 load/activate/命令路由
        IPlugin reloaded = manager.loadPlugin(FAILSTOP_ID);
        assertEquals(PluginState.LOADED, reloaded.getState(), "forceUnload 后可重新 loadPlugin");
        assign(FAILSTOP_KEY, "true");
        assertTrue(manager.activatePlugin(FAILSTOP_ID));
        result = reloaded.invokeCommand("hello", Map.of("who", "again"), null, null);
        assertEquals("hello:again", result.get("result"), "重激活后命令路由恢复");

        // 再失败注入 → FAILED → unloadPlugin（经 manager：定义层 unload FAILED 分支幂等尽力清理 +
        // holder 移除）→ UNLOADED → 重载（直接 def.unload() 会把 UNLOADED holder 留在注册表，
        // 重载走不到新定义，故经 manager 通道闭环）
        assign(FAILSTOP_KEY, "false");
        assertThrows(RuntimeException.class, () -> FutureHelper.syncGet(reloaded.deactivate()));
        assertEquals(PluginState.FAILED, reloaded.getState());
        manager.unloadPlugin(FAILSTOP_ID);
        assertEquals(PluginState.UNLOADED, reloaded.getState(), "FAILED 态残留字段幂等尽力清理后落 UNLOADED");
        assertNull(manager.getPlugin(FAILSTOP_ID), "unloadPlugin 成功路径 holder 已移除");
        IPlugin third = manager.loadPlugin(FAILSTOP_ID);
        assertEquals(PluginState.LOADED, third.getState(), "重载闭环");
    }

    /**
     * forceUnload 前置态契约：ACTIVATED 类型化拒绝（ERR_PLUGIN_FORCE_UNLOAD_NOT_ALLOWED，
     * param pluginId/pluginState——No Silent No-Op，非万能强杀），拒绝后 holder/状态保留。
     */
    @Test
    @Timeout(60)
    public void testForceUnloadRejectsActivatedWithTypedError() {
        IPlugin plugin = manager.loadPlugin(FAILSTOP_ID);
        assign(FAILSTOP_KEY, "true");
        assertTrue(manager.activatePlugin(FAILSTOP_ID));
        assertEquals(PluginState.ACTIVATED, plugin.getState());

        NopException e = assertThrows(NopException.class, () -> manager.forceUnload(FAILSTOP_ID),
                "ACTIVATED 必须类型化拒绝（引导走 deactivatePlugin 正常通道）");
        assertEquals(ERR_PLUGIN_FORCE_UNLOAD_NOT_ALLOWED.getErrorCode(), e.getErrorCode());
        assertEquals(PluginState.ACTIVATED.name(), e.getParam("pluginState"));
        assertNotNull(manager.getPlugin(FAILSTOP_ID), "拒绝后 holder 保留");
        assertEquals(PluginState.ACTIVATED, plugin.getState(), "拒绝后状态不变");
    }

    /**
     * forceUnload 幂等：插件不存在 / 已 UNLOADED 幂等返回（不抛错、不改变状态）。
     */
    @Test
    @Timeout(60)
    public void testForceUnloadIdempotentForAbsentAndUnloaded() {
        manager.forceUnload("/nop/plugin/test/not-exists.plugin.xml");

        IPlugin plugin = manager.loadPlugin(FAILSTOP_ID);
        plugin.unload();
        assertEquals(PluginState.UNLOADED, plugin.getState());
        manager.forceUnload(FAILSTOP_ID);
        assertEquals(PluginState.UNLOADED, plugin.getState(), "UNLOADED 幂等返回");
    }
}
