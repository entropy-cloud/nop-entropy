package io.nop.plugin.manager;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.plugin.api.IPlugin;
import io.nop.plugin.jarplugin.MockPluginStopFail;
import io.nop.plugin.test.MockStopControl;
import io.nop.plugin.manager.classloader.PluginClassLoader;
import io.nop.plugin.manager.impl.PluginManagerImpl;
import io.nop.plugin.test.MockPluginRecorder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_NO_PLUGIN_CLASS_NAME;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * jar 轨 unloadPlugin 失败路径回归测试 + 缺失 plugin.json 显式报错：
 * <ul>
 *     <li>stop 抛错时 classLoader 不得关闭、holder 不得残留为不可用状态——修复前
 *     finally 无条件关闭 classLoader，holder 仍留在 map（computeIfAbsent 阻断重载），
 *     插件进入"僵尸"状态且 unload 不可重试。</li>
 *     <li>uber jar 无 /nop/plugin.json 时修复前以 PluginClassLoader 自身作为插件类，
 *     loadPlugin 的 (IPlugin) 强转抛指向性不明的 CCE；修复后抛 ERR_PLUGIN_NO_PLUGIN_CLASS_NAME。</li>
 * </ul>
 */
public class TestPluginUnloadFailure {

    private static final String JAR_COORDS = "io.nop.plugin.test:mock-plugin:1.0.0";

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private static URL buildPluginJar(boolean withPluginJson, String... classNames) throws IOException {
        MockPluginRecorder.reset();
        File dir = new File("target/plugin-test/").getAbsoluteFile();
        dir.mkdirs();
        File jarFile = File.createTempFile("plugin-test-", ".jar", dir);
        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(jarFile))) {
            if (withPluginJson) {
                out.putNextEntry(new JarEntry("nop/plugin.json"));
                String json = "{\"pluginClassName\":\"" + classNames[0]
                        + "\",\"importPackages\":[\"io.nop.plugin.test\"]}";
                out.write(json.getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }

            for (String className : classNames) {
                String classPath = className.replace('.', '/') + ".class";
                try (InputStream in = TestPluginUnloadFailure.class.getClassLoader().getResourceAsStream(classPath)) {
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

    private static PluginManagerImpl newManager(URL jarUrl) {
        PluginManagerImpl manager = new PluginManagerImpl();
        manager.setResourceResolver(coords -> List.of(jarUrl));
        manager.setPluginConfigProvider(coords -> Collections.emptyMap());
        return manager;
    }

    /**
     * 经反射读取 holder 的 classLoader（plugins map 为私有实现细节，无公开访问器）。
     */
    private static PluginClassLoader classLoaderOf(PluginManagerImpl manager, String pluginId) throws Exception {
        Field pluginsField = PluginManagerImpl.class.getDeclaredField("plugins");
        pluginsField.setAccessible(true);
        Map<?, ?> plugins = (Map<?, ?>) pluginsField.get(manager);
        Object holder = plugins.get(pluginId);
        if (holder == null)
            return null;
        Field clField = holder.getClass().getDeclaredField("classLoader");
        clField.setAccessible(true);
        return (PluginClassLoader) clField.get(holder);
    }

    @Test
    public void testUnloadFailureKeepsClassLoaderUsableAndRetryable() throws IOException {
        URL jar = buildPluginJar(true, "io.nop.plugin.jarplugin.MockPluginStopFail",
                "io.nop.plugin.jarplugin.MockPlugin");
        PluginManagerImpl manager = newManager(jar);

        IPlugin plugin = manager.loadPlugin(JAR_COORDS);
        assertTrue(MockPluginRecorder.started, "jar 轨兼容路径 loadPlugin 必须调用 start");

        PluginClassLoader classLoader = assertDoesNotThrow(() -> classLoaderOf(manager, JAR_COORDS));

        // stop 抛错：unloadPlugin 失败，但 classLoader 不得被关闭（修复前 finally 无条件关闭）
        MockStopControl.failStop = true;
        try {
            assertThrows(RuntimeException.class, () -> manager.unloadPlugin(JAR_COORDS),
                    "stop 失败必须上抛");
            assertFalse(classLoader.isClosed(),
                    "unload 失败路径不得关闭 classLoader（关闭后插件进入不可恢复僵尸状态）");
            assertEquals(plugin, manager.getPlugin(JAR_COORDS), "失败路径 holder 保留，unload 可重试");

            // 修复失败原因后重试 unload：成功且 classLoader 关闭
            MockStopControl.failStop = false;
            manager.unloadPlugin(JAR_COORDS);
            assertTrue(classLoader.isClosed(), "成功 unload 后 classLoader 正常关闭");
            assertFalse(manager.getLoadedPlugins().contains(plugin), "成功 unload 后 holder 移除");
            assertTrue(MockPluginRecorder.stopped, "重试 unload 时 stop 真实被调用");
        } finally {
            MockStopControl.failStop = false;
        }
    }

    @Test
    public void testMissingPluginJsonFailsWithExplicitError() throws IOException {
        // jar 内只有类字节、无 /nop/plugin.json
        URL jar = buildPluginJar(false, "io.nop.plugin.jarplugin.MockPlugin");
        PluginManagerImpl manager = newManager(jar);

        // 修复前：以 PluginClassLoader 自身作为插件类，(IPlugin) 强转抛 ClassCastException
        NopException e = assertThrows(NopException.class, () -> manager.loadPlugin(JAR_COORDS));
        assertEquals(ERR_PLUGIN_NO_PLUGIN_CLASS_NAME.getErrorCode(), e.getErrorCode(),
                "缺失 plugin.json 必须抛明确错误码（不抛裸 CCE）");
        assertTrue(manager.getLoadedPlugins().isEmpty(), "失败装载不得留下 entry");
    }
}
