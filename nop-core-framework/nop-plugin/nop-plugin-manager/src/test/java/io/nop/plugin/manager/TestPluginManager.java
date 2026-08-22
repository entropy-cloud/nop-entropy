package io.nop.plugin.manager;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.plugin.api.IPlugin;
import io.nop.plugin.api.PluginState;
import io.nop.plugin.manager.impl.PluginManagerImpl;
import io.nop.plugin.manager.impl.VfsPluginDefinition;
import io.nop.plugin.test.MockPluginRecorder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static io.nop.plugin.api.PluginApiErrors.ERR_PLUGIN_DEFINITION_NOT_FOUND;
import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_ACTIVATOR_NOT_FOUND;
import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_DEFINITION_NOT_LOADED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PluginManagerImpl 双轨路由与定义级状态测试（W2 Phase 3 + R1 单激活收敛）：
 * <ul>
 *     <li>兼容路径回归（非 aware jar 插件）：loadPlugin 调 start、unloadPlugin 调 stop、
 *     start 失败 → stop → rethrow 且 entry 不入 map、getState 保守值 LOADED。</li>
 *     <li>aware 定义级状态（VFS 轨）：无门控定义 loadPlugin 后经 reconcile 自动激活；
 *     门控开但 activator 缺失 → 自动激活失败置 FAILED（错误可读）；显式 activatePlugin 上抛；
 *     重复 loadPlugin 幂等；unload 回 UNLOADED。</li>
 *     <li>双轨路由：VFS 路径 id 真实经 DslModelParser + plugin.xdef 解析（测试资源）；
 *     Maven 坐标 id 经 resolver + PluginClassLoader + jar 内 /nop/plugin.json（非 META-INF/services）。</li>
 *     <li>非法 id / 无法解析路径抛明确异常（错误码带 id 参数）。</li>
 * </ul>
 */
public class TestPluginManager {

    private static final String VFS_PLUGIN_ID = "/nop/plugin/test/agent-tools.plugin.xml";
    private static final String MODEL_PROVIDER_ID = "/nop/plugin/test/model-provider.plugin.xml";
    private static final String INVALID_PLUGIN_ID = "/nop/plugin/test/invalid.plugin.xml";
    private static final String NOT_EXISTS_PLUGIN_ID = "/nop/plugin/test/not-exists.plugin.xml";
    private static final String JAR_COORDS = "io.nop.plugin.test:mock-plugin:1.0.0";
    private static final String GLOBAL_TOOLS_ENABLED = "agent.tools.enabled";

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private static PluginManagerImpl newManager(URL jarUrl) {
        PluginManagerImpl manager = new PluginManagerImpl();
        manager.setResourceResolver(coords -> List.of(jarUrl));
        manager.setPluginConfigProvider(coords -> Collections.emptyMap());
        return manager;
    }

    private static PluginManagerImpl newManager() {
        return newManager(null);
    }

