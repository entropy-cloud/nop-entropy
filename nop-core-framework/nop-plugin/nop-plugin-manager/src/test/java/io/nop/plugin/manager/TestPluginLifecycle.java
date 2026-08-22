package io.nop.plugin.manager;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.plugin.api.Disposable;
import io.nop.plugin.api.IPlugin;
import io.nop.plugin.api.IPluginContext;
import io.nop.plugin.api.IPluginScope;
import io.nop.plugin.api.PluginState;
import io.nop.plugin.manager.impl.PluginManagerImpl;
import io.nop.plugin.manager.impl.VfsPluginDefinition;
import io.nop.plugin.test.AgentInstanceRecorder;
import io.nop.plugin.test.BashTool;
import io.nop.plugin.test.FailActivator;
import io.nop.plugin.test.IConfigReader;
import io.nop.plugin.test.ITool;
import io.nop.plugin.test.StateProbeActivator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static io.nop.plugin.api.PluginApiErrors.ERR_PLUGIN_INACTIVE;
import static io.nop.plugin.api.PluginApiErrors.ERR_PLUGIN_NOT_DEACTIVATED;
import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_ACTIVATION_FAILED;
import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_MULTIPLE_SERVICE_CANDIDATES;
import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_SERVICE_PROXY_ONLY_INTERFACE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R1 单激活六态状态机生命周期测试（plan-2026-08-22-2309-1 Phase 2）：
 * <ul>
 *     <li>端到端单激活流：loadPlugin → activatePlugin → getService 调真实 bean → deactivatePlugin
 *     → getService 抛 INACTIVE → unloadPlugin 完整走通。</li>
 *     <li>六态转换链可观测：LOADED→ACTIVATING→ACTIVATED→DEACTIVATING→LOADED（探针 activator）。</li>
 *     <li>接线验证：activator.activate(scope, config) 真实被调用（计数器/参数/服务解析断言）；
 *     返回的 Disposable 在 deactivate 时回退（quiescence：scope.effects() 清空）。</li>
 *     <li>幂等与守卫：ACTIVATED 重复 activate 幂等 true 不重跑（含并发单飞）；ACTIVATED 时
 *     unload 抛明确异常；激活失败置 FAILED 且错误可读；失败超阈值暂停自动激活。</li>
 *     <li>getService 激活态绑定代理：deactivate 后调用抛 INACTIVE，重激活后同一引用恢复可用；
 *     多候选/仅接口规则保持。</li>
 *     <li>定义级配置域：updateConfig LOADED 缓存 / ACTIVATED 热应用。</li>
 *     <li>start/stop 收敛：start = load + activate、stop = deactivate + unload。</li>
 * </ul>
 *
 * <p>语义注记：reconcile 是生命周期操作的成功路径触发点（load/unload/activate/deactivate，
 * 01 §五）——门控满足时 deactivate 会被随后的 reconcile 重新激活（固定点收敛）。因此
 * "去激活后保持 LOADED"的断言均在门控关闭后执行 deactivate。
 */
public class TestPluginLifecycle {

    private static final String PLUGIN_ID = "/nop/plugin/test/agent-instance.plugin.xml";
    private static final String MODEL_PROVIDER_ID = "/nop/plugin/test/model-provider.plugin.xml";
    private static final String MULTI_CANDIDATE_ID = "/nop/plugin/test/multi-candidate.plugin.xml";
    private static final String FAIL_ID = "/nop/plugin/test/activate-fail.plugin.xml";
    private static final String STATE_PROBE_ID = "/nop/plugin/test/state-probe.plugin.xml";
    private static final String GLOBAL_TOOLS_ENABLED = "agent.tools.enabled";
    private static final String GLOBAL_STATE_PROBE_ENABLED = "state.probe.enabled";

