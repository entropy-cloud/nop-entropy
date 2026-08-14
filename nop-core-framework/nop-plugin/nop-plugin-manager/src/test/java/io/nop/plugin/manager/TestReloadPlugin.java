package io.nop.plugin.manager;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.plugin.api.IPlugin;
import io.nop.plugin.api.IPluginInstance;
import io.nop.plugin.api.InstanceState;
import io.nop.plugin.api.PluginState;
import io.nop.plugin.manager.impl.PluginManagerImpl;
import io.nop.plugin.test.ITool;
import io.nop.plugin.test.MockPluginRecorder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_RELOAD_NOT_SUPPORTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W6 Phase 2：reloadPlugin + P2-A 配置快照（Plan-2026-08-14-2007-3）。
 * <ul>
 *     <li>端到端：loadPlugin（file: 可写资源）→ createInstance（实例配置 + 父子链）→
 *     修改 plugin.xml → reloadPlugin → 新定义生效（bean 变化可观测）、快照配置保留
 *     （原始实例配置回放 + 定义级 definitionConfig 快照）、父子链重建（getParent 指向
 *     新父实例对象）、reconcile 后状态正确。</li>
 *     <li>门控 null（W5 语义）→ pending：重建时定义级条件不满足 → 不误判为失败，
 *     记录 pending，门控打开 + reconcile 触发点重试重建（快照恢复语义）。</li>
 *     <li>跨定义后代：定义仍 LOADED → 重建；父实例已不存在 → pending 项显式错误丢弃。</li>
 *     <li>失败路径：load 段失败 → 定义 UNLOADED + 快照保留 + 从 map 移除（可恢复重载）；
 *     jar 轨 reload 显式失败（ERR_PLUGIN_RELOAD_NOT_SUPPORTED）。</li>
 * </ul>
 */
public class TestReloadPlugin {

    private static final String CHILD_ID = "/nop/plugin/test/child-tools.plugin.xml";
    private static final String CHILD_GATED_ID = "/nop/plugin/test/child-gated.plugin.xml";
    private static final String JAR_COORDS = "io.nop.plugin.test:mock-plugin:1.0.0";
    private static final String GLOBAL_TOOLS_ENABLED = "agent.tools.enabled";
    private static final String GLOBAL_CHILD_ENABLED = "reload.child.enabled";

    private static final File RELOAD_DIR = new File("target/plugin-reload-test/").getAbsoluteFile();
    private static final File RELOAD_TOOLS_FILE = new File(RELOAD_DIR, "reload-tools.plugin.xml");
    private static final String RELOAD_TOOLS_ID = "file:" + RELOAD_TOOLS_FILE.getAbsolutePath();
    private static final File RELOAD_GATED_FILE = new File(RELOAD_DIR, "reload-gated.plugin.xml");
    private static final String RELOAD_GATED_ID = "file:" + RELOAD_GATED_FILE.getAbsolutePath();

    private static final String TEMPLATE_TOOLS_V1 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<plugin name=\"reload-tools\" x:schema=\"/nop/schema/plugin/plugin.xdef\" xmlns:x=\"/nop/schema/xdsl.xdef\">\n"
            + "    <beans>\n"
            + "        <bean id=\"tool.bash\" class=\"io.nop.plugin.test.BashTool\" primary=\"true\"/>\n"
            + "        <bean id=\"tool.search\" class=\"io.nop.plugin.test.SearchTool\"/>\n"
            + "        <bean id=\"config.reader\" class=\"io.nop.plugin.test.ConfigReaderBean\">\n"
            + "            <property name=\"timeout\" value=\"${agent.timeout}\"/>\n"
            + "            <property name=\"mode\" value=\"${agent.mode:quiet}\"/>\n"
            + "            <property name=\"globalValue\" value=\"${test.plugin.global.var}\"/>\n"
            + "            <property name=\"onlyInstance\" value=\"${agent.only-instance:none}\"/>\n"
            + "        </bean>\n"
            + "    </beans>\n"
            + "</plugin>\n";

