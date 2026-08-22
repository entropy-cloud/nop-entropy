package io.nop.plugin.manager;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.model.object.DynamicObject;
import io.nop.plugin.api.IPlugin;
import io.nop.plugin.api.IPluginScope;
import io.nop.plugin.api.NopPluginConstants;
import io.nop.plugin.api.PluginState;
import io.nop.plugin.manager.impl.PluginManagerImpl;
import io.nop.plugin.manager.impl.VfsPluginDefinition;
import io.nop.plugin.test.AgentInstanceRecorder;
import io.nop.plugin.test.IConfigReader;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.nop.plugin.api.PluginApiErrors.ERR_PLUGIN_INACTIVE;
import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_ACTIVATION_FAILED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R3 兼容路径与命令路由回归基线（VFS 轨，plan-2026-08-22-2309-3 Phase 1/2）：
 * <ul>
 *     <li>start 补 load 步（行为差异断言）：未先 load 的定义直接 start → LOADED 语义由
 *     start 内部补齐 → ACTIVATED；stop = deactivate + unload（中间经过 deactivate：
 *     effect 已回退 quiescence + 事件序列）。</li>
 *     <li>start/stop 状态边界（两轨统一规格）：已 LOADED 不重复 load（updateConfig 累积值
 *     保留）直接 activate；已 ACTIVATED 时 start 幂等 no-op（不重跑 activator、scope 不变）；
 *     UNLOADED 态 stop 幂等 no-op。</li>
 *     <li>无静默跳过：activate 失败在 start 路径显式传播（FAILED 态 + 异常），不吞。</li>
 *     <li>invokeCommand 定义级路由：ACTIVATED 命中插件容器命令 bean；未激活/deactivate 后
 *     抛 INACTIVE（定义级状态检查）；插件容器未命中时回退链（宿主回退 + default bean 兜底）
 *     可观测（与 jar 轨行为一致——标记 "host:{who}"）。</li>
 *     <li>端到端：loadPlugin → activatePlugin → invokeCommand 命令 bean 返回真实结果 →
 *     deactivatePlugin → invokeCommand 抛 INACTIVE，全链走通。</li>
 * </ul>
 *
 * <p>语义注记：本类仅经定义级 start/stop/invokeCommand 操作（不经 manager 生命周期入口），
 * 避免 reconcile 自动激活干扰边界断言；@BeforeEach 固定门控基线，@AfterEach 恢复。
 */
public class TestCommandRoutingRegression {

    private static final String PLUGIN_ID = "/nop/plugin/test/agent-instance.plugin.xml";
    private static final String MODEL_PROVIDER_ID = "/nop/plugin/test/model-provider.plugin.xml";
    private static final String FAIL_ID = "/nop/plugin/test/activate-fail.plugin.xml";
    private static final String GLOBAL_TOOLS_ENABLED = "agent.tools.enabled";

