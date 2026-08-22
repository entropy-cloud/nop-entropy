package io.nop.plugin.manager;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.config.DefaultConfigReference;
import io.nop.api.core.config.IConfigChangeListener;
import io.nop.api.core.config.IConfigProvider;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.config.SimpleConfigProvider;
import io.nop.api.core.util.SourceLocation;
import io.nop.api.core.util.StaticValue;
import io.nop.core.initialize.CoreInitialization;
import io.nop.plugin.api.IPlugin;
import io.nop.plugin.api.PluginState;
import io.nop.plugin.manager.impl.PluginManagerImpl;
import io.nop.plugin.manager.impl.VfsPluginDefinition;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R1 插件级 coeffect + reconcile 测试（plan-2026-08-22-2309-1 Phase 2 收缩：
 * 实例级条件评估已删除，仅定义级 requires/if-property）：
 * <ul>
 *     <li>依赖链级联：provider 去激活 → reconcile → 依赖方自动去激活；恢复 → 自动激活
 *     （requires 语义 = 依赖定义处于 ACTIVATED——定义级判定）。</li>
 *     <li>门控：requires 未满足 → loadPlugin 保持 LOADED、activatePlugin 返回 false（no-op）。</li>
 *     <li>静态环检测：A↔B 互依赖 → reconcile 终止、unresolved 报告、环成员不激活。</li>
 *     <li>自动触发：全局配置订阅（可控 provider）/ 定义级 updateConfig 回调两链路。</li>
 *     <li>端到端：loadPlugin 依赖链 → 全局配置变化 → 自动 deactivate → 恢复 → 自动 activate。</li>
 * </ul>
 */
public class TestCoeffectReconcile {

    private static final String MODEL_PROVIDER_ID = "/nop/plugin/test/model-provider.plugin.xml";
    private static final String AGENT_INSTANCE_ID = "/nop/plugin/test/agent-instance.plugin.xml";
    private static final String CYCLE_A_ID = "/nop/plugin/test/cycle-a.plugin.xml";
    private static final String CYCLE_B_ID = "/nop/plugin/test/cycle-b.plugin.xml";
    private static final String GATE_CLOSED_ID = "/nop/plugin/test/gate-closed.plugin.xml";
    private static final String GLOBAL_TOOLS_ENABLED = "agent.tools.enabled";

