package io.nop.plugin.manager.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.plugin.api.IPlugin;
import io.nop.plugin.manager.classloader.PluginClassLoader;
import io.nop.plugin.test.MockPluginRecorder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_NO_PLUGIN_CLASS_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2506 2.2-C6-001 回归（C10-006 成员面）：jar 轨 loadPluginFromJar「映射建立前失败」
 * 路径（PluginClassLoader 构造期 loadPluginConfig 缺 plugin.json / loadPlugin 类加载失败）
 * 必须确定性释放 lambda 局部 PluginClassLoader 与 JAR 句柄；重试不累积。
 *
 * <p>断言缝 = 设计裁定③的 package-private {@code newPluginClassLoader} 工厂钩子（本测试类落
 * impl 同包覆写捕获实例）——映射前失败不建映射，既有 {@code classLoaderOf} holder 反射探针恒
 * null 不可用；该探针继续服务 N2「映射后失败」腿（TestPluginUnloadFailure 零回改）。
 *
 * <p>红形态（修复前 / 变异：移除 finally 的失败路径释放）：捕获实例 {@code isClosed()==false}
 * 且重试实例数线性增长、均未关闭。绿形态：每次捕获实例均已关闭；成功路径实例由 holder 持有
 * 不释放（仅失败路径释放），unloadPlugin 后才关闭。
 */
public class TestPluginLoadFailureResourceRelease {

    private static final String JAR_COORDS = "io.nop.plugin.test:release-probe:1.0.0";

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    /** 捕获经工厂钩子构造的全部 PluginClassLoader 实例（生产行为零变化：方法体原样委托） */
    static class CapturingPluginManager extends PluginManagerImpl {
        final List<PluginClassLoader> created = new ArrayList<>();

        @Override
        PluginClassLoader newPluginClassLoader(List<URL> urls) {
            PluginClassLoader cl = super.newPluginClassLoader(urls);
            created.add(cl);
            return cl;
        }
    }

