package io.nop.plugin.manager;

import io.nop.api.core.config.AppConfig;
import io.nop.core.initialize.CoreInitialization;
import io.nop.plugin.api.IPlugin;
import io.nop.plugin.api.IPluginInstance;
import io.nop.plugin.api.InstanceState;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W6 Phase 3：loader 依赖追踪接线（变更检测，Plan-2026-08-14-2007-3）。
 * <ul>
 *     <li>显式检查入口（{@code checkChangedAndReload}）：修改可写 file: 资源 plugin.xml（内容
 *     变化）→ 入口触发 reloadPlugin（新定义生效、bean 变化可观测）——真实 lastModified 严格
 *     比对（非 getLastChangeTime 时钟语义）。</li>
 *     <li>无变更 no-op：lastModified 相等 → 入口不触发 reload（比对正确性证明）。</li>
 *     <li>依赖方收敛：requires 链上被 reload 定义的依赖方经 reconcile 收敛到正确状态
 *     （reload 后门控关闭 → 依赖方级联去激活；门控打开 → pending 重建 + 依赖方恢复激活）。</li>
 * </ul>
 */
public class TestChangeDetection {

    private static final String GLOBAL_TOOLS_ENABLED = "agent.tools.enabled";
    private static final String PRODUCER_ENABLED = "change.producer.enabled";

    /**
     * 测试机制裁定（本 plan Phase 3）：classpath 测试资源不可写——HMR 测试用 file: 前缀 id 的
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
            for (IPluginInstance instance : plugin.getInstances()) {
                try {
                    instance.destroy();
                } catch (RuntimeException ignore) {
                    // Intentionally ignored: tearDown must proceed even if a deactivated instance rejects destroy
                }
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
     * （新定义生效：primary 切换可观测）；load 时记录的 lastModified 为资源真实 mtime。
     */
    @Test
    public void testCheckChangedAndReloadTriggersReload() throws IOException {
        IPlugin oldDef = manager.loadPlugin(TOOLS_ID);
        IPluginInstance oldInstance = manager.createInstance(TOOLS_ID, "p1", java.util.Map.of(), null);
        assertEquals("bash", oldInstance.getService(ITool.class).getName());
        VfsPluginDefinition oldVfs = (VfsPluginDefinition) oldDef;
        assertEquals(TOOLS_FILE.lastModified(), oldVfs.getLastModified(),
                "load 时记录资源真实 lastModified（变更检测比对源）");

        // 内容变化 + mtime 严格推进（相对记录值 +2s，跨 ms 边界确定性）
        writeFile(TOOLS_FILE, TOOLS_V2);
        TOOLS_FILE.setLastModified(oldVfs.getLastModified() + 2000);
        manager.checkChangedAndReload();

        IPlugin newDef = manager.loadPlugin(TOOLS_ID);
        assertNotSame(oldDef, newDef, "checkChangedAndReload 触发 reloadPlugin（新定义对象）");
        IPluginInstance newInstance = manager.getInstance(TOOLS_ID, "p1");
        assertNotNull(newInstance, "快照重建实例");
        assertNotSame(oldInstance, newInstance);
        assertEquals("search", newInstance.getService(ITool.class).getName(),
                "新定义生效（bean 变化可观测）");
        assertEquals(TOOLS_FILE.lastModified(), ((VfsPluginDefinition) newDef).getLastModified(),
                "reload 后重新记录新 mtime");
    }

    /**
     * 无变更 no-op：lastModified 相等 → 检查入口不触发 reload（定义对象与实例均不变）。
     */
    @Test
    public void testCheckChangedAndReloadNoChangeNoOp() throws IOException {
        IPlugin def = manager.loadPlugin(TOOLS_ID);
        IPluginInstance instance = manager.createInstance(TOOLS_ID, "p1", java.util.Map.of(), null);
        assertEquals("bash", instance.getService(ITool.class).getName());

        manager.checkChangedAndReload();
        manager.checkChangedAndReload();

        assertSame(def, manager.loadPlugin(TOOLS_ID), "无变更：定义对象不变（未触发 reload）");
        assertSame(instance, manager.getInstance(TOOLS_ID, "p1"), "无变更：实例对象不变");
        assertEquals(InstanceState.ACTIVATED, instance.getState());
        assertEquals(0, manager.getPendingRebuildCount());
    }

    /**
     * 依赖方收敛（requires 链）：reload 后定义级门控关闭 → 依赖方实例经 reconcile 级联去激活；
     * 门控打开 → pending 重建 + 依赖方经 reconcile 恢复激活（W5 级联评估覆盖，收敛到正确状态）。
     */
    @Test
    public void testReloadDependentConvergesViaReconcile() throws IOException {
        manager.loadPlugin(PRODUCER_ID);
        IPluginInstance p1 = manager.createInstance(PRODUCER_ID, "p1", java.util.Map.of(), null);
        assertEquals(InstanceState.ACTIVATED, p1.getState());

        manager.loadPlugin(CONSUMER_ID);
        IPluginInstance c1 = manager.createInstance(CONSUMER_ID, "c1", java.util.Map.of(), null);
        assertEquals(InstanceState.ACTIVATED, c1.getState(), "requires 满足 → 依赖方实例激活");

        // 定义变更：producer 增加 if-property 门控（当前全局值 false）→ reload → p1 重建门控
        // 关闭 → pending（实例不存在）→ reconcile → 依赖方 requires 不满足 → 级联去激活
        AppConfig.getConfigProvider().assignConfigValue(PRODUCER_ENABLED, "false");
        VfsPluginDefinition producerDef = (VfsPluginDefinition) manager.loadPlugin(PRODUCER_ID);
        writeFile(PRODUCER_FILE, PRODUCER_GATED);
        PRODUCER_FILE.setLastModified(producerDef.getLastModified() + 2000);
        manager.checkChangedAndReload();

        assertEquals(1, manager.getPendingRebuildCount(), "门控关闭 → p1 快照项 pending");
        assertEquals(InstanceState.DEACTIVATED, c1.getState(), "依赖方经 reconcile 级联去激活（收敛）");

        // 门控打开 → reconcile 重试重建 p1 → 依赖方 requires 恢复满足 → 重新激活（收敛）
        AppConfig.getConfigProvider().assignConfigValue(PRODUCER_ENABLED, "true");
        manager.reconcileInstances();

        IPluginInstance newP1 = manager.getInstance(PRODUCER_ID, "p1");
        assertNotNull(newP1, "pending 重试重建");
        assertEquals(InstanceState.ACTIVATED, newP1.getState());
        assertEquals(InstanceState.ACTIVATED, c1.getState(), "依赖方经 reconcile 恢复激活（收敛）");
        assertTrue(manager.getUnresolvedPluginIds().isEmpty(), "无环未解析报告");
    }
}