    private PluginManagerImpl manager;
    private Object priorToolsEnabled;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
        // 全局配置基线（同 TestPluginLifecycle）：agent-instance 夹具 bean 依赖
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
        // 全局配置基线钉死：agent.tools.enabled 为 JVM 共享键（SimpleConfigProvider 静态）——
        // setUp 赋值 true、tearDown 恢复捕获的前值（防跨测试类污染，含翻转 false 的用例）
        priorToolsEnabled = AppConfig.getConfigProvider().getConfigValue(GLOBAL_TOOLS_ENABLED, null);
        AppConfig.getConfigProvider().assignConfigValue(GLOBAL_TOOLS_ENABLED, "true");
    }

    @AfterEach
    public void tearDown() {
        for (IPlugin plugin : manager.getLoadedPlugins()) {
            try {
                plugin.deactivate();
            } catch (RuntimeException ignore) {
                // Intentionally ignored: tearDown must proceed even for non-aware plugins
            }
            try {
                plugin.unload();
            } catch (RuntimeException ignore) {
                // Intentionally ignored: cleanup tolerance
            }
        }
        AppConfig.getConfigProvider().assignConfigValue(GLOBAL_TOOLS_ENABLED, priorToolsEnabled);
    }

    private void closeGate() {
        AppConfig.getConfigProvider().assignConfigValue(GLOBAL_TOOLS_ENABLED, "false");
    }

    private void openGate() {
        AppConfig.getConfigProvider().assignConfigValue(GLOBAL_TOOLS_ENABLED, "true");
    }

    /**
     * 接线验证 + 依赖链级联：requires 语义 = 依赖定义处于 ACTIVATED（定义级判定）——
     * unload provider（先直接去激活绕过 reconcile 重激活）→ reconcile 自动去激活依赖方；
     * 重新 load provider → reconcile 自动激活依赖方；显式 reconcile 幂等。
     * 另钉死固定点语义：无门控 provider 显式 deactivatePlugin 会被 reconcile 立即重激活
     * （01 §五"条件满足且未激活 → activate"）。
     */
    @Test
    public void testReconcileWiringAndDependencyCascade() {
        manager.loadPlugin(MODEL_PROVIDER_ID);
        IPlugin agent = manager.loadPlugin(AGENT_INSTANCE_ID);
        IPlugin provider = manager.getPlugin(MODEL_PROVIDER_ID);
        assertEquals(PluginState.ACTIVATED, provider.getState(), "无门控 provider：loadPlugin 自动激活");
        assertEquals(PluginState.ACTIVATED, agent.getState(),
                "级联评估：requires（provider ACTIVATED）+ if-property 满足 → reconcile 自动激活");

        // 固定点语义钉死：无门控 provider 的显式 deactivate 被 deactivatePlugin 触发的 reconcile 重激活
        manager.deactivatePlugin(MODEL_PROVIDER_ID).toCompletableFuture().join();
        assertEquals(PluginState.ACTIVATED, provider.getState(),
                "无门控定义显式去激活后，reconcile 固定点收敛回 ACTIVATED（设计 §五）");
        assertEquals(PluginState.ACTIVATED, agent.getState(), "依赖方不受影响（门控未变）");

        // requires 腿关闭：去激活 provider（直接 deactivate，不经 reconcile 触发点）→ unload →
        // reconcile → requires 不满足 → 依赖方级联去激活
        provider.deactivate().toCompletableFuture().join();
        manager.unloadPlugin(MODEL_PROVIDER_ID);
        assertNull(manager.getPlugin(MODEL_PROVIDER_ID));
        assertEquals(PluginState.LOADED, agent.getState(), "provider 卸载自动触发 reconcile → 依赖方级联去激活");

        // 恢复：重新 load provider（自动激活）→ reconcile → requires 恢复 → 依赖方自动激活
        manager.loadPlugin(MODEL_PROVIDER_ID);
        assertEquals(PluginState.ACTIVATED, manager.getPlugin(MODEL_PROVIDER_ID).getState());
        assertEquals(PluginState.ACTIVATED, agent.getState(), "provider 重载自动触发 reconcile → 依赖方级联激活");

        // 显式 reconcile 幂等：条件未变，状态不变
        manager.reconcilePlugins();
        assertEquals(PluginState.ACTIVATED, agent.getState());
    }

    /**
     * 门控：requires 引用不存在的 plugin → loadPlugin 保持 LOADED（reconcile 不激活）、
     * 显式 activatePlugin 返回 false（no-op，不抛异常）。
     */
    @Test
    public void testActivatePluginReturnsFalseWhenGateUnsatisfied() {
        IPlugin gate = manager.loadPlugin(GATE_CLOSED_ID);
        assertEquals(PluginState.LOADED, gate.getState(), "requires 引用不存在的 plugin → 门控关闭 → 不激活");
        assertFalse(manager.activatePlugin(GATE_CLOSED_ID), "门控未满足 → activatePlugin no-op 返回 false");
        assertEquals(PluginState.LOADED, gate.getState());
        assertTrue(((VfsPluginDefinition) gate).getScope() == null, "未激活无 scope");
    }

    /**
     * 静态环检测：A↔B 互依赖 → reconcile 终止（无死循环）、环成员保持 LOADED（门控强制关闭）、
     * unresolved 报告可观测（具体类 getUnresolvedPluginIds）。
     */
    @Test
    public void testCycleDetectionTerminatesAndReportsUnresolved() {
        VfsPluginDefinition defA = (VfsPluginDefinition) manager.loadPlugin(CYCLE_A_ID);
        VfsPluginDefinition defB = (VfsPluginDefinition) manager.loadPlugin(CYCLE_B_ID);

        manager.reconcilePlugins();
        assertEquals(PluginState.LOADED, defA.getState(), "环成员门控强制关闭 → 不激活");
        assertEquals(PluginState.LOADED, defB.getState());

        // 报告内容可观测：环成员 pluginId 均在 unresolved 集合
        assertTrue(manager.getUnresolvedPluginIds().contains(CYCLE_A_ID), "unresolved 报告含 cycle-a");
        assertTrue(manager.getUnresolvedPluginIds().contains(CYCLE_B_ID), "unresolved 报告含 cycle-b");

        // 收敛后重复 reconcile 不再变化（环成员 gate 关闭 → 不激活）
        manager.reconcilePlugins();
        assertEquals(PluginState.LOADED, defA.getState());
        assertEquals(PluginState.LOADED, defB.getState());
    }

    /**
     * 自动触发 (a)：全局配置订阅——注入可控 provider，load 时订阅定义级 if-property 键，
     * 手动触发 listener → reconcile 被调用 + 依赖链状态变化。
     */
    @Test
    public void testGlobalConfigSubscriptionTriggersReconcile() {
        ControllableConfigProvider provider = new ControllableConfigProvider();
        provider.assignConfigValue(GLOBAL_TOOLS_ENABLED, "true");
        manager.setGlobalConfigProvider(provider);

        manager.loadPlugin(MODEL_PROVIDER_ID);
        IPlugin agent = manager.loadPlugin(AGENT_INSTANCE_ID);
        assertEquals(PluginState.ACTIVATED, agent.getState());

        assertTrue(provider.subscriptions.containsKey(GLOBAL_TOOLS_ENABLED),
                "load 时订阅定义级 if-property 键（接线验证：订阅真实注册）");

        // 手动触发 listener → reconcile → 定义级 if-property 不满足 → 自动 deactivate
        provider.assignConfigValue(GLOBAL_TOOLS_ENABLED, "false");
        provider.fireChange(GLOBAL_TOOLS_ENABLED, "true");
        assertEquals(PluginState.LOADED, agent.getState(), "订阅回调 → reconcile → 自动去激活");

        // 恢复 → 自动 activate
        provider.assignConfigValue(GLOBAL_TOOLS_ENABLED, "true");
        provider.fireChange(GLOBAL_TOOLS_ENABLED, "false");
        assertEquals(PluginState.ACTIVATED, agent.getState(), "订阅回调 → reconcile → 自动激活");
    }

    /**
     * 自动触发 (c)：定义级 updateConfig 经 onConfigChanged 回调自动 reconcile
     * （接线验证：先关全局门控（SimpleConfigProvider 不触发订阅回调），updateConfig 触发
     * reconcile → 门控关闭 → 去激活）。
     */
    @Test
    public void testUpdateConfigTriggersReconcile() {
        manager.loadPlugin(MODEL_PROVIDER_ID);
        IPlugin agent = manager.loadPlugin(AGENT_INSTANCE_ID);
        assertEquals(PluginState.ACTIVATED, agent.getState());

        closeGate();
        agent.updateConfig(Map.of("agent.mode", "stage"));
        assertEquals(PluginState.LOADED, agent.getState(),
                "updateConfig 自动触发 reconcile → 全局门控已关 → 去激活");

        openGate();
        agent.updateConfig(Map.of("agent.mode", "prod"));
        assertEquals(PluginState.ACTIVATED, agent.getState(), "updateConfig 自动触发 reconcile → 门控恢复 → 激活");
    }

    /**
     * 端到端：loadPlugin（依赖链）→ 全局配置变化 → 自动 deactivate → 配置恢复 → 自动
     * activate 完整链路（可控 provider 驱动订阅回调）。
     */
    @Test
    public void testEndToEndConfigDrivenChain() {
        ControllableConfigProvider provider = new ControllableConfigProvider();
        provider.assignConfigValue(GLOBAL_TOOLS_ENABLED, "true");
        manager.setGlobalConfigProvider(provider);

        // loadPlugin 依赖链（model-provider → agent-instance）+ 自动激活
        manager.loadPlugin(MODEL_PROVIDER_ID);
        IPlugin agent = manager.loadPlugin(AGENT_INSTANCE_ID);
        assertEquals(PluginState.ACTIVATED, agent.getState());

        // 全局配置变化 → 订阅回调 → 自动 deactivate
        provider.assignConfigValue(GLOBAL_TOOLS_ENABLED, "false");
        provider.fireChange(GLOBAL_TOOLS_ENABLED, "true");
        assertEquals(PluginState.LOADED, agent.getState());

        // 配置恢复 → 自动 activate
        provider.assignConfigValue(GLOBAL_TOOLS_ENABLED, "true");
        provider.fireChange(GLOBAL_TOOLS_ENABLED, "false");
        assertEquals(PluginState.ACTIVATED, agent.getState());
    }

    /**
     * 定义级宽松比较：全局配置值为 Boolean.TRUE 时与 spec 字符串 "true" 等价匹配。
     */
    @Test
    public void testDefinitionLevelLooseBooleanComparison() {
        ControllableConfigProvider provider = new ControllableConfigProvider();
        provider.assignConfigValue(GLOBAL_TOOLS_ENABLED, Boolean.TRUE);
        manager.setGlobalConfigProvider(provider);

        manager.loadPlugin(MODEL_PROVIDER_ID);
        IPlugin agent = manager.loadPlugin(AGENT_INSTANCE_ID);
        assertEquals(PluginState.ACTIVATED, agent.getState(),
                "Boolean.TRUE 与 spec expected \"true\" 等价匹配（定义级）");
        assertNotNull(((VfsPluginDefinition) agent).getScope());
    }

    /**
     * 可控配置 provider（自动触发测试注入）：值读写委托 SimpleConfigProvider，
     * subscribeChange 记录订阅（返回可取消句柄），fireChange 手动触发监听器。
     */
    static class ControllableConfigProvider implements IConfigProvider {
        final SimpleConfigProvider delegate = new SimpleConfigProvider();
        final Map<String, List<IConfigChangeListener>> subscriptions = new ConcurrentHashMap<>();

        void fireChange(String varName, Object oldValue) {
            List<IConfigChangeListener> listeners = subscriptions.get(varName);
            if (listeners != null) {
                for (IConfigChangeListener listener : listeners) {
                    listener.onConfigChange(this, Map.of(varName, oldValue));
                }
            }
        }

        @Override
        public Map<String, DefaultConfigReference<?>> getConfigReferences() {
            return delegate.getConfigReferences();
        }

        @Override
        public Map<String, StaticValue<?>> getStaticConfigValues() {
            return delegate.getStaticConfigValues();
        }

        @Override
        public <T> IConfigReference<T> getConfigReference(String varName, Class<T> clazz, T defaultValue,
                                                          SourceLocation loc) {
            return delegate.getConfigReference(varName, clazz, defaultValue, loc);
        }

        @Override
        public <T> IConfigReference<T> getStaticConfigReference(String varName, Class<T> clazz, T defaultValue,
                                                                SourceLocation loc) {
            return delegate.getStaticConfigReference(varName, clazz, defaultValue, loc);
        }

        @Override
        public void reset() {
            delegate.reset();
        }

        @Override
        public boolean isDirty() {
            return delegate.isDirty();
        }

        @Override
        public <T> void updateConfigValue(IConfigReference<T> ref, T value) {
            delegate.updateConfigValue(ref, value);
        }

        @Override
        public void assignConfigValue(String name, Object value) {
            delegate.assignConfigValue(name, value);
        }

        @Override
        public Map<String, Object> getConfigValueForPrefix(String prefix) {
            return delegate.getConfigValueForPrefix(prefix);
        }

        @Override
        public Runnable subscribeChange(String pattern, IConfigChangeListener listener) {
            List<IConfigChangeListener> list =
                    subscriptions.computeIfAbsent(pattern, k -> new CopyOnWriteArrayList<>());
            list.add(listener);
            return () -> {
                list.remove(listener);
                if (list.isEmpty()) {
                    subscriptions.remove(pattern);
                }
            };
        }

        @Override
        public <T> T getConfigValue(String varName, T defaultValue) {
            return delegate.getConfigValue(varName, defaultValue);
        }
    }
}
