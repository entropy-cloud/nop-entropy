package io.nop.plugin.manager;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.plugin.api.IPlugin;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R1 HMR 热重载测试（plan-2026-08-22-2309-1 Phase 2 过渡语义：deactivate → unload →
 * load（重放定义级 updateConfig 累积值）→ reconcile；无实例快照重建——多实例机制已移除）：
 * <ul>
 *     <li>端到端：loadPlugin（file: 可写资源，自动激活）→ updateConfig 累积 → 修改 plugin.xml →
 *     reloadPlugin → 若激活态先 deactivate → unload → load（定义级配置域重放）→ reconcile
 *     重新门控激活；新定义生效（primary 切换可观测）。</li>
 *     <li>门控关闭 reload：reload 后定义保持 LOADED（reconcile 不激活）；门控打开 + reconcile
 *     → 自动激活（原 pending 机制随实例删除，由 reconcile 固定点语义取代）。</li>
 *     <li>失败路径：load 段失败 → 定义从 map 移除（可重新 loadPlugin 恢复）。</li>
 *     <li>jar 轨 reload 显式失败（ERR_PLUGIN_RELOAD_NOT_SUPPORTED）。</li>
 * </ul>
 */
public class TestReloadPlugin {

    private static final String CHILD_ID = "/nop/plugin/test/child-tools.plugin.xml";
    private static final String JAR_COORDS = "io.nop.plugin.test:mock-plugin:1.0.0";
    private static final String GLOBAL_TOOLS_ENABLED = "agent.tools.enabled";

    private static final File RELOAD_DIR = new File("target/plugin-reload-test/").getAbsoluteFile();
    private static final File RELOAD_TOOLS_FILE = new File(RELOAD_DIR, "reload-tools.plugin.xml");
    private static final String RELOAD_TOOLS_ID = toFileId(RELOAD_TOOLS_FILE);
    private static final File RELOAD_GATED_FILE = new File(RELOAD_DIR, "reload-gated.plugin.xml");
    private static final String RELOAD_GATED_ID = toFileId(RELOAD_GATED_FILE);

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
            + "        <bean id=\"config.reader\" class=\"io.nop.plugin.test.ConfigReaderBean\">\n"
            + "            <property name=\"timeout\" value=\"${agent.timeout}\"/>\n"
            + "            <property name=\"mode\" value=\"${agent.mode:quiet}\"/>\n"
            + "        </bean>\n"
            + "    </beans>\n"
            + "</plugin>\n";

    /**
     * file: 命名空间 VFS 路径规范化：Windows 盘符路径（C:\...）需转为 /C:/... 形式（FileNamespaceHandler
     * 校验要求以 "/" 开头且第 3 个字符为 ':'），且统一为 "/" 分隔符；Linux 绝对路径天然符合。
     */
    private static String toFileId(File file) {
        String path = file.getAbsolutePath();
        if (File.separatorChar == '\\') {
            path = path.replace('\\', '/');
            if (!path.startsWith("/"))
                path = "/" + path;
        }
        return "file:" + path;
    }

    private PluginManagerImpl manager;
    private Object priorToolsEnabled;

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
    public void setUp() throws IOException {
        manager = new PluginManagerImpl();
        manager.loadPlugin(CHILD_ID);
        // 每用例重置 tools 文件为 V1（测试独立，与执行顺序无关——失败路径用例会改写文件）
        writeFile(RELOAD_TOOLS_FILE, TEMPLATE_TOOLS_V1);
        priorToolsEnabled = AppConfig.getConfigProvider().getConfigValue(GLOBAL_TOOLS_ENABLED, null);
        AppConfig.getConfigProvider().assignConfigValue(GLOBAL_TOOLS_ENABLED, "true");
    }

    @AfterEach
    public void tearDown() {
        for (IPlugin plugin : manager.getLoadedPlugins()) {
            try {
                plugin.deactivate();
            } catch (RuntimeException ignore) {
                // Intentionally ignored: tearDown must proceed
            }
            try {
                plugin.unload();
            } catch (RuntimeException ignore) {
                // Intentionally ignored: cleanup tolerance
            }
        }
        AppConfig.getConfigProvider().assignConfigValue(GLOBAL_TOOLS_ENABLED, priorToolsEnabled);
    }

