package io.nop.plugin.support;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.plugin.api.PluginState;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static io.nop.plugin.api.PluginApiErrors.ERR_PLUGIN_DEFINITION_NOT_FOUND;
import static io.nop.plugin.api.PluginApiErrors.ERR_PLUGIN_INACTIVE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AbstractPlugin} 双路径行为测试（W2 Phase 2）：
 * aware 路径 load 只到 LOADED 且不建子容器、定义缺失显式失败、unload 回 UNLOADED、
 * aware start/stop 显式失败、无实例 invokeCommand 抛 INACTIVE；
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
    public void testAwareStartStopFailExplicitly() {
        AwarePlugin plugin = new AwarePlugin(TEST_DEF_PATH);
        assertThrows(UnsupportedOperationException.class,
                () -> plugin.start("g", "a", "1.0", Collections.emptyMap()));
        assertThrows(UnsupportedOperationException.class, plugin::stop);
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
