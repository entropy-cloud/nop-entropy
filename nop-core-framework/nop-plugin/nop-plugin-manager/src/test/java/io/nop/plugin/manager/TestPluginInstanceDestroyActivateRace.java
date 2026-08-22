package io.nop.plugin.manager;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.plugin.api.IPlugin;
import io.nop.plugin.api.IPluginInstance;
import io.nop.plugin.api.InstanceState;
import io.nop.plugin.manager.impl.PluginManagerImpl;
import io.nop.plugin.test.AgentInstanceRecorder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_INSTANCE_ALREADY_DESTROYED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * destroy 与 activate/reconcile 竞态回归测试：
 * 修复前已销毁（registry 已移除）的实例 activate() 仍会成功——产生脱离注册表的
 * "僵尸"已激活容器（子容器已启动、effect 已注册，但无人再停止）。
 * 修复后 doActivate 入口校验注册表存在性，显式抛 ERR_PLUGIN_INSTANCE_ALREADY_DESTROYED。
 */
public class TestPluginInstanceDestroyActivateRace {

    private static final String PLUGIN_ID = "/nop/plugin/test/agent-instance.plugin.xml";
    private static final String MODEL_PROVIDER_ID = "/nop/plugin/test/model-provider.plugin.xml";
    private static final String GLOBAL_TOOLS_ENABLED = "agent.tools.enabled";

    private PluginManagerImpl manager;
    private Object priorToolsEnabled;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
        // 全局配置基线（与 TestPluginInstanceLifecycle 一致）：插件 bean 引用的全局键
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
        AppConfig.getConfigProvider().assignConfigValue(GLOBAL_TOOLS_ENABLED, "true");
        manager.loadPlugin(MODEL_PROVIDER_ID);
        manager.createInstance(MODEL_PROVIDER_ID, "provider-1", java.util.Map.of(), null);
    }

    @AfterEach
    public void tearDown() {
        for (IPlugin plugin : manager.getLoadedPlugins()) {
            for (IPluginInstance instance : plugin.getInstances()) {
                try {
                    instance.destroy();
                } catch (RuntimeException ignore) {
                    // 测试已自行销毁
                }
            }
        }
        AppConfig.getConfigProvider().assignConfigValue(GLOBAL_TOOLS_ENABLED, priorToolsEnabled);
    }

    @Test
    public void testActivateAfterDestroyThrowsInsteadOfZombie() {
        manager.loadPlugin(PLUGIN_ID);
        IPluginInstance inst = manager.createInstance(PLUGIN_ID, "race", java.util.Map.of(), null);
        assertEquals(InstanceState.ACTIVATED, inst.getState());

        // destroy：从 registry 移除并去激活
        manager.destroyInstance(PLUGIN_ID, "race");
        assertNull(manager.getInstance(PLUGIN_ID, "race"));

        // 修复前：activate() 成功（state 回到 ACTIVATED 但 registry 无此实例 → 僵尸容器）。
        // 修复后：显式拒绝
        NopException e = assertThrows(NopException.class, () -> FutureHelper.syncGet(inst.activate()));
        assertEquals(ERR_PLUGIN_INSTANCE_ALREADY_DESTROYED.getErrorCode(), e.getErrorCode());
        assertEquals("race", e.getParam("instanceKey"));
        assertEquals(InstanceState.DEACTIVATED, inst.getState(), "拒绝激活后实例不得处于 ACTIVATED 状态");
        assertNull(inst.getScope(), "不得残留已激活的 scope");
    }

    @Test
    public void testActivateAfterDirectDestroyThrows() {
        manager.loadPlugin(PLUGIN_ID);
        IPluginInstance inst = manager.createInstance(PLUGIN_ID, "direct", java.util.Map.of(), null);

        inst.destroy();
        assertNull(manager.getInstance(PLUGIN_ID, "direct"));

        assertThrows(NopException.class, () -> FutureHelper.syncGet(inst.activate()),
                "直接 destroy 路径同样不得允许复活");
        assertEquals(InstanceState.DEACTIVATED, inst.getState());
    }

    @Test
    public void testDeactivateActivateRoundtripUnaffected() {
        manager.loadPlugin(PLUGIN_ID);
        IPluginInstance inst = manager.createInstance(PLUGIN_ID, "rt", java.util.Map.of(), null);
        inst.deactivate();

        // 存活实例（registry 内）的正常重激活不受注册表校验影响
        FutureHelper.syncGet(inst.activate());
        assertEquals(InstanceState.ACTIVATED, inst.getState());
        assertNotNull(manager.getInstance(PLUGIN_ID, "rt"));
    }
}
