package io.nop.plugin.support;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.plugin.api.IPluginCancelToken;
import io.nop.plugin.api.IPluginInstance;
import io.nop.plugin.api.IPluginScope;
import io.nop.plugin.api.InstanceState;
import io.nop.plugin.api.PluginState;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static io.nop.plugin.api.PluginApiErrors.ARG_PLUGIN_ID;
import static io.nop.plugin.api.PluginApiErrors.ERR_PLUGIN_DEFINITION_NOT_FOUND;
import static io.nop.plugin.api.PluginApiErrors.ERR_PLUGIN_INACTIVE;
import static io.nop.plugin.api.PluginApiErrors.ERR_PLUGIN_MULTIPLE_INSTANCES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AbstractPlugin} 双路径行为测试（W2 Phase 2 + W3 收敛 + W4 路由）：
 * aware 路径 load 只到 LOADED 且不建子容器、定义缺失显式失败、unload 回 UNLOADED、
 * aware start 对 jar 轨实例化显式失败（successor 项）、aware stop 收敛为 unload、
 * 定义级 invokeCommand §7.1 三态路由（0 实例 INACTIVE / 1 实例委托 / 多实例抛
 * ERR_PLUGIN_MULTIPLE_INSTANCES，含 1 个 DEACTIVATED 实例的实例级 INACTIVE 边界）；
 * 兼容路径（非 aware 子类）保留旧 start/stop 语义、getState 保守值 LOADED。
 */
public class TestAbstractPlugin {

    static class AwarePlugin extends AbstractPlugin {
        private final String defPath;

        AwarePlugin(String defPath) {
            this.defPath = defPath;
        }

        @Override
        protected String getPluginDefinitionPath() {
            return defPath;
        }
    }

    /**
     * W4 路由测试子类：override getInstances() 返回可控实例列表
     * （AbstractPlugin 本身不 override——IPlugin default 返回空列表）。
     */
    static class AwarePluginWithInstances extends AwarePlugin {
        private final List<IPluginInstance> instances;

        AwarePluginWithInstances(String defPath, List<IPluginInstance> instances) {
            super(defPath);
            this.instances = instances;
        }

        @Override
        public List<IPluginInstance> getInstances() {
            return instances;
        }
    }

    /**
     * 路由 stub 实例：invokeCommand 记录调用并返回可断言结果；DEACTIVATED 时
     * 按实例级契约抛 INACTIVE（模拟 {@code PluginInstanceImpl} 的实例级检查）。
     */
    static class StubInstance implements IPluginInstance {
        private final String key;
        private final InstanceState state;
        private final Map<String, Object> result;
        volatile boolean invoked;

        StubInstance(String key, InstanceState state, Map<String, Object> result) {
            this.key = key;
            this.state = state;
            this.result = result;
        }

        @Override
        public String getInstanceKey() {
            return key;
        }

        @Override
        public InstanceState getState() {
            return state;
        }

        @Override
        public CompletionStage<Void> activate() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> deactivate() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public IPluginScope getScope() {
            return null;
        }

        @Override
        public <T> T getService(Class<T> serviceType) {
            return null;
        }

        @Override
        public <T> Collection<T> getServices(Class<T> serviceType) {
            return Collections.emptyList();
        }

        @Override
        public IPluginInstance getParent() {
            return null;
        }

        @Override
        public Map<String, Object> getConfig() {
            return Collections.emptyMap();
        }

        @Override
        public CompletionStage<Map<String, Object>> invokeCommandAsync(String command, Map<String, Object> args,
                                                                       String fieldSelection,
                                                                       IPluginCancelToken cancelToken) {
            invoked = true;
            if (state != InstanceState.ACTIVATED) {
                throw new NopException(ERR_PLUGIN_INACTIVE).param(ARG_PLUGIN_ID, key)
                        .param("instanceKey", key);
            }
            return CompletableFuture.completedFuture(result);
        }