    /**
     * 构建测试 uber jar：内含 /nop/plugin.json（声明 pluginClassName + importPackages 路由
     * 宿主 classpath 的 io.nop.plugin.test 包）+ 指定类的 .class 字节（从测试 classpath 拷贝）。
     */
    static URL buildPluginJar(String... classNames) throws IOException {
        File dir = new File("target/plugin-test/").getAbsoluteFile();
        dir.mkdirs();
        File jarFile = File.createTempFile("plugin-test-", ".jar", dir);
        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(jarFile))) {
            out.putNextEntry(new JarEntry("nop/plugin.json"));
            String json = "{\"pluginClassName\":\"" + classNames[0]
                    + "\",\"importPackages\":[\"io.nop.plugin.test\"]}";
            out.write(json.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();

            for (String className : classNames) {
                String classPath = className.replace('.', '/') + ".class";
                try (InputStream in = TestPluginManager.class.getClassLoader().getResourceAsStream(classPath)) {
                    if (in == null) {
                        throw new IllegalStateException("class not found in test classpath: " + classPath);
                    }
                    out.putNextEntry(new JarEntry(classPath));
                    in.transferTo(out);
                    out.closeEntry();
                }
            }
        }
        return jarFile.toURI().toURL();
    }

    @Test
    public void testCompatPathCallsStartAndStop() throws IOException {
        MockPluginRecorder.reset();
        URL jar = buildPluginJar("io.nop.plugin.jarplugin.MockPlugin");
        PluginManagerImpl manager = newManager(jar);

        IPlugin plugin = manager.loadPlugin(JAR_COORDS);

        assertTrue(MockPluginRecorder.started, "非 aware 兼容路径必须调用 start");
        assertEquals("io.nop.plugin.test", MockPluginRecorder.groupId);
        assertEquals("mock-plugin", MockPluginRecorder.artifactId);
        assertEquals("1.0.0", MockPluginRecorder.version);
        assertEquals(PluginState.LOADED, plugin.getState(), "非 aware 保守值：可见即 LOADED");

        manager.unloadPlugin(JAR_COORDS);
        assertTrue(MockPluginRecorder.stopped, "unloadPlugin 必须调用 stop");
        assertFalse(manager.getLoadedPlugins().contains(plugin), "unload 后从 getLoadedPlugins() 移除");
    }

    @Test
    public void testCompatStartFailureCleansUpAndDoesNotKeepEntry() throws IOException {
        MockPluginRecorder.reset();
        URL jar = buildPluginJar("io.nop.plugin.jarplugin.MockPluginFail",
                "io.nop.plugin.jarplugin.MockPlugin");
        PluginManagerImpl manager = newManager(jar);

        assertThrows(RuntimeException.class, () -> manager.loadPlugin(JAR_COORDS));

        assertTrue(MockPluginRecorder.stopped, "start 失败后必须调用 stop 清理");
        assertTrue(manager.getLoadedPlugins().isEmpty(), "start 失败不得留下半加载 entry");
    }

    @Test
    public void testVfsTrackAwareDefinitionState() {
        PluginManagerImpl manager = newManager();

        // agent-tools 的 coeffect 有两条腿——requires=model-provider + 定义级 if-property 读全局
        // agent.tools.enabled。内联开门控：model-provider 加载（自动激活）+ 显式赋值全局
        // agent.tools.enabled=true（捕获原值，finally 恢复防跨类污染）。
        Object priorToolsEnabled = AppConfig.getConfigProvider().getConfigValue(GLOBAL_TOOLS_ENABLED, null);
        try {
            AppConfig.getConfigProvider().assignConfigValue(GLOBAL_TOOLS_ENABLED, "true");
            manager.loadPlugin(MODEL_PROVIDER_ID);
            assertEquals(PluginState.ACTIVATED, manager.getPlugin(MODEL_PROVIDER_ID).getState(),
                    "无门控定义：loadPlugin 后 reconcile 自动激活");

            IPlugin plugin = manager.loadPlugin(VFS_PLUGIN_ID);

            assertTrue(plugin.isStateMachineAware());
            assertNull(plugin.getPluginGroupId(), "VFS 轨无 Maven 坐标");
            assertNull(plugin.getPluginArtifactId());
            assertNull(plugin.getPluginVersion());
            assertNotNull(plugin.getLoadTime(), "load 时记录真实 loadTime");
            assertNotNull(plugin.getLastChangeTime());

            // 门控开但 agent-tools 声明的 activator="agentToolsActivator" 未在 beans 中声明 →
            // loadPlugin 的 reconcile 自动激活失败 → 置 FAILED（错误可读，不上抛）
            assertEquals(PluginState.FAILED, plugin.getState(),
                    "自动激活失败 → 置 FAILED（reconcile 捕获记录）");
            VfsPluginDefinition def = (VfsPluginDefinition) plugin;
            assertNotNull(def.getLastActivationError());

            // 显式 activatePlugin：异常显式上抛（错误码完整、带 pluginId 参数）
            NopException e = assertThrows(NopException.class, () -> manager.activatePlugin(VFS_PLUGIN_ID));
            assertEquals(ERR_PLUGIN_ACTIVATOR_NOT_FOUND.getErrorCode(), e.getErrorCode());
            assertEquals(VFS_PLUGIN_ID, e.getParam("pluginId"));

            // 重复 loadPlugin 幂等返回同一定义
            assertSame(plugin, manager.loadPlugin(VFS_PLUGIN_ID));

            // FAILED 允许 unload（错误清理已完成）→ UNLOADED + 从 manager 移除
            manager.unloadPlugin(VFS_PLUGIN_ID);
            assertEquals(PluginState.UNLOADED, plugin.getState(), "unload 后回到 UNLOADED");
            assertFalse(manager.getLoadedPlugins().contains(plugin));
            assertNull(manager.getPlugin(VFS_PLUGIN_ID));
        } finally {
            AppConfig.getConfigProvider().assignConfigValue(GLOBAL_TOOLS_ENABLED, priorToolsEnabled);
        }
    }

    @Test
    public void testActivatePluginOnNotLoadedDefinitionFails() {
        PluginManagerImpl manager = newManager();
        NopException e = assertThrows(NopException.class,
                () -> manager.activatePlugin("/nop/plugin/test/never-loaded.plugin.xml"));
        assertEquals(ERR_PLUGIN_DEFINITION_NOT_LOADED.getErrorCode(), e.getErrorCode());
        assertEquals("/nop/plugin/test/never-loaded.plugin.xml", e.getParam("pluginId"));
    }

    @Test
    public void testReconcileSkipsJarTrack() throws IOException {
        MockPluginRecorder.reset();
        URL jar = buildPluginJar("io.nop.plugin.jarplugin.MockPlugin");
        PluginManagerImpl manager = newManager(jar);
        manager.loadPlugin(JAR_COORDS);

        // reconcile 遍历全部 VFS 轨定义时跳过 jar 轨（instanceof VfsPluginDefinition
        // 判别）——jar 插件在册时 reconcile 正常终止、无 unresolved 报告（No Silent No-Op 真实路径）
        manager.reconcilePlugins();
        assertTrue(manager.getUnresolvedPluginIds().isEmpty(), "jar 轨不参与 reconcile（无环检测报告）");
    }

    @Test
    public void testInvalidIdThrowsExplicitErrorWithIdParam() {
        PluginManagerImpl manager = newManager();

        NopException notFound = assertThrows(NopException.class, () -> manager.loadPlugin(NOT_EXISTS_PLUGIN_ID));
        assertEquals(ERR_PLUGIN_DEFINITION_NOT_FOUND.getErrorCode(), notFound.getErrorCode());
        assertEquals(NOT_EXISTS_PLUGIN_ID, notFound.getParam("pluginId"));

        NopException unparseable = assertThrows(NopException.class, () -> manager.loadPlugin(INVALID_PLUGIN_ID));
        assertNotNull(unparseable.getErrorCode());
        assertEquals(INVALID_PLUGIN_ID, unparseable.getParam("pluginId"));
    }

    @Test
    public void testVfsPathWithSingleColonRoutesToVfsTrack() {
        // 判别残余裁定：含单个 ":" 的 id（非双冒号坐标格式）必须走 VFS 轨而非 uber jar 轨。
        // 断言方式：manager 未配置 resolver——若误判为坐标走 jar 轨会抛 NPE；实际抛 NopException
        // （VFS 自身对含 ":" 的路径报 invalid-path，显式失败无静默跳过），证明路由正确。
        String singleColonVfsId = "/nop/plugin/test:foo/agent-tools.plugin.xml";
        PluginManagerImpl manager = newManager();

        NopException e = assertThrows(NopException.class, () -> manager.loadPlugin(singleColonVfsId));
        assertNotNull(e.getErrorCode());
        assertTrue(e.getErrorCode().contains("nop.err.core.resource.invalid-path")
                || ERR_PLUGIN_DEFINITION_NOT_FOUND.getErrorCode().equals(e.getErrorCode()));
    }
}