    private static final String TEMPLATE_TOOLS_V2 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<plugin name=\"reload-tools\" x:schema=\"/nop/schema/plugin/plugin.xdef\" xmlns:x=\"/nop/schema/xdsl.xdef\">\n"
            + "    <beans>\n"
            + "        <bean id=\"tool.bash\" class=\"io.nop.plugin.test.BashTool\"/>\n"
            + "        <bean id=\"tool.search\" class=\"io.nop.plugin.test.SearchTool\" primary=\"true\"/>\n"
            + "        <bean id=\"config.reader\" class=\"io.nop.plugin.test.ConfigReaderBean\">\n"
            + "            <property name=\"timeout\" value=\"${agent.timeout}\"/>\n"
            + "            <property name=\"mode\" value=\"${agent.mode:quiet}\"/>\n"
            + "            <property name=\"globalValue\" value=\"${test.plugin.global.var}\"/>\n"
            + "            <property name=\"onlyInstance\" value=\"${agent.only-instance:none}\"/>\n"
            + "        </bean>\n"
            + "    </beans>\n"
            + "</plugin>\n";

    private static final String TEMPLATE_GATED = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<plugin name=\"reload-gated\" if-property=\"agent.tools.enabled|true\"\n"
            + "        x:schema=\"/nop/schema/plugin/plugin.xdef\" xmlns:x=\"/nop/schema/xdsl.xdef\">\n"
            + "    <beans>\n"
            + "        <bean id=\"tool.bash\" class=\"io.nop.plugin.test.BashTool\" primary=\"true\"/>\n"
            + "    </beans>\n"
            + "</plugin>\n";

    private PluginManagerImpl manager;
    private Object priorToolsEnabled;
    private Object priorChildEnabled;