        @Override
        public Map<String, Object> invokeCommand(String command, Map<String, Object> args,
                                                 String fieldSelection, IPluginCancelToken cancelToken) {
            return FutureHelper.syncGet(invokeCommandAsync(command, args, fieldSelection, cancelToken));
        }

        @Override
        public void destroy() {
        }
    }

    static class LegacyPlugin extends AbstractPlugin {
        @Override
        public boolean isStateMachineAware() {
            return false;
        }
    }

    private static final String TEST_DEF_PATH = "/nop/plugin/test/test-plugin.plugin.xml";

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testLoadParsesDefinitionAndDoesNotCreateChildContainer() {
        AwarePlugin plugin = new AwarePlugin(TEST_DEF_PATH);
        assertEquals(PluginState.UNLOADED, plugin.getState());
        assertNull(plugin.getPluginDefinition());

        plugin.load(Collections.emptyMap());

        assertEquals(PluginState.LOADED, plugin.getState());
        assertNotNull(plugin.getPluginDefinition());
        assertEquals("test-plugin", plugin.getPluginDefinition().prop_get("name"));
        assertNull(plugin.getBeanContainer(), "aware load 不得创建子容器（无 bean 实例化副作用）");
        assertTrue(plugin.getInstances().isEmpty());
        assertNotNull(plugin.getLoadTime());
    }

    @Test
    public void testLoadMissingDefinitionFailsExplicitly() {
        AwarePlugin plugin = new AwarePlugin("/nop/plugin.plugin.xml");
        NopException e = assertThrows(NopException.class, () -> plugin.load(Collections.emptyMap()));
        assertEquals(ERR_PLUGIN_DEFINITION_NOT_FOUND.getErrorCode(), e.getErrorCode());
        assertEquals("/nop/plugin.plugin.xml", e.getParam("pluginId"));
        assertEquals(PluginState.UNLOADED, plugin.getState(), "定义缺失时不得进入 LOADED");
        assertNull(plugin.getPluginDefinition());
    }

    @Test
    public void testUnloadDropsDefinition() {
        AwarePlugin plugin = new AwarePlugin(TEST_DEF_PATH);
        plugin.load(Collections.emptyMap());
        plugin.unload();

        assertEquals(PluginState.UNLOADED, plugin.getState());
        assertNull(plugin.getPluginDefinition());
    }

    @Test
    public void testAwareInvokeCommandThrowsInactive() {
        AwarePlugin plugin = new AwarePlugin(TEST_DEF_PATH);
        plugin.load(Collections.emptyMap());

        NopException async = assertThrows(NopException.class,
                () -> plugin.invokeCommandAsync("cmd", Collections.emptyMap(), null, null));
        assertEquals(ERR_PLUGIN_INACTIVE.getErrorCode(), async.getErrorCode());

        NopException sync = assertThrows(NopException.class,
                () -> plugin.invokeCommand("cmd", Collections.emptyMap(), null, null));
        assertEquals(ERR_PLUGIN_INACTIVE.getErrorCode(), sync.getErrorCode());
    }

    @Test
    public void testAwareInvokeCommandRoutesToSingleInstance() {
        // W4 §7.1：1 实例 → 定义级委托到该实例（接线验证：实例 invokeCommand 真实被调用）
        StubInstance stub = new StubInstance("only", InstanceState.ACTIVATED, Map.of("result", "ok"));
        AwarePluginWithInstances plugin = new AwarePluginWithInstances(TEST_DEF_PATH, List.of(stub));

        Map<String, Object> sync = plugin.invokeCommand("cmd", Collections.emptyMap(), null, null);
        assertEquals("ok", sync.get("result"));
        assertTrue(stub.invoked, "定义级路由必须经 IPluginInstance.invokeCommand");

        stub.invoked = false;
        Map<String, Object> async = FutureHelper.syncGet(
                plugin.invokeCommandAsync("cmd", Collections.emptyMap(), null, null));
        assertEquals("ok", async.get("result"));
        assertTrue(stub.invoked);
    }

