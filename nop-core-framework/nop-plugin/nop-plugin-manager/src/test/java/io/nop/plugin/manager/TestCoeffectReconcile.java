package io.nop.plugin.manager;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.config.DefaultConfigReference;
import io.nop.api.core.config.IConfigChangeListener;
import io.nop.api.core.config.IConfigProvider;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.config.SimpleConfigProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.SourceLocation;
import io.nop.api.core.util.StaticValue;
import io.nop.core.initialize.CoreInitialization;
import io.nop.plugin.api.IPlugin;
import io.nop.plugin.api.IPluginInstance;
import io.nop.plugin.api.InstanceState;
import io.nop.plugin.manager.impl.PluginInstanceImpl;
import io.nop.plugin.manager.impl.PluginManagerImpl;
import io.nop.plugin.manager.impl.VfsPluginDefinition;
import io.nop.plugin.test.AgentInstanceRecorder;
import io.nop.plugin.test.FailActivator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_INSTANCE_EXISTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W5 Phase 4：coeffect + reconcile 测试补全（Plan-2026-08-14-2007-2）。
 * <ul>
 *     <li>依赖链级联（2 定义 + 3 实例拓扑）：父实例 activate/deactivate → reconcile → 子实例自动激活/去激活。</li>
 *     <li>实例级 if-property：合并视图优先 + 全局回退；实例配置携带 false 的实例被级联 deactivate（隔离差异）；updateConfig 驱动往返。</li>
 *     <li>createInstance 门控：requires 未满足 → null；重复 key 检查先于门控；start 门控关闭不创建实例。</li>
 *     <li>静态环检测：A↔B 互依赖 → reconcile 终止、unresolved 报告、环成员均 deactivate。</li>
 *     <li>自动触发：全局配置订阅（可控 provider）/ 生命周期操作 / updateConfig 三链路。</li>
 *     <li>失败阈值暂停：连续激活失败 ≥ 阈值 → reconcile 不再自动重试；显式 activate() 可恢复。</li>
 *     <li>端到端（03 §1.4 场景）：loadPlugin → createInstance → 全局配置变化 → 自动 deactivate → 恢复 → 自动 activate。</li>
 * </ul>
 */
public class TestCoeffectReconcile {

    private static final String MODEL_PROVIDER_ID = "/nop/plugin/test/model-provider.plugin.xml";
    private static final String AGENT_INSTANCE_ID = "/nop/plugin/test/agent-instance.plugin.xml";
    private static final String CYCLE_A_ID = "/nop/plugin/test/cycle-a.plugin.xml";
    private static final String CYCLE_B_ID = "/nop/plugin/test/cycle-b.plugin.xml";
    private static final String GATE_CLOSED_ID = "/nop/plugin/test/gate-closed.plugin.xml";
    private static final String FAIL_ID = "/nop/plugin/test/activate-fail.plugin.xml";
    private static final String GLOBAL_TOOLS_ENABLED = "agent.tools.enabled";

