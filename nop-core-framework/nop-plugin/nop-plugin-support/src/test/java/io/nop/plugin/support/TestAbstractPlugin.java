package io.nop.plugin.support;

import io.nop.api.core.config.AppConfig;
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
 * {@link AbstractPlugin} 双路径行为测试（R1 jar 轨契约，plan-2026-08-22-2309-1 Phase 2；
 * R3 兼容路径与命令路由回归基线，plan-2026-08-22-2309-3 Phase 1/2）：
 * <ul>
 *     <li>load 容忍 xdef 缺失：约定路径无 *.plugin.xml → 空定义加载成功；有载体 → 解析持有
 *     （仅元数据，不驱动门控/activator）。</li>
 *     <li>activate 门控恒空集：无条件激活（fixture 的 requires/if-property 均不满足仍达
 *     ACTIVATED）；激活回调跳过（无 activator）；容器构建复用 doStart 路径逻辑。</li>
 *     <li>invokeCommand 定义级路由：命令 bean 分发于本插件激活容器；未激活显式抛 INACTIVE；
 *     回退链（插件容器 → 宿主回退 → default bean 兜底/终点显式异常）与非 aware 一致。</li>
 *     <li>unload 守卫：ACTIVATED 时 unload 抛明确异常；deactivate 后放行。</li>
 *     <li>start/stop 收敛 + 状态边界（R3 两轨统一规格）：start = load + activate（仅 UNLOADED
 *     补 load 步；已 LOADED 不重复 load 直接 activate；已 ACTIVATED 幂等 no-op 不重建容器）；
 *     stop = deactivate + unload（UNLOADED 态幂等 no-op）。</li>
 *     <li>兼容路径（非 aware 子类）保留旧 start/stop 语义（AppConfig.assignConfigValue 全局
 *     写入 + doStart 子容器创建）、getState 保守值 LOADED、命令路由经既有回退链。</li>
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

    /**
     * load 步计数插件：getPluginDefinitionPath 每次 load() 调用一次——
     * "已 LOADED/ACTIVATED 时 start 不重复 load"的可观测判别。
     */
    static class CountingPlugin extends AwarePlugin {
        int loadPathCount;

        CountingPlugin(String defPath) {
            super(defPath);
        }

        @Override
        protected String getPluginDefinitionPath() {
            loadPathCount++;
            return super.getPluginDefinitionPath();
        }
    }

    /**
     * 激活失败注入插件（No Silent No-Op 验证 seam）：buildBeanContainer 抛出——
     * start 路径必须显式传播且置 FAILED，不吞。
     */
    static class FailingPlugin extends AwarePlugin {
        FailingPlugin(String defPath) {
            super(defPath);
        }

        @Override
        protected void buildBeanContainer(boolean startAwarePath) {
            throw new IllegalStateException("boom-activate");
        }
    }

    /**
     * 无 beans 文件插件：激活成功但无子容器——命令经回退链解析（终点 miss 显式抛异常）。
     */
    static class NoBeansPlugin extends AwarePlugin {
        NoBeansPlugin(String defPath) {
            super(defPath);
        }

        @Override
        protected String getPluginBeansPath() {
            return "/nop/plugin/not-exists.beans.xml";
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
     * stop = deactivate + unload；stop 后 invokeCommand 抛 INACTIVE（定义级状态检查）。
     */
    @Test
    public void testStartStopConvergeToLoadActivateDeactivateUnload() {
        AwarePlugin plugin = new AwarePlugin(TEST_DEF_PATH);
        plugin.start("io.nop.plugin.test", "aware-plugin", "1.0", Collections.emptyMap());

        assertEquals(PluginState.ACTIVATED, plugin.getState(), "start = load + activate");
        assertNotNull(plugin.getPluginDefinition());
        assertNotNull(plugin.getBeanContainer());
        Map<String, Object> result = plugin.invokeCommand("hello", Map.of("who", "world"), null, null);
        assertEquals("hello:world", result.get("result"), "start 后命令命中容器命令 bean");

        plugin.stop();
        assertEquals(PluginState.UNLOADED, plugin.getState(), "stop = deactivate + unload");
        assertNull(plugin.getPluginDefinition());
        assertNull(plugin.getBeanContainer(), "deactivate 回退子容器");
        NopException inactive = assertThrows(NopException.class,
                () -> plugin.invokeCommand("hello", Map.of("who", "world"), null, null));
        assertEquals(ERR_PLUGIN_INACTIVE.getErrorCode(), inactive.getErrorCode(), "stop 后命令快速失败");
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

    /**
     * 非 aware 兼容路径 W2-W4 基线（R3 钉死）：start 的 config 经 AppConfig.assignConfigValue
     * 全局写入（旧语义可观测）、doStart 子容器路径（build 不 start——改造前行为）、
     * invokeCommand 维持既有 getCommandBean 回退链行为——链上可见名经插件容器 getBean 显式
     * 抛 container-not-started（兼容容器 build 不 start，改造前等价），未知名落入
     * default bean 兜底分支（宿主无 default → 链终点显式抛异常）——存量第三方插件零回归。
     */
    @Test
    public void testCompatPathW2W4BaselineConfigAndContainer() {
        String key = "test.plugin.compat.baseline";
        Object prior = AppConfig.getConfigProvider().getConfigValue(key, null);
        try {
            LegacyPlugin plugin = new LegacyPlugin();
            plugin.start("io.nop.plugin.test", "legacy-plugin", "1.0", Map.of(key, "w24"));

            // 旧语义 1：config 经 AppConfig.assignConfigValue 全局写入
            assertEquals("w24", AppConfig.getConfigProvider().getConfigValue(key, null),
                    "兼容路径 config 必须写入全局 AppConfig（改造前行为）");
            // 旧语义 2：doStart 子容器路径（build 不 start，/nop/plugin.beans.xml 装配）
            assertNotNull(plugin.getBeanContainer(), "兼容路径 doStart 创建子容器");
            assertTrue(plugin.getBeanContainer().containsBean("nopPluginCommand_hello"));

            // 旧语义 3（getCommandBean 链现状）：链上可见名（hello 在插件容器本地）经
            // beanContainer.getBean 显式抛 container-not-started（兼容容器 build 不 start——
            // 改造前等价行为，非 aware 路由代码不变）
            NopException notStarted = assertThrows(NopException.class,
                    () -> plugin.invokeCommand("hello", Map.of("who", "world"), null, null));
            assertTrue(notStarted.getErrorCode().contains("container-not-started"),
                    "链上可见名经插件容器 getBean 显式失败（不静默）");

            // 旧语义 4（default bean 兜底分支）：未知名 → 静态宿主回退 miss → default 兜底
            // getBean（宿主无 nopPluginCommand_default）→ 链终点显式抛异常
            assertThrows(RuntimeException.class,
                    () -> plugin.invokeCommand("no-such-command", Map.of(), null, null),
                    "回退链终点 miss 必须显式抛异常（不静默返回）");

            plugin.stop();
            assertFalse(plugin.isActive());
        } finally {
            AppConfig.getConfigProvider().assignConfigValue(key, prior);
        }
    }

    /**
     * start/stop 状态边界（jar 轨 aware，两轨统一规格 R3）：
     * <ul>
     *     <li>仅 UNLOADED 补 load 步：fresh start 后 load 计数 = 1。</li>
     *     <li>已 LOADED 不重复 load 直接 activate：load() 后 start，load 计数不变。</li>
     *     <li>已 ACTIVATED 时 start 幂等 no-op：容器不重建（同一实例）——重复 load+activate
     *     会泄漏旧容器，此断言是容器泄漏回归的钉死。</li>
     *     <li>UNLOADED 态 stop 幂等 no-op：不抛异常、状态保持 UNLOADED。</li>
     * </ul>
     */
    @Test
    public void testStartStopStateBoundariesJarTrack() {
        // UNLOADED 态 stop 幂等 no-op
        CountingPlugin fresh = new CountingPlugin(TEST_DEF_PATH);
        fresh.stop();
        assertEquals(PluginState.UNLOADED, fresh.getState(), "UNLOADED 态 stop 幂等 no-op");

        // fresh start：补 load 步（计数 1）→ ACTIVATED
        CountingPlugin plugin = new CountingPlugin(TEST_DEF_PATH);
        plugin.start("io.nop.plugin.test", "boundary-plugin", "1.0", Collections.emptyMap());
        assertEquals(PluginState.ACTIVATED, plugin.getState());
        assertEquals(1, plugin.loadPathCount, "UNLOADED → start 补一次 load");
        Object container = plugin.getBeanContainer();
        assertNotNull(container);

        // 已 ACTIVATED：start 幂等 no-op（不重复 load、不重建容器）
        plugin.start("io.nop.plugin.test", "boundary-plugin", "1.0", Collections.emptyMap());
        assertEquals(PluginState.ACTIVATED, plugin.getState());
        assertEquals(1, plugin.loadPathCount, "ACTIVATED 重复 start 不重复 load");
        assertEquals(container, plugin.getBeanContainer(), "ACTIVATED 重复 start 幂等不重建容器（无泄漏）");

        // 已 LOADED：start 不重复 load 直接 activate（显式 load 计入计数：1 → 2）
        plugin.stop();
        assertEquals(PluginState.UNLOADED, plugin.getState());
        plugin.load(Collections.emptyMap());
        assertEquals(2, plugin.loadPathCount, "显式 load 计入计数");
        plugin.start("io.nop.plugin.test", "boundary-plugin", "1.0", Collections.emptyMap());
        assertEquals(PluginState.ACTIVATED, plugin.getState(), "LOADED → start 直接 activate");
        assertEquals(2, plugin.loadPathCount, "LOADED 时 start 不重复 load");
    }

    /**
     * 无静默跳过（jar 轨）：activate 失败在 start 路径显式传播（FAILED 态 + 异常 +
     * lastActivationError 可读），不吞。
     */
    @Test
    public void testStartPropagatesActivationFailureExplicitly() {
        FailingPlugin plugin = new FailingPlugin(TEST_DEF_PATH);

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> plugin.start("io.nop.plugin.test", "failing-plugin", "1.0", Collections.emptyMap()),
                "start 路径必须显式传播 activate 失败");
        assertEquals("boom-activate", e.getMessage());
        assertEquals(PluginState.FAILED, plugin.getState(), "激活失败置 FAILED");
        assertNotNull(plugin.getLastActivationError(), "lastActivationError 可读（错误可观测）");
        assertEquals("boom-activate", plugin.getLastActivationError().getMessage());

        // FAILED 态可 unload（错误清理已完成）
        plugin.unload();
        assertEquals(PluginState.UNLOADED, plugin.getState());
    }

    /**
     * 回退链裁定（jar 轨 aware，R3 Phase 2）：插件激活容器未命中指定命令 bean 时按裁定回退链
     * 行为可观察——宿主回退命中（nopPluginCommand_host 只在宿主容器；aware 容器已 start，
     * containsBean/getBean 经 parent 链解析宿主 bean）。非 aware 兼容路径同链但容器 build 不
     * start（改造前行为），链上可见名显式抛 container-not-started——链结构一致的差异仅在
     * 容器启动状态（aware start = load + activate 子容器启动；非 aware 保持旧语义）。
     */
    @Test
    public void testAwareCommandHostFallbackObservable() {
        AwarePlugin plugin = new AwarePlugin(TEST_DEF_PATH);
        plugin.start("io.nop.plugin.test", "aware-plugin", "1.0", Collections.emptyMap());
        assertEquals(PluginState.ACTIVATED, plugin.getState());

        Map<String, Object> result = plugin.invokeCommand("host", Map.of("who", "world"), null, null);
        assertEquals("host:world", result.get("result"), "aware 插件容器未命中 → 宿主回退命中");

        // 非 aware 同一命令：getCommandBean 链结构一致（插件容器优先），但兼容容器 build 不
        // start → 链上可见名（host 经 parent 链）显式抛 container-not-started（改造前等价）
        LegacyPlugin legacy = new LegacyPlugin();
        legacy.start("io.nop.plugin.test", "legacy-plugin", "1.0", Collections.emptyMap());
        NopException notStarted = assertThrows(NopException.class,
                () -> legacy.invokeCommand("host", Map.of("who", "world"), null, null));
        assertTrue(notStarted.getErrorCode().contains("container-not-started"),
                "非 aware 兼容容器 build 不 start（改造前行为）");
        legacy.stop();
    }

    /**
     * 非 aware 回退链终点（default bean 兜底分支，R3 Phase 2）：命令在插件容器与宿主均未命中、
     * 宿主无 nopPluginCommand_default（本 JVM fixture 故意不注册）→ 经 getBean 显式抛异常，
     * 不静默返回。
     */
    @Test
    public void testCommandChainEndThrowsExplicitWhenNoDefaultBean() {
        LegacyPlugin plugin = new LegacyPlugin();
        plugin.start("io.nop.plugin.test", "legacy-plugin", "1.0", Collections.emptyMap());

        assertThrows(RuntimeException.class,
                () -> plugin.invokeCommand("no-such-command", Map.of(), null, null),
                "回退链终点 miss 必须显式抛异常（不静默返回）");
        plugin.stop();
    }

    /**
     * 激活成功但插件无容器（无 beans 文件，jar 轨 aware）：命令经既有回退链解析——宿主回退
     * 可命中；终点 miss 最终抛明确异常（不静默返回）。
     */
    @Test
    public void testAwareNoContainerCommandResolvesViaFallbackChain() {
        NoBeansPlugin plugin = new NoBeansPlugin(TEST_DEF_PATH);
        plugin.start("io.nop.plugin.test", "no-beans-plugin", "1.0", Collections.emptyMap());

        assertEquals(PluginState.ACTIVATED, plugin.getState(), "无 beans 文件激活仍成功");
        assertNull(plugin.getBeanContainer(), "无子容器（beans 文件缺失）");

        // 宿主回退命中
        Map<String, Object> result = plugin.invokeCommand("host", Map.of("who", "chain"), null, null);
        assertEquals("host:chain", result.get("result"), "无容器 → 回退链经宿主回退命中");

        // 终点 miss（hello 只在 /nop/plugin.beans.xml，本插件无容器；宿主无 hello 也无 default）→ 显式异常
        assertThrows(RuntimeException.class,
                () -> plugin.invokeCommand("hello", Map.of("who", "world"), null, null),
                "回退链终点 miss 必须显式抛异常（不静默返回）");
    }
}
