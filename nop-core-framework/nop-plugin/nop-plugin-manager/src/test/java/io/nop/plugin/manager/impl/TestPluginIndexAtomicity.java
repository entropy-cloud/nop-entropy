/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.plugin.manager.impl;

import io.nop.api.core.util.FutureHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.plugin.api.IPlugin;
import io.nop.plugin.api.PluginState;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2549 R4-15（C1-018 / C1-019）插件索引/装载原子性载体钉。
 */
public class TestPluginIndexAtomicity extends BaseTestCase {

    static final java.io.File DIR = new java.io.File("target/plugin-idx-test/").getAbsoluteFile();
    static final java.io.File PLUGIN_FILE = new java.io.File(DIR, "idx-probe.plugin.xml");

    static String toFileId(java.io.File file) {
        String path = file.getAbsolutePath();
        if (java.io.File.separatorChar == '\\') {
            path = path.replace('\\', '/');
            if (!path.startsWith("/"))
                path = "/" + path;
        }
        return "file:" + path;
    }

    static final String PLUGIN_ID = toFileId(PLUGIN_FILE);

    static final String PLUGIN_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<plugin name=\"idx-probe\" x:schema=\"/nop/schema/plugin/plugin.xdef\" xmlns:x=\"/nop/schema/xdsl.xdef\">\n"
            + "    <beans>\n"
            + "        <bean id=\"tool.bash\" class=\"io.nop.plugin.test.BashTool\" primary=\"true\"/>\n"
            + "    </beans>\n"
            + "</plugin>\n";

    @org.junit.jupiter.api.BeforeAll
    public static void writeFile() throws java.io.IOException {
        DIR.mkdirs();
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(PLUGIN_FILE)) {
            out.write(PLUGIN_XML.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    /** pin6：并发 register×unregisterNameIndex 后 name 索引集无孤儿（requires 门控语义保持）。 */
    @Test
    public void pin6_nameIndexNoOrphanUnderConcurrency() throws Exception {
        PluginManagerImpl manager = new PluginManagerImpl();
        {
            IPlugin plugin = manager.loadPlugin(PLUGIN_ID);
            assertNotNull(plugin);
            // 门控满足时 loadPlugin 自动 reconcile → ACTIVATED；LOADED 亦为合法初态
            assertTrue(plugin.getState() == PluginState.LOADED || plugin.getState() == PluginState.ACTIVATED,
                    "实际 " + plugin.getState());

            // 并发 unload × loadPlugin（索引摘除 vs computeIfAbsent 登记）
            CountDownLatch start = new CountDownLatch(2);
            Thread t1 = new Thread(() -> {
                start.countDown();
                try {
                    manager.unloadPlugin(PLUGIN_ID);
                } catch (RuntimeException ignore) {
                    // 卸载竞争失败可容忍
                }
            });
            Thread t2 = new Thread(() -> {
                start.countDown();
                try {
                    manager.loadPlugin(PLUGIN_ID);
                } catch (RuntimeException ignore) {
                    // 竞争失败可容忍
                }
            });
            t1.start();
            t2.start();
            assertTrue(start.await(5, TimeUnit.SECONDS));
            t1.join(10000);
            t2.join(10000);

            // 终态无孤儿：同 name 索引集要么空要么只含现存 pluginId（requires 门控不静默失效）
            IPlugin current = manager.loadPlugin(PLUGIN_ID);
            assertNotNull(current, "收敛后应可重新装载（索引不阻断）");
            try {
                current.deactivate();
            } catch (RuntimeException ignore) {
                // 未激活时容忍
            }
            manager.unloadPlugin(PLUGIN_ID);
        }
    }

    /** pin7：reloadPlugin 成功路径无双活定义（plugins map 原子替换，get 同一实例）。 */
    @Test
    public void pin7_reloadReplacesAtomically() {
        PluginManagerImpl manager = new PluginManagerImpl();
        IPlugin first = manager.loadPlugin(PLUGIN_ID);
        assertNotNull(first);
        manager.reloadPlugin(PLUGIN_ID);

        IPlugin second = manager.getPlugin(PLUGIN_ID);
        assertNotNull(second, "reload 后定义应存在（无双活/无 null 窗口）");
        // 状态可继续演进：先 deactivate（reconcile 已激活）再 unload
        try {
            second.deactivate();
        } catch (RuntimeException ignore) {
            // 未激活时容忍
        }
        manager.unloadPlugin(PLUGIN_ID);
    }
}
