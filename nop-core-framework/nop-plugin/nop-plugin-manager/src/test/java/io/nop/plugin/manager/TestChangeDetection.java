package io.nop.plugin.manager;

import io.nop.api.core.config.AppConfig;
import io.nop.core.initialize.CoreInitialization;
import io.nop.plugin.api.IPlugin;
import io.nop.plugin.api.PluginState;
import io.nop.plugin.manager.impl.PluginManagerImpl;
import io.nop.plugin.manager.impl.VfsPluginDefinition;
import io.nop.plugin.test.ITool;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R1 变更检测接线测试（loader 依赖追踪，Plan-2026-08-22-2309-1 收缩为单激活流）：
 * <ul>
 *     <li>显式检查入口（{@code checkChangedAndReload}）：修改可写 file: 资源 plugin.xml（内容
 *     变化）→ 入口触发 reloadPlugin（新定义生效、自动重新激活、bean 变化可观测）——
 *     真实 lastModified 严格比对（非 getLastChangeTime 时钟语义）。</li>
 *     <li>无变更 no-op：lastModified 相等 → 入口不触发 reload（定义对象不变）。</li>
 *     <li>依赖方收敛：requires 链上被 reload 的定义门控关闭 → 依赖方经 reconcile 级联去激活；
 *     门控打开 → reconcile 恢复激活。</li>
 * </ul>
 */
public class TestChangeDetection {

    private static final String GLOBAL_TOOLS_ENABLED = "agent.tools.enabled";
    private static final String PRODUCER_ENABLED = "change.producer.enabled";

    /**
     * 测试机制裁定：classpath 测试资源不可写——HMR 测试用 file: 前缀 id 的
     * 可写临时目录（DefaultVirtualFileSystem 默认注册 file: 命名空间，无需自定义 VFS 注册），
     * 保持真实 lastModified 比对的端到端真实性。目录与 TestReloadPlugin 同模式放 target/
     * （项目内可写目录，mvn clean 清理）。
     */
    private static final File CHANGE_DIR = new File("target/plugin-change-test/").getAbsoluteFile();
    private static final File TOOLS_FILE = new File(CHANGE_DIR, "change-tools.plugin.xml");
    private static final String TOOLS_ID = toFileId(TOOLS_FILE);
    private static final File PRODUCER_FILE = new File(CHANGE_DIR, "change-producer.plugin.xml");
    private static final String PRODUCER_ID = toFileId(PRODUCER_FILE);
    private static final File CONSUMER_FILE = new File(CHANGE_DIR, "change-consumer.plugin.xml");
    private static final String CONSUMER_ID = toFileId(CONSUMER_FILE);

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

    private static final String TOOLS_V1 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<plugin name=\"change-tools\" x:schema=\"/nop/schema/plugin/plugin.xdef\" xmlns:x=\"/nop/schema/xdsl.xdef\">\n"
            + "    <beans>\n"
            + "        <bean id=\"tool.bash\" class=\"io.nop.plugin.test.BashTool\" primary=\"true\"/>\n"
            + "        <bean id=\"tool.search\" class=\"io.nop.plugin.test.SearchTool\"/>\n"
            + "    </beans>\n"
            + "</plugin>\n";

    private static final String TOOLS_V2 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<plugin name=\"change-tools\" x:schema=\"/nop/schema/plugin/plugin.xdef\" xmlns:x=\"/nop/schema/xdsl.xdef\">\n"
            + "    <beans>\n"
            + "        <bean id=\"tool.bash\" class=\"io.nop.plugin.test.BashTool\"/>\n"
            + "        <bean id=\"tool.search\" class=\"io.nop.plugin.test.SearchTool\" primary=\"true\"/>\n"
            + "    </beans>\n"
            + "</plugin>\n";

    private static final String PRODUCER_V1 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<plugin name=\"change-producer\" x:schema=\"/nop/schema/plugin/plugin.xdef\" xmlns:x=\"/nop/schema/xdsl.xdef\">\n"
            + "    <beans>\n"
            + "        <bean id=\"tool.bash\" class=\"io.nop.plugin.test.BashTool\" primary=\"true\"/>\n"
            + "    </beans>\n"
            + "</plugin>\n";

    private static final String PRODUCER_GATED = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<plugin name=\"change-producer\" if-property=\"change.producer.enabled|true\"\n"
            + "        x:schema=\"/nop/schema/plugin/plugin.xdef\" xmlns:x=\"/nop/schema/xdsl.xdef\">\n"
            + "    <beans>\n"
            + "        <bean id=\"tool.bash\" class=\"io.nop.plugin.test.BashTool\" primary=\"true\"/>\n"
            + "    </beans>\n"
            + "</plugin>\n";

    private static final String CONSUMER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<plugin name=\"change-consumer\" requires=\"change-producer\"\n"
            + "        x:schema=\"/nop/schema/plugin/plugin.xdef\" xmlns:x=\"/nop/schema/xdsl.xdef\">\n"
            + "    <beans>\n"
            + "        <bean id=\"tool.bash\" class=\"io.nop.plugin.test.BashTool\" primary=\"true\"/>\n"
            + "    </beans>\n"
            + "</plugin>\n";

    private PluginManagerImpl manager;
    private Object priorToolsEnabled;
    private Object priorProducerEnabled;

