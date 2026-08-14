package io.nop.plugin.manager;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.plugin.api.Disposable;
import io.nop.plugin.api.IPlugin;
import io.nop.plugin.api.IPluginInstance;
import io.nop.plugin.api.IPluginScope;
import io.nop.plugin.api.InstanceState;
import io.nop.plugin.manager.impl.PluginManagerImpl;
import io.nop.plugin.test.AgentInstanceRecorder;
import io.nop.plugin.test.BashTool;
import io.nop.plugin.test.ConfigReaderBean;
import io.nop.plugin.test.ITool;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static io.nop.plugin.api.PluginApiErrors.ERR_PLUGIN_INACTIVE;
import static io.nop.plugin.manager.PluginManagerConstants.DEFAULT_INSTANCE_KEY;
import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_ACTIVATION_FAILED;
import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_DEFINITION_NOT_LOADED;
import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_INSTANCES_NOT_EMPTY;
import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_INSTANCE_EXISTS;
import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_INSTANCE_NOT_SUPPORTED;
import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_MULTIPLE_SERVICE_CANDIDATES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W3 实例级生命周期 + effect + activator（plan-2026-08-14-1720-3 Phase 5）：
 * <ul>
 *     <li>quiescence：effect 注册（含 activator 返回值）→ deactivate/destroy 后 effects() 清空，
 *     LIFO 回退顺序（return-disposed → effect-disposed → bean-destroyed）可观测。</li>
 *     <li>多实例隔离：同定义 agent-1/agent-2 独立 scope/effect/配置域。</li>
 *     <li>生命周期往返：ACTIVATED→DEACTIVATED→ACTIVATED（deactivate 实例对象保留、activate 重跑
 *     activator）；destroy 从 registry 移除。</li>
 *     <li>异常路径：重复 key / unload 守卫 / 未加载定义 / jar 轨边界 / 激活失败（错误带 key 参数）。</li>
 *     <li>activator 参数传递：scope + 合并视图 config；返回值 disposer 自动注册。</li>
 *     <li>配置域：实例覆盖全局、仅实例键（非全局回落）、全局无污染；P2-C 热应用/缓存。</li>
 *     <li>并发重复 activate 幂等（in-flight 单飞）。</li>
 *     <li>per-instance 命令路由（invokeCommand 到本实例子容器）。</li>
 * </ul>
 */
public class TestPluginInstanceLifecycle {

    private static final String PLUGIN_ID = "/nop/plugin/test/agent-instance.plugin.xml";
    private static final String MULTI_CANDIDATE_ID = "/nop/plugin/test/multi-candidate.plugin.xml";
    private static final String FAIL_ID = "/nop/plugin/test/activate-fail.plugin.xml";
    private static final String JAR_COORDS = "io.nop.plugin.test:mock-plugin:1.0.0";