    private static void writeFile(File file, String content) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * 端到端验证：loadPlugin（file: 可写资源，自动激活）→ updateConfig 累积 → 修改定义 →
     * reloadPlugin（ACTIVATED → 先 deactivate → unload → load 重放定义级配置域 → reconcile
     * 重新激活）→ 新定义对象、新定义生效（primary 切换可观测）、定义级配置域累积值保留。
     */
    @Test
    public void testReloadEndToEndDefinitionConfigReplay() throws IOException {
        IPlugin oldDef = manager.loadPlugin(RELOAD_TOOLS_ID);
        assertEquals(PluginState.ACTIVATED, oldDef.getState(), "无门控 → loadPlugin 自动激活");
        assertEquals("bash", oldDef.getService(ITool.class).getName());
        oldDef.updateConfig(Map.of("agent.mode", "dev"));
        assertEquals("dev", oldDef.getService(io.nop.plugin.test.IConfigReader.class).getMode());

        // 修改定义内容（primary 从 BashTool 切到 SearchTool）→ reload
        writeFile(RELOAD_TOOLS_FILE, TEMPLATE_TOOLS_V2);
        manager.reloadPlugin(RELOAD_TOOLS_ID);

        IPlugin newDef = manager.loadPlugin(RELOAD_TOOLS_ID);
        assertNotSame(oldDef, newDef, "reloadPlugin 产出新定义对象");
        assertEquals(PluginState.ACTIVATED, newDef.getState(), "reconcile 重新门控激活");
        assertEquals("search", newDef.getService(ITool.class).getName(), "新定义生效（primary 切换可观测）");
        assertEquals("dev", newDef.getService(io.nop.plugin.test.IConfigReader.class).getMode(),
                "定义级 updateConfig 累积值在 reload 后重放（配置域保留）");

        // reconcile 幂等
        manager.reconcilePlugins();
        assertEquals(PluginState.ACTIVATED, newDef.getState());
    }

    /**
     * 门控关闭 reload：ACTIVATED 的门控定义 → 关门控 → reload → deactivate → unload → load →
     * reconcile（门控关闭不激活）→ 保持 LOADED；门控打开 + reconcile → 自动激活
     * （原 pending 重建机制随实例删除，由 reconcile 固定点语义取代）。
     */
    @Test
    public void testReloadWhenGateClosedStaysLoadedUntilReconcile() throws IOException {
        IPlugin def = manager.loadPlugin(RELOAD_GATED_ID);
        assertEquals(PluginState.ACTIVATED, def.getState());

        // 关门控 → reload：重新装载后 reconcile 不激活（门控关闭）
        AppConfig.getConfigProvider().assignConfigValue(GLOBAL_TOOLS_ENABLED, "false");
        manager.reloadPlugin(RELOAD_GATED_ID);
        IPlugin newDef = manager.getPlugin(RELOAD_GATED_ID);
        assertNotSame(def, newDef);
        assertEquals(PluginState.LOADED, newDef.getState(), "门控关闭 → reload 后保持 LOADED");

        // 门控仍关：reconcile 不激活
        manager.reconcilePlugins();
        assertEquals(PluginState.LOADED, newDef.getState());

        // 门控打开 + reconcile → 自动激活（恢复语义）
        AppConfig.getConfigProvider().assignConfigValue(GLOBAL_TOOLS_ENABLED, "true");
        manager.reconcilePlugins();
        assertEquals(PluginState.ACTIVATED, newDef.getState(), "门控打开 → reconcile 自动激活");
    }

    /**
     * 失败路径（load 段）：破坏定义文件 → reloadPlugin 抛明确异常、定义从 map 移除
     * （可重新 loadPlugin 恢复，不残留不可重载的陈旧 UNLOADED 定义）；修复文件后恢复。
     */
    @Test
    public void testReloadLoadFailureLeavesUnloadedRecoverable() throws IOException {
        IPlugin oldDef = manager.loadPlugin(RELOAD_TOOLS_ID);
        assertEquals(PluginState.ACTIVATED, oldDef.getState());

        writeFile(RELOAD_TOOLS_FILE, "<plugin this-is-not-valid-xml");
        assertThrows(NopException.class, () -> manager.reloadPlugin(RELOAD_TOOLS_ID));
        assertNull(manager.getPlugin(RELOAD_TOOLS_ID), "load 段失败后定义从 map 移除（可恢复）");
        assertTrue(!manager.getLoadedPlugins().contains(oldDef));

        // 修复文件 → loadPlugin 恢复（自动激活）
        writeFile(RELOAD_TOOLS_FILE, TEMPLATE_TOOLS_V2);
        IPlugin recovered = manager.loadPlugin(RELOAD_TOOLS_ID);
        assertEquals(PluginState.ACTIVATED, recovered.getState());
        assertEquals("search", recovered.getService(ITool.class).getName());
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