    private PluginManagerImpl manager;
    private Object priorToolsEnabled;
    private Object priorProbeEnabled;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
        // 全局配置写入（测试基线，非 plugin 框架行为）：定义级配置域"全局回落"测试前提
        AppConfig.getConfigProvider().assignConfigValue("agent.timeout", "global-timeout");
        AppConfig.getConfigProvider().assignConfigValue("test.plugin.global.var", "global-default");
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    public void setUp() {
        manager = new PluginManagerImpl();
        AgentInstanceRecorder.reset();
        FailActivator.attemptCount = 0;
        StateProbeActivator.reset();
        // 门控基线（钉死）：agent-instance 的 coeffect 有两条腿——requires=model-provider
        // （开门控 = model-provider 定义加载且 ACTIVATED）+ 定义级 if-property 读全局
        // agent.tools.enabled（设 true）。赋值前捕获原值，@AfterEach 恢复（防跨测试类污染）。
        priorToolsEnabled = AppConfig.getConfigProvider().getConfigValue(GLOBAL_TOOLS_ENABLED, null);
        AppConfig.getConfigProvider().assignConfigValue(GLOBAL_TOOLS_ENABLED, "true");
        priorProbeEnabled = AppConfig.getConfigProvider().getConfigValue(GLOBAL_STATE_PROBE_ENABLED, null);
        AppConfig.getConfigProvider().assignConfigValue(GLOBAL_STATE_PROBE_ENABLED, "false");
    }

    @AfterEach
    public void tearDown() {
        // 统一清理：逐个 deactivate（幂等，容错）→ unload → 恢复全局配置键原值
        for (IPlugin plugin : manager.getLoadedPlugins()) {
            try {
                FutureHelper.syncGet(plugin.deactivate());
            } catch (RuntimeException ignore) {
                // 测试已自行去激活：忽略（@AfterEach 清理容错）
            }
            try {
                plugin.unload();
            } catch (RuntimeException ignore) {
                // ignore for cleanup
            }
        }
        AppConfig.getConfigProvider().assignConfigValue(GLOBAL_TOOLS_ENABLED, priorToolsEnabled);
        AppConfig.getConfigProvider().assignConfigValue(GLOBAL_STATE_PROBE_ENABLED, priorProbeEnabled);
    }

    private void closeToolsGate() {
        AppConfig.getConfigProvider().assignConfigValue(GLOBAL_TOOLS_ENABLED, "false");
    }

    private void openToolsGate() {
        AppConfig.getConfigProvider().assignConfigValue(GLOBAL_TOOLS_ENABLED, "true");
    }

    /**
     * 端到端验证：loadPlugin（门控关闭 → LOADED）→ 开门控 → activatePlugin（真实激活）→
     * getService 调真实 bean 方法（经代理）→ 关门控 → deactivatePlugin → getService 抛
     * INACTIVE（新调用与既有代理引用均快速失败）→ unloadPlugin 成功（单激活流主干）。
     */
    @Test
    public void testEndToEndSingleActivationFlow() {
        // 门控关闭装载（reconcile 不激活）——保证 activatePlugin 走真实激活路径
        closeToolsGate();
        manager.loadPlugin(MODEL_PROVIDER_ID);
        IPlugin plugin = manager.loadPlugin(PLUGIN_ID);
        assertEquals(PluginState.LOADED, plugin.getState(), "门控关闭：loadPlugin 只到 LOADED");
        assertEquals(0, AgentInstanceRecorder.activatedCount);

        // 门控打开 → 显式 activatePlugin（门控满足 → 建子容器 + activator + effect）
        openToolsGate();
        assertTrue(manager.activatePlugin(PLUGIN_ID));
        assertEquals(PluginState.ACTIVATED, plugin.getState());
        assertEquals(1, AgentInstanceRecorder.activatedCount, "activator 真实被调用");

        // getService 代理调真实 bean 方法
        ITool tool = plugin.getService(ITool.class);
        assertTrue(Proxy.isProxyClass(tool.getClass()), "getService 必须返回激活态绑定代理");
        assertEquals("bash", tool.getName(), "primary 优先，真实 bean 方法调用");

        // 关门控 → deactivatePlugin（reconcile 固定点：门控关闭保持 LOADED）
        closeToolsGate();
        FutureHelper.syncGet(manager.deactivatePlugin(PLUGIN_ID));
        assertEquals(PluginState.LOADED, plugin.getState(), "deactivate 回到 LOADED（定义保留）");
        NopException direct = assertThrows(NopException.class, () -> plugin.getService(ITool.class));
        assertEquals(ERR_PLUGIN_INACTIVE.getErrorCode(), direct.getErrorCode());
        NopException viaProxy = assertThrows(NopException.class, tool::getName);
        assertEquals(ERR_PLUGIN_INACTIVE.getErrorCode(), viaProxy.getErrorCode());

        // unloadPlugin 成功
        manager.unloadPlugin(PLUGIN_ID);
        assertEquals(PluginState.UNLOADED, plugin.getState());
        assertNull(manager.getPlugin(PLUGIN_ID));
    }