    private PluginManagerImpl manager;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
        // 全局配置写入（测试基线，非 plugin 框架行为）：实例配置域"全局回落 / 实例覆盖全局"测试前提
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
    }

    /**
     * 端到端链路：manager createInstance → 子容器创建 → activator 调用 → scope 取服务 →
     * deactivate → quiescence（LIFO 回退）→ destroy 移除。
     */
    @Test
    public void testEndToEndChain() {
        manager.loadPlugin(PLUGIN_ID);

        IPluginInstance inst = manager.createInstance(PLUGIN_ID, "agent-1", Map.of("agent.timeout", "10"), null);

        // createInstance 即激活
        assertEquals(InstanceState.ACTIVATED, inst.getState());
        assertEquals("agent-1", inst.getInstanceKey());
        assertNull(inst.getParent(), "W3 parent 恒 null（W6 落地层级）");

        // activator 调用链：scope + config 参数传入；scope.getService 取 primary bean 成功
        assertEquals(1, AgentInstanceRecorder.activatedCount);
        assertEquals("10", AgentInstanceRecorder.lastConfig.get("agent.timeout"));
        assertInstanceOf(BashTool.class, AgentInstanceRecorder.serviceTool, "primary 优先");
        assertEquals(2, AgentInstanceRecorder.serviceCount);

        // 返回值 disposer 自动注册 + scope.effect 注册 → 2 个 effect
        IPluginScope scope = inst.getScope();
        assertNotNull(scope);
        assertEquals(2, scope.effects().size());

        // deactivate → quiescence：effects 清空，LIFO 顺序 + bean destroy 在 effect 回退之后
        inst.deactivate();
        assertEquals(InstanceState.DEACTIVATED, inst.getState());
        assertNull(inst.getScope());
        assertEquals(0, scope.effects().size());
        assertEquals(List.of("return-disposed", "effect-disposed", "bean-destroyed"),
                AgentInstanceRecorder.events());

        // DEACTIVATED 实例 getConfig 仍可读
        assertEquals("10", inst.getConfig().get("agent.timeout"));

        // DEACTIVATED 实例 getService 快速失败（W3 无代理，显式异常）
        NopException inactive = assertThrows(NopException.class, () -> inst.getService(ITool.class));
        assertEquals(ERR_PLUGIN_INACTIVE.getErrorCode(), inactive.getErrorCode());

        // destroy → 从 registry 移除
        inst.destroy();
        assertNull(manager.getInstance(PLUGIN_ID, "agent-1"));
    }

    @Test
    public void testQuiescenceAndScopeContract() {
        manager.loadPlugin(PLUGIN_ID);
        IPluginInstance inst = manager.createInstance(PLUGIN_ID, "q", Map.of(), null);

        IPluginScope scope = inst.getScope();
        assertEquals(2, scope.effects().size());

        // 可移除句柄：手动移除并回退
        Disposable handle = scope.effect(() -> AgentInstanceRecorder.event("manual-disposed"));
        assertEquals(3, scope.effects().size());
        handle.dispose();
        assertEquals(2, scope.effects().size());

        // close 幂等 + close 后注册抛异常
        inst.deactivate();
        scope.close();
        assertThrows(IllegalStateException.class, () -> scope.effect(() -> {
        }), "close 后 effect 注册必须抛异常");
    }

    @Test
    public void testMultiInstanceIsolation() {
        manager.loadPlugin(PLUGIN_ID);

        IPluginInstance i1 = manager.createInstance(PLUGIN_ID, "agent-1", Map.of("agent.timeout", "10"), null);
        IPluginInstance i2 = manager.createInstance(PLUGIN_ID, "agent-2", Map.of("agent.timeout", "20"), null);

        // 各自独立 scope / 配置域
        assertNotSame(i1.getScope(), i2.getScope());
        assertEquals("10", i1.getConfig().get("agent.timeout"));
        assertEquals("20", i2.getConfig().get("agent.timeout"));

        // 实例内 bean 各自解析实例配置域
        assertEquals("10", i1.getService(ConfigReaderBean.class).getTimeout());
        assertEquals("20", i2.getService(ConfigReaderBean.class).getTimeout());

        // 一个实例的 effect 变更不影响另一个
        i1.getScope().effect(() -> AgentInstanceRecorder.event("i1-extra"));
        assertEquals(3, i1.getScope().effects().size());
        assertEquals(2, i2.getScope().effects().size());

        // 一个实例 deactivate 不影响另一个
        i1.deactivate();
        assertEquals(InstanceState.DEACTIVATED, i1.getState());
        assertEquals(InstanceState.ACTIVATED, i2.getState());
        assertEquals(2, i2.getScope().effects().size(), "i2 的 effect 不受 i1 deactivate 影响");
        assertEquals("20", i2.getService(ConfigReaderBean.class).getTimeout());
    }

    @Test
    public void testLifecycleRoundtripAndDestroy() {
        manager.loadPlugin(PLUGIN_ID);
        IPluginInstance inst = manager.createInstance(PLUGIN_ID, "rt", Map.of("agent.timeout", "10"), null);
        assertEquals(1, AgentInstanceRecorder.activatedCount);

        inst.deactivate();
        assertNull(inst.getScope());
        assertEquals(InstanceState.DEACTIVATED, inst.getState());

        // 重新 activate：实例对象保留、activator 重跑、effect 重新注册
        inst.activate();
        assertEquals(InstanceState.ACTIVATED, inst.getState());
        assertEquals(2, AgentInstanceRecorder.activatedCount, "deactivate 后 activate 必须重跑 activator");
        assertNotNull(inst.getScope());
        assertEquals(2, inst.getScope().effects().size(), "effect 需重新注册");

        inst.destroy();
        assertNull(manager.getInstance(PLUGIN_ID, "rt"));
        assertTrue(manager.getInstances(PLUGIN_ID).isEmpty());
    }

    @Test
    public void testDuplicateKeyThrowsExplicitError() {
        manager.loadPlugin(PLUGIN_ID);
        manager.createInstance(PLUGIN_ID, "dup", Map.of(), null);

        NopException e = assertThrows(NopException.class,
                () -> manager.createInstance(PLUGIN_ID, "dup", Map.of(), null));
        assertEquals(ERR_PLUGIN_INSTANCE_EXISTS.getErrorCode(), e.getErrorCode());
        assertEquals("dup", e.getParam("instanceKey"));
    }

    @Test
    public void testUnloadGuardBlocksWhenInstancesExist() {
        IPlugin plugin = manager.loadPlugin(PLUGIN_ID);
        manager.createInstance(PLUGIN_ID, "guard", Map.of(), null);

        NopException e = assertThrows(NopException.class, () -> manager.unloadPlugin(PLUGIN_ID));
        assertEquals(ERR_PLUGIN_INSTANCES_NOT_EMPTY.getErrorCode(), e.getErrorCode());
        assertTrue(e.getParam("instanceKeys").toString().contains("guard"));

        // registry 清空后正常 unload（W2 行为保留）
        manager.destroyInstance(PLUGIN_ID, "guard");
        manager.unloadPlugin(PLUGIN_ID);
        assertEquals(io.nop.plugin.api.PluginState.UNLOADED, plugin.getState(), "unload 后回到 UNLOADED");
    }

    @Test
    public void testCreateInstanceOnNotLoadedDefinitionFails() {
        NopException e = assertThrows(NopException.class,
                () -> manager.createInstance("/nop/plugin/test/never-loaded.plugin.xml", "k", Map.of(), null));
        assertEquals(ERR_PLUGIN_DEFINITION_NOT_LOADED.getErrorCode(), e.getErrorCode());
        assertEquals("/nop/plugin/test/never-loaded.plugin.xml", e.getParam("pluginId"));
    }

    @Test
    public void testCreateInstanceOnJarTrackFailsExplicitly() throws IOException {
        URL jar = buildPluginJar("io.nop.plugin.jarplugin.MockPlugin");
        manager.setResourceResolver(coords -> List.of(jar));
        manager.setPluginConfigProvider(coords -> Map.of());
        manager.loadPlugin(JAR_COORDS);

        NopException e = assertThrows(NopException.class,
                () -> manager.createInstance(JAR_COORDS, "k", Map.of(), null));
        assertEquals(ERR_PLUGIN_INSTANCE_NOT_SUPPORTED.getErrorCode(), e.getErrorCode());
        assertEquals("k", e.getParam("instanceKey"));
    }

    @Test
    public void testActivatorFailureRollsBackToDeactivated() {
        manager.loadPlugin(FAIL_ID);

        NopException e = assertThrows(NopException.class,
                () -> manager.createInstance(FAIL_ID, "f1", Map.of(), null));
        assertEquals(ERR_PLUGIN_ACTIVATION_FAILED.getErrorCode(), e.getErrorCode());
        assertEquals("f1", e.getParam("instanceKey"));
        assertNotNull(e.getCause());
        assertTrue(e.getCause().getMessage().contains("boom"), "原始错误保留为 cause");

        // 实例回退 DEACTIVATED（不残留半激活实例），可 destroy 清理
        IPluginInstance inst = manager.getInstance(FAIL_ID, "f1");
        assertNotNull(inst);
        assertEquals(InstanceState.DEACTIVATED, inst.getState());
        inst.destroy();
        assertNull(manager.getInstance(FAIL_ID, "f1"));
    }

    @Test
    public void testConfigDomainMergedView() {
        manager.loadPlugin(PLUGIN_ID);
        IPluginInstance inst = manager.createInstance(PLUGIN_ID, "cfg",
                Map.of("agent.timeout", "30", "agent.only-instance", "x"), null);

        ConfigReaderBean reader = inst.getService(ConfigReaderBean.class);
        assertEquals("30", reader.getTimeout(), "实例配置覆盖全局默认");
        assertEquals("x", reader.getOnlyInstance(), "仅实例有而全局无的键命中合并视图（非全局回落）");
        assertEquals("global-default", reader.getGlobalValue(), "无实例值的键回落全局 provider");

        // AppConfig 全局无污染：实例键（agent.only-instance / agent.mode）从未被写入全局——
        // agent.timeout / test.plugin.global.var 为测试基线的显式全局写入（非 framework 行为）
        assertNull(AppConfig.getConfigProvider().getConfigValue("agent.only-instance", null));
        assertEquals("30", inst.getConfig().get("agent.timeout"));
    }

    @Test
    public void testUpdateConfigHotApplyAndDeactivatedCaching() {
        IPlugin plugin = manager.loadPlugin(PLUGIN_ID);

        // 定义默认 → 进实例合并视图（实例配置不覆盖 agent.mode）
        plugin.updateConfig(Map.of("agent.mode", "dev"));
        IPluginInstance inst = manager.createInstance(PLUGIN_ID, "hot", Map.of("agent.timeout", "30"), null);
        ConfigReaderBean reader = inst.getService(ConfigReaderBean.class);
        assertEquals("dev", reader.getMode());
        assertEquals("30", reader.getTimeout());

        // ACTIVATED 热应用：合并视图刷新 + 经委托 provider 触发变更通知（bean 属性真实重绑定，非仅改快照）
        plugin.updateConfig(Map.of("agent.mode", "prod"));
        assertEquals("prod", reader.getMode(), "热应用必须触发响应式重绑定");
        assertEquals("prod", inst.getConfig().get("agent.mode"));
        assertEquals("30", inst.getConfig().get("agent.timeout"), "实例配置仍覆盖定义默认");

        // DEACTIVATED 缓存：updateConfig 不应用；下次 activate 应用
        inst.deactivate();
        plugin.updateConfig(Map.of("agent.mode", "stage"));
        assertEquals("prod", inst.getConfig().get("agent.mode"), "DEACTIVATED 缓存配置、下次 activate 应用");
        inst.activate();
        assertEquals("stage", inst.getConfig().get("agent.mode"));
        assertEquals("stage", inst.getService(ConfigReaderBean.class).getMode());

        // 全局无污染（agent.mode 只经实例配置域/定义默认流转，从未写入全局）
        assertNull(AppConfig.getConfigProvider().getConfigValue("agent.mode", null));
    }

    @Test
    public void testConcurrentRepeatedActivateIsIdempotent() throws Exception {
        manager.loadPlugin(PLUGIN_ID);
        IPluginInstance inst = manager.createInstance(PLUGIN_ID, "conc", Map.of(), null);
        inst.deactivate();
        AgentInstanceRecorder.reset();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(2);
            for (int i = 0; i < 2; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        inst.activate();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            done.await();

            assertEquals(1, AgentInstanceRecorder.activatedCount, "并发重复 activate 必须单飞（不重跑 activator）");
            assertEquals(InstanceState.ACTIVATED, inst.getState());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void testGetServiceMultipleCandidatesThrowsExplicitError() {
        manager.loadPlugin(MULTI_CANDIDATE_ID);
        IPluginInstance inst = manager.createInstance(MULTI_CANDIDATE_ID, "mc", Map.of(), null);

        // 无 activator 声明 → 激活成功但无 effect 注册
        assertEquals(InstanceState.ACTIVATED, inst.getState());
        assertEquals(0, inst.getScope().effects().size());

        // 多候选且无 primary → 明确异常（不静默返回集合）
        NopException e = assertThrows(NopException.class, () -> inst.getService(ITool.class));
        assertEquals(ERR_PLUGIN_MULTIPLE_SERVICE_CANDIDATES.getErrorCode(), e.getErrorCode());

        // 集合版返回全部实现
        assertEquals(2, inst.getServices(ITool.class).size());
    }

    @Test
    public void testInvokeCommandRoutesToInstanceContainer() {
        manager.loadPlugin(PLUGIN_ID);
        IPluginInstance inst = manager.createInstance(PLUGIN_ID, "cmd", Map.of(), null);

        Map<String, Object> result = inst.invokeCommand("hello", Map.of("who", "world"), null, null);
        assertEquals("hello:world", result.get("result"));
        assertTrue(AgentInstanceRecorder.events().contains("command:hello"));

        // DEACTIVATED 实例命令快速失败
        inst.deactivate();
        NopException e = assertThrows(NopException.class,
                () -> inst.invokeCommand("hello", Map.of(), null, null));
        assertEquals(ERR_PLUGIN_INACTIVE.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testStartConvergesToDefaultKeyInstance() {
        IPlugin plugin = manager.loadPlugin(PLUGIN_ID);

        // §7.1 收敛：start = load + createInstance(默认 key)——测试覆盖默认 key 实例化
        plugin.start("g", "a", "1.0", Map.of("agent.timeout", "5"));
        IPluginInstance inst = manager.getInstance(PLUGIN_ID, DEFAULT_INSTANCE_KEY);
        assertNotNull(inst);
        assertEquals(InstanceState.ACTIVATED, inst.getState());
        assertEquals("5", inst.getConfig().get("agent.timeout"));

        // stop = destroyInstance + unload
        plugin.stop();
        assertNull(manager.getInstance(PLUGIN_ID, DEFAULT_INSTANCE_KEY));
        assertEquals(io.nop.plugin.api.PluginState.UNLOADED, plugin.getState());
    }

    private static URL buildPluginJar(String className) throws IOException {
        File dir = new File("target/plugin-test/").getAbsoluteFile();
        dir.mkdirs();
        File jarFile = File.createTempFile("plugin-test-", ".jar", dir);
        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(jarFile))) {
            out.putNextEntry(new JarEntry("nop/plugin.json"));
            String json = "{\"pluginClassName\":\"" + className
                    + "\",\"importPackages\":[\"io.nop.plugin.test\"]}";
            out.write(json.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();

            String classPath = className.replace('.', '/') + ".class";
            try (InputStream in = TestPluginInstanceLifecycle.class.getClassLoader().getResourceAsStream(classPath)) {
                if (in == null) {
                    throw new IllegalStateException("class not found in test classpath: " + classPath);
                }
                out.putNextEntry(new JarEntry(classPath));
                in.transferTo(out);
                out.closeEntry();
            }
        }
        return jarFile.toURI().toURL();
    }
}