    @BeforeAll
    public static void init() throws IOException {
        CoreInitialization.initialize();
        CHANGE_DIR.mkdirs();
        writeFile(PRODUCER_FILE, PRODUCER_V1);
        writeFile(CONSUMER_FILE, CONSUMER);
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    public void setUp() throws IOException {
        manager = new PluginManagerImpl();
        // 每用例重置 tools 文件为 V1（测试独立，与执行顺序无关）
        writeFile(TOOLS_FILE, TOOLS_V1);
        priorToolsEnabled = AppConfig.getConfigProvider().getConfigValue(GLOBAL_TOOLS_ENABLED, null);
        AppConfig.getConfigProvider().assignConfigValue(GLOBAL_TOOLS_ENABLED, "true");
        priorProducerEnabled = AppConfig.getConfigProvider().getConfigValue(PRODUCER_ENABLED, null);
        AppConfig.getConfigProvider().assignConfigValue(PRODUCER_ENABLED, "true");
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
        AppConfig.getConfigProvider().assignConfigValue(PRODUCER_ENABLED, priorProducerEnabled);
    }

    private static void writeFile(File file, String content) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * 接线验证：修改可写资源 plugin.xml（内容变化）→ 显式检查入口 → reloadPlugin 真实触发
     * （新定义生效：primary 切换可观测、自动重新激活）；load 时记录的 lastModified 为资源真实 mtime。
     */
    @Test
    public void testCheckChangedAndReloadTriggersReload() throws IOException {
        IPlugin oldDef = manager.loadPlugin(TOOLS_ID);
        assertEquals(PluginState.ACTIVATED, oldDef.getState(), "无门控 → 自动激活");
        assertEquals("bash", oldDef.getService(ITool.class).getName());
        VfsPluginDefinition oldVfs = (VfsPluginDefinition) oldDef;
        assertEquals(TOOLS_FILE.lastModified(), oldVfs.getLastModified(),
                "load 时记录资源真实 lastModified（变更检测比对源）");

        // 内容变化 + mtime 严格推进（相对记录值 +2s，跨 ms 边界确定性）
        writeFile(TOOLS_FILE, TOOLS_V2);
        TOOLS_FILE.setLastModified(oldVfs.getLastModified() + 2000);
        manager.checkChangedAndReload();

        IPlugin newDef = manager.getPlugin(TOOLS_ID);
        assertNotSame(oldDef, newDef, "checkChangedAndReload 触发 reloadPlugin（新定义对象）");
        assertEquals(PluginState.ACTIVATED, newDef.getState(), "reload 后 reconcile 重新激活");
        assertEquals("search", newDef.getService(ITool.class).getName(),
                "新定义生效（bean 变化可观测）");
        assertEquals(TOOLS_FILE.lastModified(), ((VfsPluginDefinition) newDef).getLastModified(),
                "reload 后重新记录新 mtime");
    }

    /**
     * 无变更 no-op：lastModified 相等 → 检查入口不触发 reload（定义对象不变、保持激活）。
     */
    @Test
    public void testCheckChangedAndReloadNoChangeNoOp() throws IOException {
        IPlugin def = manager.loadPlugin(TOOLS_ID);
        assertEquals(PluginState.ACTIVATED, def.getState());
        assertEquals("bash", def.getService(ITool.class).getName());

        manager.checkChangedAndReload();
        manager.checkChangedAndReload();

        assertSame(def, manager.getPlugin(TOOLS_ID), "无变更：定义对象不变（未触发 reload）");
        assertEquals(PluginState.ACTIVATED, def.getState(), "无变更：保持激活");
    }

    /**
     * 依赖方收敛（requires 链）：reload 后定义级门控关闭 → 依赖方经 reconcile 级联去激活；
     * 门控打开 → reconcile 恢复双方激活（级联评估覆盖，收敛到正确状态）。
     */
    @Test
    public void testReloadDependentConvergesViaReconcile() throws IOException {
        manager.loadPlugin(PRODUCER_ID);
        IPlugin consumer = manager.loadPlugin(CONSUMER_ID);
        assertEquals(PluginState.ACTIVATED, manager.getPlugin(PRODUCER_ID).getState());
        assertEquals(PluginState.ACTIVATED, consumer.getState(), "requires 满足 → 依赖方自动激活");

        // 定义变更：producer 增加 if-property 门控（当前全局值 false）→ reload → producer
        // 重载后门控关闭保持 LOADED → reconcile → 依赖方 requires 不满足 → 级联去激活
        AppConfig.getConfigProvider().assignConfigValue(PRODUCER_ENABLED, "false");
        VfsPluginDefinition producerDef = (VfsPluginDefinition) manager.loadPlugin(PRODUCER_ID);
        writeFile(PRODUCER_FILE, PRODUCER_GATED);
        PRODUCER_FILE.setLastModified(producerDef.getLastModified() + 2000);
        manager.checkChangedAndReload();

        assertEquals(PluginState.LOADED, manager.getPlugin(PRODUCER_ID).getState(),
                "reload 后门控关闭 → 保持 LOADED");
        assertEquals(PluginState.LOADED, consumer.getState(), "依赖方经 reconcile 级联去激活（收敛）");

        // 门控打开 → reconcile：producer 恢复激活 → 依赖方 requires 恢复满足 → 重新激活（收敛）
        AppConfig.getConfigProvider().assignConfigValue(PRODUCER_ENABLED, "true");
        manager.reconcilePlugins();

        assertEquals(PluginState.ACTIVATED, manager.getPlugin(PRODUCER_ID).getState());
        assertEquals(PluginState.ACTIVATED, consumer.getState(), "依赖方经 reconcile 恢复激活（收敛）");
        assertTrue(manager.getUnresolvedPluginIds().isEmpty(), "无环未解析报告");
    }
}