    /**
     * 六态转换链可观测（探针 activator）：LOADED→ACTIVATING（activator 执行期）→ACTIVATED→
     * DEACTIVATING（effect 回退期）→LOADED 全链断言。
     */
    @Test
    public void testSixStateTransitionsObservable() {
        // 门控关闭装载（保持 LOADED，reconcile 不激活）→ 注入探针 → 开门控 → 显式激活
        VfsPluginDefinition def = (VfsPluginDefinition) manager.loadPlugin(STATE_PROBE_ID);
        assertEquals(PluginState.LOADED, def.getState(), "state.probe.enabled=false → 门控关闭保持 LOADED");
        StateProbeActivator.probedPlugin = def;

        AppConfig.getConfigProvider().assignConfigValue(GLOBAL_STATE_PROBE_ENABLED, "true");
        assertTrue(manager.activatePlugin(STATE_PROBE_ID));
        assertEquals(PluginState.ACTIVATING, StateProbeActivator.stateAtActivate,
                "activator 执行期状态必须为 ACTIVATING（中间态可观测）");
        assertEquals(PluginState.ACTIVATED, def.getState());

        // 关门控 → deactivate（effect 回退期探针捕获 DEACTIVATING；门控关闭保持 LOADED）
        AppConfig.getConfigProvider().assignConfigValue(GLOBAL_STATE_PROBE_ENABLED, "false");
        FutureHelper.syncGet(manager.deactivatePlugin(STATE_PROBE_ID));
        assertEquals(PluginState.DEACTIVATING, StateProbeActivator.stateAtDispose,
                "effect 回退期（scope.close 内）状态必须为 DEACTIVATING（中间态可观测）");
        assertEquals(PluginState.LOADED, def.getState());
    }

    /**
     * 接线验证 + quiescence：activator 声明的插件激活时 activate(scope, config) 确实被调用
     * （计数器、config 参数、scope 服务解析断言）；返回的 Disposable 在 deactivate 时确实被回退
     * （scope.effects() 清空 = quiescence）；LIFO 回退顺序 + bean destroy 在 effect 回退之后。
     */
    @Test
    public void testActivatorWiringAndEffectQuiescence() {
        manager.loadPlugin(MODEL_PROVIDER_ID);
        manager.loadPlugin(PLUGIN_ID);
        assertEquals(PluginState.ACTIVATED, manager.getPlugin(PLUGIN_ID).getState(),
                "reconcile 自动激活（门控满足）");

        // activator 调用链：scope + config 参数传入；scope.getService 取 primary bean 成功
        assertEquals(1, AgentInstanceRecorder.activatedCount);
        assertNotNull(AgentInstanceRecorder.lastConfig);
        assertInstanceOf(BashTool.class, AgentInstanceRecorder.serviceTool, "primary 优先");
        assertEquals(2, AgentInstanceRecorder.serviceCount);

        // 返回值 disposer 自动注册 + scope.effect 注册 → 2 个 effect
        IPluginScope scope = ((VfsPluginDefinition) manager.getPlugin(PLUGIN_ID)).getScope();
        assertNotNull(scope);
        assertEquals(2, scope.effects().size());

        // 关门控 → deactivate → quiescence：effects 清空，LIFO 顺序 + bean destroy 在 effect 回退之后
        closeToolsGate();
        FutureHelper.syncGet(manager.deactivatePlugin(PLUGIN_ID));
        assertNull(((VfsPluginDefinition) manager.getPlugin(PLUGIN_ID)).getScope());
        assertEquals(0, scope.effects().size(), "quiescence：effects 清空");
        assertEquals(List.of("return-disposed", "effect-disposed", "bean-destroyed"),
                AgentInstanceRecorder.events(), "LIFO 回退（return 后注册先回退）+ bean destroy 最后");
    }