    private PluginManagerImpl manager;
    private Object priorToolsEnabled;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
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
        priorToolsEnabled = AppConfig.getConfigProvider().getConfigValue(GLOBAL_TOOLS_ENABLED, null);
        AppConfig.getConfigProvider().assignConfigValue(GLOBAL_TOOLS_ENABLED, "false");
    }

    @AfterEach
    public void tearDown() {
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
    }

    private void openToolsGate() {
        AppConfig.getConfigProvider().assignConfigValue(GLOBAL_TOOLS_ENABLED, "true");
    }

    /**
     * 直接构造（未先 load、未注入评估器——防御场景保守放行）的 VFS 定义：start 内部补
     * load 步 → ACTIVATED（R1 前旧基线为"不含 load 步、要求已 LOADED"——本断言钉死补步后的
     * 新语义）；命令路由真实走通；stop = deactivate + unload（中间经过 deactivate：effect
     * 已回退 quiescence + LIFO 事件序列可观测）。
     */
    @Test
    public void testVfsDirectStartWithoutPriorLoadIncludesLoadStep() {
        DynamicObject definition = (DynamicObject) new DslModelParser(NopPluginConstants.PLUGIN_XDEF_PATH)
                .parseFromVirtualPath(PLUGIN_ID);
        VfsPluginDefinition plugin = new VfsPluginDefinition(PLUGIN_ID, definition);
        assertEquals(PluginState.UNLOADED, plugin.getState(), "直接构造未 load：UNLOADED");

        plugin.start(null, null, null, Map.of("agent.mode", "direct-start"));
        assertEquals(PluginState.ACTIVATED, plugin.getState(), "start 补 load 步 → 无条件激活成功");
        assertEquals(1, AgentInstanceRecorder.activatedCount, "activator 真实执行");
        assertEquals("direct-start", plugin.getDefinitionConfig().get("agent.mode"), "start config 进入定义级配置域");

        Map<String, Object> result = plugin.invokeCommand("hello", Map.of("who", "world"), null, null);
        assertEquals("hello:world", result.get("result"), "激活容器命令 bean 真实返回结果");

        plugin.stop();
        assertEquals(PluginState.UNLOADED, plugin.getState(), "stop = deactivate + unload → UNLOADED");
        assertTrue(AgentInstanceRecorder.events().contains("return-disposed"), "stop 中间经过 deactivate（effect 已回退）");
        assertTrue(AgentInstanceRecorder.events().contains("bean-destroyed"), "deactivate 顺序：effect 回退先于 bean destroy");
        assertNull(plugin.getScope(), "quiescence：scope 已回退");
    }

    /**
     * start/stop 状态边界（VFS 轨，两轨统一规格）：已 LOADED 时 start 不重复 load
     * （updateConfig 累积值保留）直接 activate；已 ACTIVATED 时 start 幂等 no-op（不重跑
     * activator、scope 不变）；UNLOADED 态 stop 幂等 no-op。
     */
    @Test
    public void testVfsStartStopStateBoundaries() {
        // 前置：门控关闭装载（保持 LOADED）→ updateConfig 累积
        VfsPluginDefinition def = (VfsPluginDefinition) manager.loadPlugin(PLUGIN_ID);
        assertEquals(PluginState.LOADED, def.getState(), "门控关闭：loadPlugin 只到 LOADED");
        def.updateConfig(Map.of("agent.only-instance", "x"));
        assertEquals(PluginState.LOADED, def.getState());

        // 门控打开（requires=model-provider 由 manager 装载）→ start：不重复 load（累积值保留）直接 activate
        manager.loadPlugin(MODEL_PROVIDER_ID);
        openToolsGate();
        def.start(null, null, null, Map.of("agent.mode", "5"));
        assertEquals(PluginState.ACTIVATED, def.getState(), "LOADED → start 直接 activate");
        assertEquals("5", def.getDefinitionConfig().get("agent.mode"), "start config 并入定义级配置域");
        assertEquals("x", def.getDefinitionConfig().get("agent.only-instance"),
                "updateConfig 累积值保留（load(config) 会重置配置域——保留即证明不重复 load）");
        assertEquals("5", def.getService(IConfigReader.class).getMode(), "start config 激活时送达子容器");
        assertEquals("x", def.getService(IConfigReader.class).getOnlyInstance(), "累积值同样送达");

        // 已 ACTIVATED：start 幂等 no-op（不重跑 activator、scope 不变）
        IPluginScope scopeBefore = def.getScope();
        assertNotNull(scopeBefore);
        AgentInstanceRecorder.reset();
        def.start(null, null, null, null);
        assertEquals(PluginState.ACTIVATED, def.getState(), "ACTIVATED 重复 start 幂等");
        assertEquals(0, AgentInstanceRecorder.activatedCount, "幂等不重跑 activator");
        assertSame(scopeBefore, def.getScope(), "幂等不重建 scope/容器");

        // UNLOADED 态 stop 幂等 no-op（直接构造定义，不进 manager registry）
        DynamicObject definition = (DynamicObject) new DslModelParser(NopPluginConstants.PLUGIN_XDEF_PATH)
                .parseFromVirtualPath(MODEL_PROVIDER_ID);
        VfsPluginDefinition unloaded = new VfsPluginDefinition(MODEL_PROVIDER_ID, definition);
        unloaded.stop();
        assertEquals(PluginState.UNLOADED, unloaded.getState(), "UNLOADED 态 stop 幂等 no-op");
    }

    /**
     * 无静默跳过（VFS 轨）：activate 失败在 start 路径显式传播（FAILED 态 + 异常），不吞。
     */
    @Test
    public void testVfsStartPropagatesActivationFailureExplicitly() {
        manager.loadPlugin(FAIL_ID);
        IPlugin plugin = manager.getPlugin(FAIL_ID);
        assertEquals(PluginState.FAILED, plugin.getState(), "loadPlugin 自动激活失败 → FAILED");

        NopException e = assertThrows(NopException.class,
                () -> plugin.start("g", "a", "1.0", null),
                "start 路径必须显式传播 activate 失败");
        assertEquals(ERR_PLUGIN_ACTIVATION_FAILED.getErrorCode(), e.getErrorCode());
        assertEquals(PluginState.FAILED, plugin.getState(), "失败后保持 FAILED（可重试/unload）");
    }

    /**
     * invokeCommand 定义级状态检查（VFS 轨）：未激活抛 INACTIVE；激活后命中；deactivate 后
     * 再调用抛 INACTIVE（定义级状态检查，无实例路由）。
     */
    @Test
    public void testVfsCommandInactiveBeforeAndAfterDeactivate() {
        VfsPluginDefinition def = (VfsPluginDefinition) manager.loadPlugin(PLUGIN_ID);
        assertEquals(PluginState.LOADED, def.getState());

        NopException before = assertThrows(NopException.class,
                () -> def.invokeCommand("hello", Map.of("who", "world"), null, null));
        assertEquals(ERR_PLUGIN_INACTIVE.getErrorCode(), before.getErrorCode());

        manager.loadPlugin(MODEL_PROVIDER_ID);
        openToolsGate();
        assertTrue(manager.activatePlugin(PLUGIN_ID));
        assertEquals("hello:world", def.invokeCommand("hello", Map.of("who", "world"), null, null)
                .get("result"), "ACTIVATED 命中插件容器命令 bean");

        // 关门控 deactivate（门控开则 reconcile 会重激活）→ 定义级状态检查再抛 INACTIVE
        AppConfig.getConfigProvider().assignConfigValue(GLOBAL_TOOLS_ENABLED, "false");
        FutureHelper.syncGet(manager.deactivatePlugin(PLUGIN_ID));
        assertEquals(PluginState.LOADED, def.getState());
        NopException after = assertThrows(NopException.class,
                () -> def.invokeCommand("hello", Map.of("who", "world"), null, null));
        assertEquals(ERR_PLUGIN_INACTIVE.getErrorCode(), after.getErrorCode(), "deactivate 后定义级状态检查抛 INACTIVE");
    }

    /**
     * 回退链裁定（VFS 轨 aware，与 jar 轨行为一致）：插件激活容器未命中指定命令 bean 时——
     * 宿主回退命中（nopPluginCommand_host 只在宿主容器，标记 "host:{who}" 与 jar 轨一致）；
     * 未知名经 default bean 兜底命中（nopPluginCommand_default 只在宿主容器，不静默返回）。
     */
    @Test
    public void testVfsCommandFallbackChainHostAndDefault() {
        manager.loadPlugin(MODEL_PROVIDER_ID);
        openToolsGate();
        VfsPluginDefinition def = (VfsPluginDefinition) manager.loadPlugin(PLUGIN_ID);
        assertEquals(PluginState.ACTIVATED, def.getState());

        Map<String, Object> host = def.invokeCommand("host", Map.of("who", "world"), null, null);
        assertEquals("host:world", host.get("result"), "插件容器未命中 → 宿主回退命中（与 jar 轨标记一致）");

        Map<String, Object> fallback = def.invokeCommand("no-such-command", Map.of(), null, null);
        assertEquals("default:no-such-command", fallback.get("result"), "宿主亦未命中 → default bean 兜底命中");
    }

    /**
     * 端到端验证（Anti-Hollow）：loadPlugin → activatePlugin → invokeCommand 命令 bean 返回
     * 真实结果（接线验证：命令 bean 真实被调用）→ deactivatePlugin → invokeCommand 抛
     * INACTIVE，全链走通。
     */
    @Test
    public void testEndToEndLoadActivateInvokeDeactivate() {
        manager.loadPlugin(MODEL_PROVIDER_ID);
        openToolsGate();
        IPlugin plugin = manager.loadPlugin(PLUGIN_ID);
        assertEquals(PluginState.ACTIVATED, plugin.getState(), "门控满足：loadPlugin 自动激活");

        Map<String, Object> result = plugin.invokeCommand("hello", Map.of("who", "e2e"), null, null);
        assertEquals("hello:e2e", result.get("result"), "命令 bean 真实返回结果");
        assertTrue(AgentInstanceRecorder.events().contains("command:hello"), "接线验证：命令 bean 真实被调用");

        AppConfig.getConfigProvider().assignConfigValue(GLOBAL_TOOLS_ENABLED, "false");
        FutureHelper.syncGet(manager.deactivatePlugin(PLUGIN_ID));
        NopException inactive = assertThrows(NopException.class,
                () -> plugin.invokeCommand("hello", Map.of("who", "e2e"), null, null));
        assertEquals(ERR_PLUGIN_INACTIVE.getErrorCode(), inactive.getErrorCode(), "deactivate 后命令快速失败");
    }
}