    private PluginManagerImpl manager;
    private Object priorToolsEnabled;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
        // 全局配置基线（同 TestPluginInstanceLifecycle）：agent-instance 夹具 bean 依赖
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
        // 全局配置基线钉死：agent.tools.enabled 为 JVM 共享键（SimpleConfigProvider 静态）——
        // setUp 赋值 true、tearDown 恢复捕获的前值（防跨测试类污染，含翻转 false 的用例）
        priorToolsEnabled = AppConfig.getConfigProvider().getConfigValue(GLOBAL_TOOLS_ENABLED, null);
        AppConfig.getConfigProvider().assignConfigValue(GLOBAL_TOOLS_ENABLED, "true");
    }

    @AfterEach
    public void tearDown() {
        for (IPlugin plugin : manager.getLoadedPlugins()) {
            for (IPluginInstance instance : plugin.getInstances()) {
                try {
                    instance.destroy();
                } catch (RuntimeException ignore) {
                    // Intentionally ignored: tearDown must proceed even if a deactivated instance rejects destroy
                }
            }
        }
        AppConfig.getConfigProvider().assignConfigValue(GLOBAL_TOOLS_ENABLED, priorToolsEnabled);
    }

    private void openGate() {
        manager.loadPlugin(MODEL_PROVIDER_ID);
        manager.createInstance(MODEL_PROVIDER_ID, "p1", Map.of(), null);
        manager.loadPlugin(AGENT_INSTANCE_ID);
    }

    /**
     * 接线验证 + 依赖链级联：reconcile 真实遍历 manager 的全部定义与实例
     * （2 定义 + 3 实例拓扑），父实例状态变化经 reconcile 级联到子实例。
     */
    @Test
    public void testReconcileWiringAndDependencyCascade() {
        openGate();
        IPluginInstance a1 = manager.createInstance(AGENT_INSTANCE_ID, "agent-1", Map.of(), null);
        IPluginInstance a2 = manager.createInstance(AGENT_INSTANCE_ID, "agent-2", Map.of(), null);
        assertEquals(InstanceState.ACTIVATED, a1.getState(), "级联评估：requires+if-property 满足 → 保持激活");
        assertEquals(InstanceState.ACTIVATED, a2.getState());

        // 父实例 deactivate → 显式 reconcile → 子实例自动 deactivate
        manager.destroyInstance(MODEL_PROVIDER_ID, "p1");
        assertEquals(InstanceState.DEACTIVATED, a1.getState(), "destroy 父实例自动触发 reconcile → 子实例级联去激活");
        assertEquals(InstanceState.DEACTIVATED, a2.getState());

        // 父恢复 → 子自动激活
        manager.createInstance(MODEL_PROVIDER_ID, "p1", Map.of(), null);
        assertEquals(InstanceState.ACTIVATED, a1.getState(), "create 父实例自动触发 reconcile → 子实例级联激活");
        assertEquals(InstanceState.ACTIVATED, a2.getState());

        // 显式 reconcile 幂等：条件未变，状态不变
        manager.reconcileInstances();
        assertEquals(InstanceState.ACTIVATED, a1.getState());
        assertEquals(InstanceState.ACTIVATED, a2.getState());
    }

    /**
     * 实例级 if-property：合并视图优先 + 全局回退；实例配置携带 false 的实例被级联 deactivate
     * （与全局 true 的实例差异——隔离差异断言）；定义级 updateConfig 驱动 activate/deactivate 往返。
     */
    @Test
    public void testInstanceLevelIfPropertyIsolationAndRoundTrip() {
        manager.loadPlugin(MODEL_PROVIDER_ID);
        manager.createInstance(MODEL_PROVIDER_ID, "p1", Map.of(), null);
        IPlugin plugin = manager.loadPlugin(AGENT_INSTANCE_ID);

        // agent-1 无实例键（全局回退 true）；agent-2 实例配置携带 false
        IPluginInstance a1 = manager.createInstance(AGENT_INSTANCE_ID, "agent-1", Map.of(), null);
        IPluginInstance a2 = manager.createInstance(AGENT_INSTANCE_ID, "agent-2",
                Map.of(GLOBAL_TOOLS_ENABLED, "false"), null);
        assertEquals(InstanceState.ACTIVATED, a1.getState(), "未命中实例视图 → 全局回退 true");
        assertEquals(InstanceState.DEACTIVATED, a2.getState(), "实例配置 false → 实例级不满足 → 级联 deactivate（隔离差异）");

        // 定义级 updateConfig 写入 agent.tools.enabled=false → 实例级（合并视图优先）→ 自动 reconcile → deactivate
        plugin.updateConfig(Map.of(GLOBAL_TOOLS_ENABLED, "false"));
        assertEquals(InstanceState.DEACTIVATED, a1.getState(), "updateConfig 自动触发 reconcile → 往返去激活");
        assertEquals(InstanceState.DEACTIVATED, a2.getState());

        // 恢复 true → 自动 reconcile → 往返激活；a2 实例配置仍 false → 保持 deactivate
        plugin.updateConfig(Map.of(GLOBAL_TOOLS_ENABLED, "true"));
        assertEquals(InstanceState.ACTIVATED, a1.getState(), "updateConfig 自动触发 reconcile → 往返激活");
        assertEquals(InstanceState.DEACTIVATED, a2.getState(), "实例级 false 覆盖定义级 true → 隔离差异保持");
    }

    /**
     * createInstance 门控：requires 未满足 → no-op 返回 null；满足 → 创建成功；start 门控关闭不创建实例。
     */
    @Test
    public void testCreateInstanceGateReturnsNullWhenUnsatisfied() {
        manager.loadPlugin(GATE_CLOSED_ID);
        assertNull(manager.createInstance(GATE_CLOSED_ID, "g1", Map.of(), null),
                "requires 引用不存在的 plugin id → 门控关闭 → null（no-op）");
        assertTrue(manager.getInstances(GATE_CLOSED_ID).isEmpty(), "门控关闭不注册实例");

        // start 门控关闭：不创建实例、不抛异常（日志路径）
        IPlugin gate = manager.loadPlugin(GATE_CLOSED_ID);
        gate.start("g", "a", "1.0", Map.of());
        assertTrue(manager.getInstances(GATE_CLOSED_ID).isEmpty(), "start 门控关闭不创建实例");

        // 满足路径回归
        manager.loadPlugin(MODEL_PROVIDER_ID);
        assertNotNull(manager.createInstance(MODEL_PROVIDER_ID, "p1", Map.of(), null));
    }

    /**
     * 门控边界：重复 key 检查先于门控——实例已存在（先开后关场景）仍抛重复 key 异常，不静默返回 null。
     */
    @Test
    public void testDuplicateKeyCheckPrecedesGate() {
        manager.loadPlugin(MODEL_PROVIDER_ID);
        manager.createInstance(MODEL_PROVIDER_ID, "p1", Map.of(), null);
        manager.loadPlugin(AGENT_INSTANCE_ID);
        manager.createInstance(AGENT_INSTANCE_ID, "dup", Map.of(), null);

        // 关闭门控（destroy 依赖实例 → 自动 reconcile → agent-instance 门控关闭）
        manager.destroyInstance(MODEL_PROVIDER_ID, "p1");
        assertNull(manager.createInstance(AGENT_INSTANCE_ID, "new-key", Map.of(), null),
                "门控关闭 → 新 key 返回 null");

        // 同 key 重复创建：重复 key 检查先于门控 → 仍抛明确异常
        NopException e = assertThrows(NopException.class,
                () -> manager.createInstance(AGENT_INSTANCE_ID, "dup", Map.of(), null));
        assertEquals(ERR_PLUGIN_INSTANCE_EXISTS.getErrorCode(), e.getErrorCode());
        assertEquals("dup", e.getParam("instanceKey"));
    }

    /**
     * 静态环检测：A↔B 互依赖 → reconcile 终止（无死循环）、环成员实例 deactivate、
     * unresolved 报告可观测（具体类 getUnresolvedPluginIds）。
     */
    @Test
    public void testCycleDetectionTerminatesAndReportsUnresolved() {
        VfsPluginDefinition defA = (VfsPluginDefinition) manager.loadPlugin(CYCLE_A_ID);
        VfsPluginDefinition defB = (VfsPluginDefinition) manager.loadPlugin(CYCLE_B_ID);

        // 环成员实例经 registry 变更入口种子（门控语义下 createInstance 无法派生环成员实例）
        PluginInstanceImpl a1 = new PluginInstanceImpl(defA, "a1", Map.of(), null);
        defA.addInstance(a1);
        PluginInstanceImpl b1 = new PluginInstanceImpl(defB, "b1", Map.of(), null);
        defB.addInstance(b1);
        FutureHelper.syncGet(a1.activate());
        FutureHelper.syncGet(b1.activate());
        assertEquals(InstanceState.ACTIVATED, a1.getState());
        assertEquals(InstanceState.ACTIVATED, b1.getState());

        manager.reconcileInstances();
        assertEquals(InstanceState.DEACTIVATED, a1.getState(), "环成员实例被 deactivate（级联语义）");
        assertEquals(InstanceState.DEACTIVATED, b1.getState());

        // 报告内容可观测：环成员 pluginId 均在 unresolved 集合
        assertTrue(manager.getUnresolvedPluginIds().contains(CYCLE_A_ID), "unresolved 报告含 cycle-a");
        assertTrue(manager.getUnresolvedPluginIds().contains(CYCLE_B_ID), "unresolved 报告含 cycle-b");

        // 收敛后重复 reconcile 不再变化（环成员 gate 关闭 → 不激活）
        manager.reconcileInstances();
        assertEquals(InstanceState.DEACTIVATED, a1.getState());
        assertEquals(InstanceState.DEACTIVATED, b1.getState());
    }

    /**
     * 失败阈值暂停：activate 持续失败 ≥ 阈值 → reconcile 不再自动重试（尝试计数器不变）；
     * 显式 activate() 恢复（计数 +1）。
     */
    @Test
    public void testFailThresholdPausesAutoActivation() {
        manager.loadPlugin(FAIL_ID);
        // createInstance 激活失败（计数 1），实例回退 DEACTIVATED 留在 registry
        assertThrows(NopException.class, () -> manager.createInstance(FAIL_ID, "f1", Map.of(), null));
        IPluginInstance inst = manager.getInstance(FAIL_ID, "f1");
        assertNotNull(inst);
        assertEquals(InstanceState.DEACTIVATED, inst.getState());
        assertEquals(1, FailActivator.attemptCount);

        // reconcile 自动重试至阈值（5 次连续失败 → 暂停）
        manager.reconcileInstances();
        manager.reconcileInstances();
        manager.reconcileInstances();
        manager.reconcileInstances();
        assertEquals(5, FailActivator.attemptCount);
        assertTrue(((PluginInstanceImpl) inst).isAutoActivationPaused(), "阈值后暂停自动激活");

        // 暂停后 reconcile 不再尝试（计数不变）
        manager.reconcileInstances();
        assertEquals(5, FailActivator.attemptCount, "暂停后 reconcile 不再自动重试");

        // 显式 activate() 恢复（绕过暂停，计数 +1；失败仍回退 DEACTIVATED）
        assertThrows(NopException.class, () -> FutureHelper.syncGet(inst.activate()));
        assertEquals(6, FailActivator.attemptCount, "显式激活可恢复（计数 +1）");
        assertEquals(InstanceState.DEACTIVATED, inst.getState());
    }

    /**
     * 自动触发 (a)：全局配置订阅——注入可控 provider，load 时订阅定义级 if-property 键，
     * 手动触发 listener → reconcile 被调用 + 实例状态变化。
     */
    @Test
    public void testGlobalConfigSubscriptionTriggersReconcile() {
        ControllableConfigProvider provider = new ControllableConfigProvider();
        provider.assignConfigValue(GLOBAL_TOOLS_ENABLED, "true");
        manager.setGlobalConfigProvider(provider);

        manager.loadPlugin(MODEL_PROVIDER_ID);
        manager.createInstance(MODEL_PROVIDER_ID, "p1", Map.of(), null);
        manager.loadPlugin(AGENT_INSTANCE_ID);
        IPluginInstance a1 = manager.createInstance(AGENT_INSTANCE_ID, "agent-1", Map.of(), null);
        assertEquals(InstanceState.ACTIVATED, a1.getState());

        assertTrue(provider.subscriptions.containsKey(GLOBAL_TOOLS_ENABLED),
                "load 时订阅定义级 if-property 键（接线验证：订阅真实注册）");

        // 手动触发 listener → reconcile → 定义级 if-property 不满足 → 实例自动 deactivate
        provider.assignConfigValue(GLOBAL_TOOLS_ENABLED, "false");
        provider.fireChange(GLOBAL_TOOLS_ENABLED, "true");
        assertEquals(InstanceState.DEACTIVATED, a1.getState(), "订阅回调 → reconcile → 自动去激活");

        // 恢复 → 自动 activate
        provider.assignConfigValue(GLOBAL_TOOLS_ENABLED, "true");
        provider.fireChange(GLOBAL_TOOLS_ENABLED, "false");
        assertEquals(InstanceState.ACTIVATED, a1.getState(), "订阅回调 → reconcile → 自动激活");
    }

    /**
     * 自动触发 (b)：生命周期操作成功路径——createInstance / destroyInstance 后自动 reconcile
     * （loadPlugin 触发经 reconcile 状态收敛，不自动创建实例）。
     */
    @Test
    public void testLifecycleOperationsTriggerReconcile() {
        openGate();
        IPluginInstance a1 = manager.createInstance(AGENT_INSTANCE_ID, "agent-1", Map.of(), null);
        assertEquals(InstanceState.ACTIVATED, a1.getState());

        // destroyInstance → 自动 reconcile → 依赖不满足 → 子实例自动 deactivate
        manager.destroyInstance(MODEL_PROVIDER_ID, "p1");
        assertEquals(InstanceState.DEACTIVATED, a1.getState(), "destroyInstance 自动触发 reconcile");

        // createInstance → 自动 reconcile → 依赖恢复 → 子实例自动 activate
        manager.createInstance(MODEL_PROVIDER_ID, "p1", Map.of(), null);
        assertEquals(InstanceState.ACTIVATED, a1.getState(), "createInstance 自动触发 reconcile");
    }

    /**
     * 自动触发 (c)：定义级 updateConfig 经 onConfigChanged 回调自动 reconcile
     * （实例级条件的唯一变化源——实例配置在 createInstance 后无独立变更 API）。
     */
    @Test
    public void testUpdateConfigTriggersReconcile() {
        openGate();
        IPluginInstance a1 = manager.createInstance(AGENT_INSTANCE_ID, "agent-1", Map.of(), null);
        assertEquals(InstanceState.ACTIVATED, a1.getState());

        IPlugin plugin = manager.loadPlugin(AGENT_INSTANCE_ID);
        plugin.updateConfig(Map.of(GLOBAL_TOOLS_ENABLED, "false"));
        assertEquals(InstanceState.DEACTIVATED, a1.getState(), "updateConfig 自动触发 reconcile → 去激活");

        plugin.updateConfig(Map.of(GLOBAL_TOOLS_ENABLED, "true"));
        assertEquals(InstanceState.ACTIVATED, a1.getState(), "updateConfig 自动触发 reconcile → 激活");
    }

    /**
     * 端到端（03 §1.4 场景）：loadPlugin（依赖链）→ createInstance → 全局配置变化
     * → 自动 deactivate → 配置恢复 → 自动 activate 完整链路。
     */
    @Test
    public void testEndToEndConfigDrivenChain() {
        ControllableConfigProvider provider = new ControllableConfigProvider();
        provider.assignConfigValue(GLOBAL_TOOLS_ENABLED, "true");
        manager.setGlobalConfigProvider(provider);

        // loadPlugin 依赖链（model-provider → agent-instance）+ createInstance
        manager.loadPlugin(MODEL_PROVIDER_ID);
        manager.createInstance(MODEL_PROVIDER_ID, "p1", Map.of(), null);
        manager.loadPlugin(AGENT_INSTANCE_ID);
        IPluginInstance a1 = manager.createInstance(AGENT_INSTANCE_ID, "agent-1", Map.of(), null);
        assertEquals(InstanceState.ACTIVATED, a1.getState());

        // 全局配置变化 → 订阅回调 → 自动 deactivate
        provider.assignConfigValue(GLOBAL_TOOLS_ENABLED, "false");
        provider.fireChange(GLOBAL_TOOLS_ENABLED, "true");
        assertEquals(InstanceState.DEACTIVATED, a1.getState());

        // 配置恢复 → 自动 activate
        provider.assignConfigValue(GLOBAL_TOOLS_ENABLED, "true");
        provider.fireChange(GLOBAL_TOOLS_ENABLED, "false");
        assertEquals(InstanceState.ACTIVATED, a1.getState());
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
        manager.createInstance(MODEL_PROVIDER_ID, "p1", Map.of(), null);
        manager.loadPlugin(AGENT_INSTANCE_ID);
        assertNotNull(manager.createInstance(AGENT_INSTANCE_ID, "agent-1", Map.of(), null),
                "Boolean.TRUE 与 spec expected \"true\" 等价匹配（定义级）");

        // 实例级：实例配置 Boolean.TRUE 与 spec "true" 等价
        IPluginInstance inst = manager.createInstance(AGENT_INSTANCE_ID, "agent-2",
                Map.of(GLOBAL_TOOLS_ENABLED, Boolean.TRUE), null);
        assertEquals(InstanceState.ACTIVATED, inst.getState(), "实例级 Boolean.TRUE 等价匹配");
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