    @Test
    public void testAwareInvokeCommandThrowsOnMultipleInstances() {
        StubInstance s1 = new StubInstance("a", InstanceState.ACTIVATED, Map.of("result", "1"));
        StubInstance s2 = new StubInstance("b", InstanceState.ACTIVATED, Map.of("result", "2"));
        AwarePluginWithInstances plugin = new AwarePluginWithInstances(TEST_DEF_PATH, List.of(s1, s2));

        NopException async = assertThrows(NopException.class,
                () -> plugin.invokeCommandAsync("cmd", Collections.emptyMap(), null, null));
        assertEquals(ERR_PLUGIN_MULTIPLE_INSTANCES.getErrorCode(), async.getErrorCode());
        assertEquals(TEST_DEF_PATH, async.getParam("pluginId"));
        assertTrue(async.getParam("instanceKeys").toString().contains("a"));
        assertTrue(async.getParam("instanceKeys").toString().contains("b"));
        assertFalse(s1.invoked && s2.invoked, "多实例歧义必须快速失败，不静默路由到任一实例");

        NopException sync = assertThrows(NopException.class,
                () -> plugin.invokeCommand("cmd", Collections.emptyMap(), null, null));
        assertEquals(ERR_PLUGIN_MULTIPLE_INSTANCES.getErrorCode(), sync.getErrorCode());
    }

    @Test
    public void testAwareInvokeCommandDeactivatedSingleInstanceDelegatesInactive() {
        // §7.1 边界：1 个 DEACTIVATED 实例 → 委托到实例级 INACTIVE（实例级检查决定）
        StubInstance stub = new StubInstance("deactivated", InstanceState.DEACTIVATED, null);
        AwarePluginWithInstances plugin = new AwarePluginWithInstances(TEST_DEF_PATH, List.of(stub));

        NopException e = assertThrows(NopException.class,
                () -> plugin.invokeCommand("cmd", Collections.emptyMap(), null, null));
        assertEquals(ERR_PLUGIN_INACTIVE.getErrorCode(), e.getErrorCode());
        assertTrue(stub.invoked, "DEACTIVATED 实例也必须被委托（INACTIVE 来自实例级检查而非定义级跳过）");
    }

    @Test
    public void testAwareStartFailsExplicitlyOnJarTrackInstanceCreation() {
        // W3 收敛：start = load + createInstance(默认 key)。AbstractPlugin 为 uber jar 轨持有类，
        // jar 轨实例化路径是显式 successor 项（W4/W7）——load 落地后 createInstance 明确失败
        AwarePlugin plugin = new AwarePlugin(TEST_DEF_PATH);
        assertThrows(UnsupportedOperationException.class,
                () -> plugin.start("g", "a", "1.0", Collections.emptyMap()));
        assertEquals(PluginState.LOADED, plugin.getState(), "start 失败前 load 已落地");
    }

    @Test
    public void testAwareStopConvergesToUnload() {
        // W3 收敛：stop = destroyInstance + unload；jar 轨无实例（createInstance 为 successor 项），
        // destroyInstance 空操作，unload 落地——不再抛临时失败异常
        AwarePlugin plugin = new AwarePlugin(TEST_DEF_PATH);
        plugin.load(Collections.emptyMap());
        plugin.stop();

        assertEquals(PluginState.UNLOADED, plugin.getState(), "aware stop 收敛为 unload");
        assertNull(plugin.getPluginDefinition());
    }

    @Test
    public void testCompatPathKeepsStartStopSemantics() {
        LegacyPlugin plugin = new LegacyPlugin();
        assertEquals(PluginState.LOADED, plugin.getState(), "非 aware 保守值：可见即 LOADED");

        Map<String, Object> config = Map.of("test.plugin.compat.value", "x");
        plugin.start("io.nop.plugin.test", "legacy-plugin", "1.0", config);

        assertTrue(plugin.isActive());
        assertFalse(plugin.isStopping());

        plugin.stop();
        assertFalse(plugin.isActive());
    }
}