    /**
     * 幂等与并发单飞：ACTIVATED 重复 activate 返回 true 不重跑 activator；
     * 并发重复 activate 单飞收敛（计数仍为 1）。
     */
    @Test
    public void testIdempotentActivateDoesNotRerun() throws Exception {
        closeToolsGate();
        manager.loadPlugin(MODEL_PROVIDER_ID);
        IPlugin plugin = manager.loadPlugin(PLUGIN_ID);
        assertEquals(PluginState.LOADED, plugin.getState());

        openToolsGate();
        assertTrue(manager.activatePlugin(PLUGIN_ID));
        assertEquals(1, AgentInstanceRecorder.activatedCount);
        assertTrue(manager.activatePlugin(PLUGIN_ID), "ACTIVATED 重复 activate 幂等 true");
        assertEquals(1, AgentInstanceRecorder.activatedCount, "幂等不重跑 activator");

        // 并发重复 activate 单飞：定义级 deactivate（不经 manager reconcile 触发点——门控仍开，
        // 经 manager 入口会被 reconcile 立即重激活），再并发显式激活
        FutureHelper.syncGet(plugin.deactivate());
        assertEquals(PluginState.LOADED, plugin.getState());
        AgentInstanceRecorder.reset();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(2);
            AtomicInteger trueCount = new AtomicInteger();
            for (int i = 0; i < 2; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        if (manager.activatePlugin(PLUGIN_ID)) {
                            trueCount.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            done.await();
            assertEquals(2, trueCount.get(), "并发激活均返回 true（单飞收敛，不重跑）");
            assertEquals(1, AgentInstanceRecorder.activatedCount, "并发重复 activate 必须单飞（不重跑 activator）");
            assertEquals(PluginState.ACTIVATED, manager.getPlugin(PLUGIN_ID).getState());
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * unload 守卫：ACTIVATED 时 unload 抛明确异常；deactivate 后 unload 放行。
     */
    @Test
    public void testUnloadGuardThrowsWhenActivated() {
        manager.loadPlugin(MODEL_PROVIDER_ID);
        IPlugin plugin = manager.loadPlugin(PLUGIN_ID);
        assertEquals(PluginState.ACTIVATED, plugin.getState());

        NopException e = assertThrows(NopException.class, plugin::unload);
        assertEquals(ERR_PLUGIN_NOT_DEACTIVATED.getErrorCode(), e.getErrorCode());
        assertEquals(PluginState.ACTIVATED.name(), e.getParam("pluginState"));

        // manager 入口同样被守卫阻塞（定义保留，可先 deactivate 再重试 unload）
        NopException viaManager = assertThrows(NopException.class, () -> manager.unloadPlugin(PLUGIN_ID));
        assertEquals(ERR_PLUGIN_NOT_DEACTIVATED.getErrorCode(), viaManager.getErrorCode());
        assertNotNull(manager.getPlugin(PLUGIN_ID));

        FutureHelper.syncGet(plugin.deactivate());
        manager.unloadPlugin(PLUGIN_ID);
        assertEquals(PluginState.UNLOADED, plugin.getState());
    }

    /**
     * 激活失败：置 FAILED、错误可读（lastActivationError + 异常 cause 链）、可重试。
     */
    @Test
    public void testActivationFailureSetsFailedWithReadableError() {
        manager.loadPlugin(FAIL_ID);
        VfsPluginDefinition def = (VfsPluginDefinition) manager.getPlugin(FAIL_ID);
        assertEquals(PluginState.FAILED, def.getState(),
                "loadPlugin 自动激活失败 → 置 FAILED（reconcile 捕获记录，不上抛）");
        assertNotNull(def.getLastActivationError(), "错误可读（定义级记录）");
        assertTrue(def.getLastActivationError().getMessage().contains("boom"), "原始错误保留");
        assertEquals(1, FailActivator.attemptCount);

        // 显式 activatePlugin：异常显式上抛（wrapped，cause 保留）
        NopException e = assertThrows(NopException.class, () -> manager.activatePlugin(FAIL_ID));
        assertEquals(ERR_PLUGIN_ACTIVATION_FAILED.getErrorCode(), e.getErrorCode());
        assertNotNull(e.getCause());
        assertTrue(e.getCause().getMessage().contains("boom"), "原始错误保留为 cause");
        assertEquals(PluginState.FAILED, def.getState());
        assertEquals(2, FailActivator.attemptCount);

        // FAILED 可重试（仍失败 → 保持 FAILED，资源已回退不残留）
        assertThrows(NopException.class, () -> manager.activatePlugin(FAIL_ID));
        assertEquals(PluginState.FAILED, def.getState());
    }

    /**
     * 失败阈值暂停（机制自实例迁移到定义级，语义保持）：连续失败 ≥ 阈值（5）→ reconcile 不再
     * 自动重试（尝试计数不变）；显式 activatePlugin 可恢复尝试（绕过暂停，计数 +1）。
     */
    @Test
    public void testFailureThresholdPausesAutoActivation() {
        manager.loadPlugin(FAIL_ID);
        VfsPluginDefinition def = (VfsPluginDefinition) manager.getPlugin(FAIL_ID);
        assertEquals(1, FailActivator.attemptCount, "loadPlugin 自动激活失败（计数 1）");

        // reconcile 自动重试至阈值（5 次连续失败 → 暂停）
        manager.reconcilePlugins();
        manager.reconcilePlugins();
        manager.reconcilePlugins();
        manager.reconcilePlugins();
        assertEquals(5, FailActivator.attemptCount);
        assertTrue(def.isAutoActivationPaused(), "阈值后暂停自动激活");

        // 暂停后 reconcile 不再尝试（计数不变）
        manager.reconcilePlugins();
        assertEquals(5, FailActivator.attemptCount, "暂停后 reconcile 不再自动重试");

        // 显式 activatePlugin 恢复尝试（绕过暂停，计数 +1；失败仍置 FAILED）
        assertThrows(NopException.class, () -> manager.activatePlugin(FAIL_ID));
        assertEquals(6, FailActivator.attemptCount, "显式激活可恢复（计数 +1）");
        assertEquals(PluginState.FAILED, def.getState());
    }

    /**
     * getService 激活态绑定代理矩阵：ACTIVATED 可用 → deactivate 后 INACTIVE → 重激活后同一
     * 引用恢复可用（按调用重新解析）；集合代理按 bean id 恢复；仅接口/多候选规则保持。
     */
    @Test
    public void testServiceProxyLifecycleMatrix() {
        manager.loadPlugin(MODEL_PROVIDER_ID);
        manager.loadPlugin(PLUGIN_ID);

        IConfigReader reader = manager.getPlugin(PLUGIN_ID).getService(IConfigReader.class);
        assertTrue(Proxy.isProxyClass(reader.getClass()));
        assertEquals("global-timeout", reader.getTimeout(), "无定义级值回落全局 provider");

        Collection<ITool> tools = manager.getPlugin(PLUGIN_ID).getServices(ITool.class);
        assertEquals(2, tools.size());

        // 关门控 → deactivate → 同一代理引用调用抛 INACTIVE（快速失败，不悬空）
        closeToolsGate();
        FutureHelper.syncGet(manager.deactivatePlugin(PLUGIN_ID));
        NopException inactive = assertThrows(NopException.class, reader::getTimeout);
        assertEquals(ERR_PLUGIN_INACTIVE.getErrorCode(), inactive.getErrorCode());
        for (ITool t : tools) {
            assertThrows(NopException.class, t::getName);
        }

        // 重激活 → 同一代理引用恢复可用（绑定对象 = 插件激活态，按调用重新解析）
        openToolsGate();
        assertTrue(manager.activatePlugin(PLUGIN_ID));
        assertEquals("global-timeout", reader.getTimeout());
        boolean sawBash = false;
        boolean sawSearch = false;
        for (ITool t : tools) {
            if ("bash".equals(t.getName())) {
                sawBash = true;
            }
            if ("search".equals(t.getName())) {
                sawSearch = true;
            }
        }
        assertTrue(sawBash && sawSearch, "集合代理重激活后按 bean id 恢复");

        // 非接口类型（具体类）明确抛错：getService 与 getServices 同规则
        NopException concrete = assertThrows(NopException.class,
                () -> manager.getPlugin(PLUGIN_ID).getService(BashTool.class));
        assertEquals(ERR_PLUGIN_SERVICE_PROXY_ONLY_INTERFACE.getErrorCode(), concrete.getErrorCode());
        NopException concreteCollection = assertThrows(NopException.class,
                () -> manager.getPlugin(PLUGIN_ID).getServices(BashTool.class));
        assertEquals(ERR_PLUGIN_SERVICE_PROXY_ONLY_INTERFACE.getErrorCode(), concreteCollection.getErrorCode());
    }

    /**
     * 多候选与唯一实现分支：multi-candidate（无 primary）getService 抛明确异常、getServices
     * 返回全部实现；agent-instance 的 IConfigReader 为唯一实现分支（经代理）。
     */
    @Test
    public void testGetServiceMultipleCandidatesThrowsExplicitError() {
        manager.loadPlugin(MULTI_CANDIDATE_ID);
        IPlugin plugin = manager.getPlugin(MULTI_CANDIDATE_ID);
        assertEquals(PluginState.ACTIVATED, plugin.getState(), "无门控 → reconcile 自动激活");

        NopException e = assertThrows(NopException.class, () -> plugin.getService(ITool.class));
        assertEquals(ERR_PLUGIN_MULTIPLE_SERVICE_CANDIDATES.getErrorCode(), e.getErrorCode(),
                "多候选且无 primary → 明确异常（不静默返回集合）");
        assertEquals(2, plugin.getServices(ITool.class).size(), "集合版返回全部实现");

        // 唯一实现分支：agent-instance 的 IConfigReader 仅 config.reader bean 实现
        manager.loadPlugin(MODEL_PROVIDER_ID);
        manager.loadPlugin(PLUGIN_ID);
        IConfigReader reader = manager.getPlugin(PLUGIN_ID).getService(IConfigReader.class);
        assertTrue(Proxy.isProxyClass(reader.getClass()), "唯一实现分支也返回生命周期代理");
        assertEquals(1, manager.getPlugin(PLUGIN_ID).getServices(IConfigReader.class).size());
    }

    /**
     * 定义级命令路由：invokeCommand 分发于本插件激活容器；未激活抛 INACTIVE。
     */
    @Test
    public void testDefinitionLevelCommandRouting() {
        closeToolsGate();
        IPlugin plugin = manager.loadPlugin(PLUGIN_ID);

        NopException inactive = assertThrows(NopException.class,
                () -> plugin.invokeCommand("hello", Map.of("who", "world"), null, null));
        assertEquals(ERR_PLUGIN_INACTIVE.getErrorCode(), inactive.getErrorCode());

        openToolsGate();
        manager.loadPlugin(MODEL_PROVIDER_ID);
        manager.activatePlugin(PLUGIN_ID);

        Map<String, Object> result = plugin.invokeCommand("hello", Map.of("who", "world"), null, null);
        assertEquals("hello:world", result.get("result"), "命令 bean 分发于本插件激活容器");
        assertTrue(AgentInstanceRecorder.events().contains("command:hello"));
    }

    /**
     * 定义级配置域：updateConfig LOADED 缓存（下次 activate 应用）/ ACTIVATED 热应用
     * （合并视图刷新 + 经委托 provider 触发变更通知，bean 属性真实重绑定）；全局回落与全局无污染。
     */
    @Test
    public void testConfigDomainDefinitionLevel() {
        closeToolsGate();
        VfsPluginDefinition def = (VfsPluginDefinition) manager.loadPlugin(PLUGIN_ID);

        // LOADED 缓存：updateConfig 不激活、值进入定义级配置域
        def.updateConfig(Map.of("agent.mode", "dev", "agent.only-instance", "x"));
        assertEquals(PluginState.LOADED, def.getState());
        assertEquals("dev", def.getDefinitionConfig().get("agent.mode"));

        // 激活：缓存值送达子容器（bean 读定义级配置域，定义级键命中非全局回落）
        openToolsGate();
        manager.loadPlugin(MODEL_PROVIDER_ID);
        manager.activatePlugin(PLUGIN_ID);
        IConfigReader reader = def.getService(IConfigReader.class);
        assertEquals("dev", reader.getMode(), "LOADED 缓存的 updateConfig 在激活时应用");
        assertEquals("x", reader.getOnlyInstance(), "定义级键命中（非全局回落）");
        assertEquals("global-default", reader.getGlobalValue(), "无定义级值的键回落全局 provider");
        assertNull(AppConfig.getConfigProvider().getConfigValue("agent.only-instance", null),
                "AppConfig 全局无污染（定义级配置域不写全局）");

        // ACTIVATED 热应用：合并视图刷新 + bean 属性真实重绑定（响应式变更通知）
        def.updateConfig(Map.of("agent.mode", "prod"));
        assertEquals("prod", reader.getMode(), "热应用必须触发响应式重绑定");
        assertEquals("prod", def.getDefinitionConfig().get("agent.mode"));
        assertNull(AppConfig.getConfigProvider().getConfigValue("agent.mode", null),
                "全局无污染（agent.mode 只经定义级配置域流转）");
    }

    /**
     * start/stop 收敛（§7.1）：start = load + activate（门控关闭 no-op 不抛异常）；
     * stop = deactivate + unload。
     */
    @Test
    public void testStartStopConvergeToLoadActivate() {
        closeToolsGate();
        IPlugin plugin = manager.loadPlugin(PLUGIN_ID);

        // 门控关闭：start 不激活、不抛异常
        plugin.start("g", "a", "1.0", Map.of("agent.mode", "5"));
        assertEquals(PluginState.LOADED, plugin.getState());
        assertEquals(0, AgentInstanceRecorder.activatedCount);

        // 门控打开：start = load + activate
        openToolsGate();
        manager.loadPlugin(MODEL_PROVIDER_ID);
        plugin.start("g", "a", "1.0", Map.of("agent.mode", "5"));
        assertEquals(PluginState.ACTIVATED, plugin.getState());
        assertEquals(1, AgentInstanceRecorder.activatedCount);
        assertEquals("5", plugin.getService(IConfigReader.class).getMode(), "start config 进入定义级配置域");

        // stop = deactivate + unload（quiescence + UNLOADED）
        plugin.stop();
        assertEquals(PluginState.UNLOADED, plugin.getState());
        assertTrue(AgentInstanceRecorder.events().contains("return-disposed"), "stop 先回退 effect");
    }

    /**
     * IPluginContext 实现（registry）：getPlugin 命中/未命中、allPlugins 全量、reconcile 幂等。
     */
    @Test
    public void testPluginContextRegistry() {
        IPluginContext context = manager;
        assertNull(context.getPlugin(PLUGIN_ID));

        IPlugin plugin = manager.loadPlugin(PLUGIN_ID);
        assertSame(plugin, context.getPlugin(PLUGIN_ID));
        assertTrue(context.allPlugins().contains(plugin));
        assertEquals(manager.getLoadedPlugins().size(), context.allPlugins().size());

        // reconcile 幂等：状态不变
        context.reconcile();
        assertSame(plugin, context.getPlugin(PLUGIN_ID));
    }

    /**
     * scope 契约回归：可移除句柄 + close 幂等 + close 后注册抛异常（W3 契约在单激活下保持）。
     */
    @Test
    public void testScopeContract() {
        manager.loadPlugin(MODEL_PROVIDER_ID);
        manager.loadPlugin(PLUGIN_ID);
        IPluginScope scope = ((VfsPluginDefinition) manager.getPlugin(PLUGIN_ID)).getScope();
        assertEquals(2, scope.effects().size());

        Disposable handle = scope.effect(() -> AgentInstanceRecorder.event("manual-disposed"));
        assertEquals(3, scope.effects().size());
        handle.dispose();
        assertEquals(2, scope.effects().size());

        closeToolsGate();
        FutureHelper.syncGet(manager.deactivatePlugin(PLUGIN_ID));
        scope.close();
        assertThrows(IllegalStateException.class, () -> scope.effect(() -> {
        }), "close 后 effect 注册必须抛异常");
    }
}
