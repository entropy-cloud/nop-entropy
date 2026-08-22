package io.nop.plugin.api;

import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import static io.nop.plugin.api.PluginApiErrors.ERR_PLUGIN_LIFECYCLE_NOT_SUPPORTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 钉死 {@link IPlugin} 生命周期 default 方法的默认行为（非状态机感知兼容路径）与
 * 单层六态枚举的可引用性：未 override 的最小实现必须呈现"不进入新状态机"的保守语义
 * 且显式失败（抛 {@link UnsupportedOperationException}，不静默 no-op），防止实现期发明行为。
 */
public class TestPluginDefaultMethods {

    static class MinimalPlugin implements IPlugin {
        @Override
        public String getPluginGroupId() {
            return null;
        }

        @Override
        public String getPluginArtifactId() {
            return null;
        }

        @Override
        public String getPluginVersion() {
            return null;
        }

        @Override
        public Timestamp getLastChangeTime() {
            return null;
        }

        @Override
        public Timestamp getLoadTime() {
            return null;
        }

        @Override
        public CompletionStage<Map<String, Object>> invokeCommandAsync(String command, Map<String, Object> args,
                                                                       String fieldSelection,
                                                                       IPluginCancelToken cancelToken) {
            return null;
        }

        @Override
        public Map<String, Object> invokeCommand(String command, Map<String, Object> args,
                                                 String fieldSelection,
                                                 IPluginCancelToken cancelToken) {
            return null;
        }

        @Override
        public void start(String pluginGroupId, String pluginArtifactId, String pluginVersion,
                          Map<String, Object> config) {
        }

        @Override
        public void stop() {
        }
    }

    @Test
    public void testSixStateEnumReferenceable() {
        assertEquals(6, PluginState.values().length, "单层六态：UNLOADED/LOADED/ACTIVATING/ACTIVATED/DEACTIVATING/FAILED");
        assertEquals(PluginState.UNLOADED, PluginState.valueOf("UNLOADED"));
        assertEquals(PluginState.LOADED, PluginState.valueOf("LOADED"));
        assertEquals(PluginState.ACTIVATING, PluginState.valueOf("ACTIVATING"));
        assertEquals(PluginState.ACTIVATED, PluginState.valueOf("ACTIVATED"));
        assertEquals(PluginState.DEACTIVATING, PluginState.valueOf("DEACTIVATING"));
        assertEquals(PluginState.FAILED, PluginState.valueOf("FAILED"));
    }

    @Test
    public void testIsStateMachineAwareDefaultFalse() {
        assertFalse(new MinimalPlugin().isStateMachineAware());
    }

    @Test
    public void testGetStateDefaultLoaded() {
        assertEquals(PluginState.LOADED, new MinimalPlugin().getState());
    }

    @Test
    public void testLifecycleDefaultsThrowExplicitError() {
        IPlugin plugin = new MinimalPlugin();
        assertThrows(NopException.class, () -> plugin.load(null));
        assertThrows(NopException.class, plugin::unload);
        assertThrows(NopException.class, plugin::activate,
                "activate 无状态机支撑时必须显式抛异常（不静默 no-op 返回 false）");
        assertThrows(NopException.class, plugin::deactivate);
        assertThrows(NopException.class, () -> plugin.updateConfig(Map.of()));
    }

    @Test
    public void testGetServiceDefaultsThrowExplicitError() {
        IPlugin plugin = new MinimalPlugin();
        assertThrows(NopException.class, () -> plugin.getService(Runnable.class));
        assertThrows(NopException.class, () -> plugin.getServices(Runnable.class));
    }

    @Test
    public void testMinimalPluginCompilesAgainstShrunkInterface() {
        // getInstance(instanceKey)/getInstances() 已从接口删除：最小实现不再需要感知实例机制，
        // 也不存在静默返回 null/空列表的 default 面
        assertTrue(new MinimalPlugin().getPluginVersion() == null);
    }
}
