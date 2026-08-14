package io.nop.plugin.api;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 钉死 {@link IPlugin} 新增 default 方法的默认行为（非状态机感知兼容路径）：
 * 未 override 的最小实现必须呈现"不进入新状态机"的保守语义，防止实现期发明行为。
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
    public void testIsStateMachineAwareDefaultFalse() {
        assertFalse(new MinimalPlugin().isStateMachineAware());
    }

    @Test
    public void testGetStateDefaultLoaded() {
        assertEquals(PluginState.LOADED, new MinimalPlugin().getState());
    }

    @Test
    public void testGetInstanceDefaultNull() {
        assertNull(new MinimalPlugin().getInstance("key"));
    }

    @Test
    public void testGetInstancesDefaultEmpty() {
        assertTrue(new MinimalPlugin().getInstances().isEmpty());
    }

    @Test
    public void testLoadDefaultThrowsUnsupportedOperation() {
        IPlugin plugin = new MinimalPlugin();
        assertThrows(UnsupportedOperationException.class, () -> plugin.load(null));
    }

    @Test
    public void testUnloadDefaultThrowsUnsupportedOperation() {
        IPlugin plugin = new MinimalPlugin();
        assertThrows(UnsupportedOperationException.class, plugin::unload);
    }
}