    @BeforeAll
    public static void init() throws IOException {
        CoreInitialization.initialize();
        AppConfig.getConfigProvider().assignConfigValue("agent.timeout", "global-timeout");
        AppConfig.getConfigProvider().assignConfigValue("test.plugin.global.var", "global-default");
        RELOAD_DIR.mkdirs();
        writeFile(RELOAD_TOOLS_FILE, TEMPLATE_TOOLS_V1);
        writeFile(RELOAD_GATED_FILE, TEMPLATE_GATED);
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    public void setUp() {
        manager = new PluginManagerImpl();
        manager.loadPlugin(CHILD_ID);
        manager.loadPlugin(CHILD_GATED_ID);
        priorToolsEnabled = AppConfig.getConfigProvider().getConfigValue(GLOBAL_TOOLS_ENABLED, null);
        AppConfig.getConfigProvider().assignConfigValue(GLOBAL_TOOLS_ENABLED, "true");
        priorChildEnabled = AppConfig.getConfigProvider().getConfigValue(GLOBAL_CHILD_ENABLED, null);
        AppConfig.getConfigProvider().assignConfigValue(GLOBAL_CHILD_ENABLED, "true");
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
        AppConfig.getConfigProvider().assignConfigValue(GLOBAL_CHILD_ENABLED, priorChildEnabled);
    }

    private static void writeFile(File file, String content) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * 端到端验证：loadPlugin（file: 可写资源）→ createInstance（实例配置 + 父子链）→
     * 修改定义 → reloadPlugin → 新实例存在、快照配置保留、父子链重建（getParent）、
     * 新定义生效（primary 切换可观测）、reconcile 后状态正确。
     */
    @Test
    public void testReloadEndToEndSnapshotRebuild() throws IOException {
        IPlugin fileDef = manager.loadPlugin(RELOAD_TOOLS_ID);
        fileDef.updateConfig(Map.of("agent.mode", "dev"));
        IPluginInstance parent = manager.createInstance(RELOAD_TOOLS_ID, "p1",
                Map.of("agent.timeout", "10", "agent.only-instance", "from-parent"), null);
        IPluginInstance child = manager.createInstance(CHILD_ID, "c1", Map.of("agent.timeout", "20"), parent);

        assertEquals("bash", parent.getService(ITool.class).getName());
        assertEquals("bash", child.getService(ITool.class).getName(), "跨定义回退到父容器（旧定义）");
        assertEquals("dev", child.getConfig().get("agent.mode"), "父定义级配置经层叠进子合并视图");

        // 修改定义内容（primary 从 BashTool 切到 SearchTool）→ reload
        writeFile(RELOAD_TOOLS_FILE, TEMPLATE_TOOLS_V2);
        manager.reloadPlugin(RELOAD_TOOLS_ID);

        IPluginInstance newParent = manager.getInstance(RELOAD_TOOLS_ID, "p1");
        IPluginInstance newChild = manager.getInstance(CHILD_ID, "c1");
        assertNotNull(newParent, "快照重建：父实例存在");
        assertNotNull(newChild, "快照重建：跨定义后代实例存在（定义仍 LOADED → 重建，不静默丢失）");
        assertNotSame(parent, newParent, "新定义对象（旧实例已销毁）");
        assertEquals(InstanceState.ACTIVATED, newParent.getState());
        assertEquals(InstanceState.ACTIVATED, newChild.getState(), "reconcile 后状态正确");

        // 父子链重建（getParent 断言指向新父实例对象）
        assertEquals(newParent, newChild.getParent(), "父子链重建到新父实例");
        assertNull(newParent.getParent());

        // 快照配置保留：原始实例配置回放 + 定义级 definitionConfig 快照
        assertEquals("20", newChild.getConfig().get("agent.timeout"), "子原始实例配置回放");
        assertEquals("10", newParent.getConfig().get("agent.timeout"));
        assertEquals("from-parent", newParent.getConfig().get("agent.only-instance"));
        assertEquals("dev", newParent.getConfig().get("agent.mode"), "定义级 definitionConfig 快照保留");

        // 新定义生效（bean 变化可观测：primary 切换后经回退链命中新容器）
        assertEquals("search", newParent.getService(ITool.class).getName());
        assertEquals("search", newChild.getService(ITool.class).getName(), "子沿链回退到新父容器");

        // reconcile 幂等
        manager.reconcileInstances();
        assertEquals(InstanceState.ACTIVATED, newChild.getState());
    }

    /**
     * 门控 null（W5 语义）→ pending 重建路径：重建时定义级条件不满足 → 不误判为失败
     * （reload 不抛异常、实例不创建），pending 项保留；门控打开 + reconcile 触发点重试
     * 重建成功（快照配置保留）。
     */
    @Test
    public void testReloadGatedInstanceBecomesPendingAndRetries() throws IOException {
        manager.loadPlugin(RELOAD_GATED_ID);
        IPluginInstance g1 = manager.createInstance(RELOAD_GATED_ID, "g1", Map.of("agent.timeout", "7"), null);
        assertEquals(InstanceState.ACTIVATED, g1.getState());

        // 关闭门控（全局 agent.tools.enabled=false）→ 显式 reconcile → 实例级联去激活
        AppConfig.getConfigProvider().assignConfigValue(GLOBAL_TOOLS_ENABLED, "false");
        manager.reconcileInstances();
        assertEquals(InstanceState.DEACTIVATED, g1.getState());

        // reload：重建时门控关闭 → createInstance 返回 null → pending（不误判为失败）
        manager.reloadPlugin(RELOAD_GATED_ID);
        assertNull(manager.getInstance(RELOAD_GATED_ID, "g1"), "门控关闭下重建为 pending（不创建实例、不抛异常）");
        assertEquals(1, manager.getPendingRebuildCount(), "pending 快照项保留");

        // 仍门控：reconcile 重试不创建（计数不变）
        manager.reconcileInstances();
        assertNull(manager.getInstance(RELOAD_GATED_ID, "g1"));
        assertEquals(1, manager.getPendingRebuildCount());

        // 门控打开 → reconcile 触发点重试重建（快照配置保留）
        AppConfig.getConfigProvider().assignConfigValue(GLOBAL_TOOLS_ENABLED, "true");
        manager.reconcileInstances();
        IPluginInstance rebuilt = manager.getInstance(RELOAD_GATED_ID, "g1");
        assertNotNull(rebuilt, "pending 重试重建成功");
        assertEquals(InstanceState.ACTIVATED, rebuilt.getState());
        assertEquals("7", rebuilt.getConfig().get("agent.timeout"), "快照原始配置保留");
        assertEquals(0, manager.getPendingRebuildCount());
    }

    /**
     * 跨定义后代 + pending 生命周期：父门控关闭 → 父 + 子（PARENT_PENDING）均 pending；
     * 父门控打开（子门控关闭）→ 父重建、子保持 pending；父被 destroy 后 → 子 pending 项
     * 显式错误丢弃（父实例已不存在，不静默悬挂）。
     */
    @Test
    public void testReloadPendingParentChainLifecycle() throws IOException {
        manager.loadPlugin(RELOAD_GATED_ID);
        IPluginInstance parent = manager.createInstance(RELOAD_GATED_ID, "p1", Map.of(), null);
        IPluginInstance child = manager.createInstance(CHILD_GATED_ID, "c1", Map.of(), parent);
        assertEquals(InstanceState.ACTIVATED, child.getState());

        // 只关父门控（子门控键仍 true）→ reload：父 GATED、子 PARENT_PENDING → 2 pending
        AppConfig.getConfigProvider().assignConfigValue(GLOBAL_TOOLS_ENABLED, "false");
        manager.reloadPlugin(RELOAD_GATED_ID);
        assertEquals(2, manager.getPendingRebuildCount(), "父 + 子均 pending（子等待父）");
        assertNull(manager.getInstance(RELOAD_GATED_ID, "p1"));
        assertNull(manager.getInstance(CHILD_GATED_ID, "c1"));

        // 开父门控 + 关子门控 → reconcile 重试：父先建成功；子门控关闭仍 pending
        AppConfig.getConfigProvider().assignConfigValue(GLOBAL_TOOLS_ENABLED, "true");
        AppConfig.getConfigProvider().assignConfigValue(GLOBAL_CHILD_ENABLED, "false");
        manager.reconcileInstances();
        IPluginInstance newParent = manager.getInstance(RELOAD_GATED_ID, "p1");
        assertNotNull(newParent, "父门控打开后重建");
        assertEquals(InstanceState.ACTIVATED, newParent.getState());
        assertNull(manager.getInstance(CHILD_GATED_ID, "c1"), "子门控关闭仍 pending");
        assertEquals(1, manager.getPendingRebuildCount());

        // 父被 destroy → 子 pending 项父引用不可解析 → 显式错误丢弃（不悬挂）
        manager.destroyInstance(RELOAD_GATED_ID, "p1");
        manager.reconcileInstances();
        assertNull(manager.getInstance(CHILD_GATED_ID, "c1"));
        assertEquals(0, manager.getPendingRebuildCount(), "父不存在后 pending 项丢弃");
    }

    /**
     * 失败路径（load 段）：破坏定义文件 → reloadPlugin 抛明确异常、定义 UNLOADED +
     * 快照保留 + 实例已销毁（状态可观测）；修复文件后 loadPlugin 恢复、reloadPlugin 成功。
     */
    @Test
    public void testReloadLoadFailureLeavesUnloadedWithSnapshotRetained() throws IOException {
        manager.loadPlugin(RELOAD_TOOLS_ID);
        IPluginInstance p1 = manager.createInstance(RELOAD_TOOLS_ID, "p1", Map.of("agent.timeout", "10"), null);
        assertEquals(InstanceState.ACTIVATED, p1.getState());
        IPlugin oldDef = manager.loadPlugin(RELOAD_TOOLS_ID);

        writeFile(RELOAD_TOOLS_FILE, "<plugin this-is-not-valid-xml");
        assertThrows(NopException.class, () -> manager.reloadPlugin(RELOAD_TOOLS_ID));
        assertNotNull(manager.getLastReloadSnapshot(), "load 段失败快照保留（可重试）");
        assertNull(manager.getInstance(RELOAD_TOOLS_ID, "p1"), "实例已级联销毁（状态可观测）");
        assertTrue(!manager.getLoadedPlugins().contains(oldDef),
                "失败后定义从 map 移除（不残留不可重载的陈旧 UNLOADED 定义）");

        // 修复文件 → loadPlugin 恢复 → reloadPlugin 成功（快照为空，重建无实例）
        writeFile(RELOAD_TOOLS_FILE, TEMPLATE_TOOLS_V2);
        IPlugin recovered = manager.loadPlugin(RELOAD_TOOLS_ID);
        assertEquals(PluginState.LOADED, recovered.getState());
        manager.reloadPlugin(RELOAD_TOOLS_ID);
        IPluginInstance fresh = manager.createInstance(RELOAD_TOOLS_ID, "p1", Map.of("agent.timeout", "10"), null);
        assertEquals(InstanceState.ACTIVATED, fresh.getState());
        assertEquals("10", fresh.getConfig().get("agent.timeout"));
    }

    /**
     * jar 轨 reloadPlugin 显式失败（设计 §六 HMR 面向本地/开发场景；uber jar 不可编辑）。
     */
    @Test
    public void testReloadJarTrackFailsExplicitly() throws IOException {
        URL jar = buildPluginJar("io.nop.plugin.jarplugin.MockPlugin");
        manager.setResourceResolver(coords -> List.of(jar));
        manager.setPluginConfigProvider(coords -> Collections.emptyMap());
        manager.loadPlugin(JAR_COORDS);

        NopException e = assertThrows(NopException.class, () -> manager.reloadPlugin(JAR_COORDS));
        assertEquals(ERR_PLUGIN_RELOAD_NOT_SUPPORTED.getErrorCode(), e.getErrorCode());
        assertEquals(JAR_COORDS, e.getParam("pluginId"));
    }

    private static URL buildPluginJar(String className) throws IOException {
        MockPluginRecorder.reset();
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
            try (InputStream in = TestReloadPlugin.class.getClassLoader().getResourceAsStream(classPath)) {
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