    private static URL buildPluginJar(boolean withPluginJson, String pluginClassName,
                                      String... bundledClassNames) throws IOException {
        MockPluginRecorder.reset();
        File dir = new File("target/plugin-test/").getAbsoluteFile();
        dir.mkdirs();
        File jarFile = File.createTempFile("release-probe-", ".jar", dir);
        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(jarFile))) {
            if (withPluginJson) {
                out.putNextEntry(new JarEntry("nop/plugin.json"));
                String json = "{\"pluginClassName\":\"" + pluginClassName
                        + "\",\"importPackages\":[\"io.nop.plugin.test\"]}";
                out.write(json.getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
            for (String className : bundledClassNames) {
                String classPath = className.replace('.', '/') + ".class";
                try (InputStream in = TestPluginLoadFailureResourceRelease.class.getClassLoader()
                        .getResourceAsStream(classPath)) {
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

    private static CapturingPluginManager newManager(URL jarUrl) {
        CapturingPluginManager manager = new CapturingPluginManager();
        manager.setResourceResolver(coords -> List.of(jarUrl));
        manager.setPluginConfigProvider(coords -> Collections.emptyMap());
        return manager;
    }

    /**
     * 映射前失败点①：PluginClassLoader 构造期 loadPluginConfig 缺 plugin.json
     * （ERR_PLUGIN_NO_PLUGIN_CLASS_NAME）→ 装载失败、无 entry。构造抛错窗的句柄自清理由
     * 构造器自身承载（实例在钩子返回前即不可达，工厂捕获不可行）——直测见
     * {@code TestPluginClassLoaderConstructorCleanup}；本用例守住 manager 层错误码与零残留。
     */
    @Test
    @Timeout(60)
    public void testMissingPluginJsonReleasesClassLoader() throws IOException {
        URL jar = buildPluginJar(false, null, "io.nop.plugin.jarplugin.MockPlugin");
        CapturingPluginManager manager = newManager(jar);

        NopException e = assertThrows(NopException.class, () -> manager.loadPlugin(JAR_COORDS));
        assertEquals(ERR_PLUGIN_NO_PLUGIN_CLASS_NAME.getErrorCode(), e.getErrorCode(), "错误码与传播不变");
        assertTrue(manager.created.isEmpty(),
                "构造期抛错发生在钩子返回之前（自清理由 PluginClassLoader 构造器承载）");
        assertTrue(manager.getLoadedPlugins().isEmpty(), "失败装载不建映射");
    }

    /**
     * 映射前失败点②：plugin.json 在位但 pluginClassName 指向 jar 内不存在的类
     * （loadPlugin 类加载失败）→ 捕获实例已关闭。
     */
    @Test
    @Timeout(60)
    public void testPluginClassLoadFailureReleasesClassLoader() throws IOException {
        URL jar = buildPluginJar(true, "io.nop.plugin.jarplugin.NoSuchPluginClass");
        CapturingPluginManager manager = newManager(jar);

        assertThrows(RuntimeException.class, () -> manager.loadPlugin(JAR_COORDS), "类加载失败必须上抛（不静默）");
        assertEquals(1, manager.created.size());
        assertTrue(manager.created.get(0).isClosed(), "loadPlugin 失败点纳入同一 finally 释放");
        assertTrue(manager.getLoadedPlugins().isEmpty());
    }

    /**
     * 重试不累积：坏包（plugin.json 在位但 pluginClassName 指向 jar 内不存在的类，失败点 =
     * 构造后的 loadPlugin）被周期性重试 N 次 → N 个实例均已关闭（修复前：N 个实例均滞留
     * 未关闭，句柄数与 Metaspace 线性累积）。构造期抛错窗的逐次自清理见
     * {@code TestPluginClassLoaderConstructorCleanup#testRepeatedConstructorFailureClosesEveryAttempt}。
     */
    @Test
    @Timeout(60)
    public void testRepeatedLoadFailureDoesNotAccumulateOpenClassLoaders() throws IOException {
        URL jar = buildPluginJar(true, "io.nop.plugin.jarplugin.NoSuchPluginClass");
        CapturingPluginManager manager = newManager(jar);

        int attempts = 5;
        for (int i = 0; i < attempts; i++) {
            assertThrows(RuntimeException.class, () -> manager.loadPlugin(JAR_COORDS));
        }
        assertEquals(attempts, manager.created.size(), "每次重试重建全新 classLoader（不建映射）");
        for (PluginClassLoader cl : manager.created) {
            assertTrue(cl.isClosed(), "每个失败实例均已释放，零累积");
        }
        assertTrue(manager.getLoadedPlugins().isEmpty());
    }

    /**
     * 仅失败路径释放：成功装载的 classLoader 归 holder 持有、不得被 finally 关闭
     * （无条件释放会关闭在用 classLoader）；unloadPlugin 成功路径后才关闭。
     */
    @Test
    @Timeout(60)
    public void testSuccessPathKeepsClassLoaderOpenUntilUnload() throws IOException {
        URL jar = buildPluginJar(true, "io.nop.plugin.jarplugin.MockPlugin",
                "io.nop.plugin.jarplugin.MockPlugin");
        CapturingPluginManager manager = newManager(jar);

        IPlugin plugin = manager.loadPlugin(JAR_COORDS);
        assertNotNull(plugin);
        assertEquals(1, manager.created.size());
        PluginClassLoader cl = manager.created.get(0);
        assertFalse(cl.isClosed(), "成功路径 classLoader 由 holder 持有，finally 不得释放");
        assertEquals(plugin, manager.getPlugin(JAR_COORDS), "holder 已建映射");

        manager.unloadPlugin(JAR_COORDS);
        assertTrue(cl.isClosed(), "unloadPlugin 成功路径关闭 classLoader（既有语义不变）");
        assertTrue(manager.getLoadedPlugins().isEmpty());
    }
}
