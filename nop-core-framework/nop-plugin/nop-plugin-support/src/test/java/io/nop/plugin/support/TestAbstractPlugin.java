package io.nop.plugin.support;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.plugin.api.PluginApiErrors;
import io.nop.plugin.api.PluginState;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static io.nop.plugin.api.PluginApiErrors.ERR_PLUGIN_INACTIVE;
import static io.nop.plugin.api.PluginApiErrors.ERR_PLUGIN_NOT_DEACTIVATED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AbstractPlugin} 双路径行为测试（R1 jar 轨契约，plan-2026-08-22-2309-1 Phase 2）：
 * <ul>
 *     <li>load 容忍 xdef 缺失：约定路径无 *.plugin.xml → 空定义加载成功；有载体 → 解析持有
 *     （仅元数据，不驱动门控/activator）。</li>
 *     <li>activate 门控恒空集：无条件激活（fixture 的 requires/if-property 均不满足仍达
 *     ACTIVATED）；激活回调跳过（无 activator）；容器构建复用 doStart 路径逻辑。</li>
 *     <li>invokeCommand 定义级路由：命令 bean 分发于本插件激活容器；未激活显式抛 INACTIVE。</li>
 *     <li>unload 守卫：ACTIVATED 时 unload 抛明确异常；deactivate 后放行。</li>
 *     <li>start/stop 收敛：start = load + activate、stop = deactivate + unload。</li>
 *     <li>兼容路径（非 aware 子类）保留旧 start/stop 语义、getState 保守值 LOADED。</li>
 * </ul>
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
    public void testLoadWithXdefCarrierParsesAndHoldsAsMetadata() {
        AwarePlugin plugin = new AwarePlugin(TEST_DEF_PATH);
        assertEquals(PluginState.UNLOADED, plugin.getState());
        assertNull(plugin.getPluginDefinition());

        plugin.load(Collections.emptyMap());

        assertEquals(PluginState.LOADED, plugin.getState());
        assertNotNull(plugin.getPluginDefinition(), "有 xdef 载体 → 解析持有");
        assertEquals("test-plugin", plugin.getPluginDefinition().prop_get("name"));
        assertNull(plugin.getBeanContainer(), "load 不得创建子容器（无 bean 实例化副作用）");
        assertNotNull(plugin.getLoadTime());
    }

    /**
     * jar 轨契约：无 xdef 载体（约定路径无定义文件）→ 空定义加载成功（不抛异常）。
     */
    @Test
    public void testLoadWithoutXdefCarrierLoadsEmptyDefinition() {
        AwarePlugin plugin = new AwarePlugin("/nop/plugin/not-exists.plugin.xml");
        plugin.load(Collections.emptyMap());

        assertEquals(PluginState.LOADED, plugin.getState(), "无载体 → 空定义加载成功");
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

    /**
     * jar 轨契约：activate 门控恒为空集——fixture 的 requires=model-provider 未加载、
     * if-property=test.plugin.enabled 全局未设置（两条腿均不满足）仍无条件激活达 ACTIVATED；
     * 激活回调跳过（无 activator 概念）；容器构建复用 doStart 路径（plugin.beans.xml 装配）。
     */
    @Test
    public void testActivateUnconditionalWithEmptyGate() {
        AwarePlugin plugin = new AwarePlugin(TEST_DEF_PATH);
        plugin.load(Collections.emptyMap());

        assertTrue(plugin.activate(), "门控恒空集 → 无条件激活返回 true");
        assertEquals(PluginState.ACTIVATED, plugin.getState());
        assertNotNull(plugin.getBeanContainer(), "子容器构建（doStart 路径逻辑复用）");
        assertTrue(plugin.getBeanContainer().containsBean("nopPluginCommand_hello"));
    }

    /**
     * activate 幂等：ACTIVATED 重复 activate 返回 true 不重建容器。
     */
    @Test
    public void testActivateIdempotent() {
        AwarePlugin plugin = new AwarePlugin(TEST_DEF_PATH);
        plugin.load(Collections.emptyMap());
        plugin.activate();
        Object container = plugin.getBeanContainer();
        assertTrue(plugin.activate(), "重复 activate 幂等 true");
        assertEquals(PluginState.ACTIVATED, plugin.getState());
        assertEquals(container, plugin.getBeanContainer(), "幂等不重建容器");
    }

    /**
     * unload 守卫：ACTIVATED 时 unload 抛明确异常（须先 deactivate）；deactivate 后放行。
     */
    @Test
    public void testUnloadGuardThrowsWhenActivated() {
        AwarePlugin plugin = new AwarePlugin(TEST_DEF_PATH);
        plugin.load(Collections.emptyMap());
        plugin.activate();

        NopException e = assertThrows(NopException.class, plugin::unload);
        assertEquals(ERR_PLUGIN_NOT_DEACTIVATED.getErrorCode(), e.getErrorCode());
        assertEquals(PluginState.ACTIVATED.name(), e.getParam("pluginState"));

        FutureHelper.syncGet(plugin.deactivate());
        assertEquals(PluginState.LOADED, plugin.getState(), "deactivate 回到 LOADED（定义保留）");
        plugin.unload();
        assertEquals(PluginState.UNLOADED, plugin.getState());
    }

    /**
     * invokeCommand 定义级路由：命令 bean 分发于本插件激活容器（hello 命令只在
     * plugin.beans.xml 声明，全局容器无此 bean——命中即证明本插件容器路由）；
     * 未激活显式抛 INACTIVE（不静默返回）。
     */
    @Test
    public void testAwareInvokeCommandDefinitionLevelRouting() {
        AwarePlugin plugin = new AwarePlugin(TEST_DEF_PATH);
        plugin.load(Collections.emptyMap());

        NopException inactive = assertThrows(NopException.class,
                () -> plugin.invokeCommand("hello", Map.of("who", "world"), null, null));
        assertEquals(ERR_PLUGIN_INACTIVE.getErrorCode(), inactive.getErrorCode());

        plugin.activate();
        Map<String, Object> result = plugin.invokeCommand("hello", Map.of("who", "world"), null, null);
        assertEquals("hello:world", result.get("result"), "命令分发于本插件激活容器");

        // deactivate 后命令快速失败
        FutureHelper.syncGet(plugin.deactivate());
        NopException closed = assertThrows(NopException.class,
                () -> plugin.invokeCommand("hello", Map.of(), null, null));
        assertEquals(ERR_PLUGIN_INACTIVE.getErrorCode(), closed.getErrorCode());
    }

    /**
     * start/stop 收敛（§7.1）：start = load + activate（门控空集 → activate 恒 true）；
     * stop = deactivate + unload。
     */
    @Test
    public void testStartStopConvergeToLoadActivateDeactivateUnload() {
        AwarePlugin plugin = new AwarePlugin(TEST_DEF_PATH);
        plugin.start("io.nop.plugin.test", "aware-plugin", "1.0", Collections.emptyMap());

        assertEquals(PluginState.ACTIVATED, plugin.getState(), "start = load + activate");
        assertNotNull(plugin.getPluginDefinition());
        assertNotNull(plugin.getBeanContainer());

        plugin.stop();
        assertEquals(PluginState.UNLOADED, plugin.getState(), "stop = deactivate + unload");
        assertNull(plugin.getPluginDefinition());
        assertNull(plugin.getBeanContainer(), "deactivate 回退子容器");
    }

    /**
     * 非 aware 兼容路径：activate/deactivate 显式抛 NopException
     * （不进入新状态机，不静默 no-op）；旧 start/stop 语义保持。
     */
    @Test
    public void testCompatPathKeepsStartStopSemantics() {
        LegacyPlugin plugin = new LegacyPlugin();
        assertEquals(PluginState.LOADED, plugin.getState(), "非 aware 保守值：可见即 LOADED");

        NopException activateErr = assertThrows(NopException.class, plugin::activate,
                "非 aware activate 显式失败（不静默 no-op）");
        assertEquals(PluginApiErrors.ERR_PLUGIN_LIFECYCLE_NOT_SUPPORTED.getErrorCode(),
                activateErr.getErrorCode());
        assertThrows(NopException.class, plugin::deactivate);

        Map<String, Object> config = Map.of("test.plugin.compat.value", "x");
        plugin.start("io.nop.plugin.test", "legacy-plugin", "1.0", config);

        assertTrue(plugin.isActive());
        assertFalse(plugin.isStopping());

        plugin.stop();
        assertFalse(plugin.isActive());
    }
}
