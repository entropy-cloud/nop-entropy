package io.nop.plugin.manager.classloader;

import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_NO_PLUGIN_CLASS_NAME;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * plan 2506 2.2-C6-001（映射前失败点①）：PluginClassLoader 构造期 loadPluginConfig 失败
 * （plugin.json 缺失/非法，经 getResource 触发 jar 打开后抛错）必须自清理已注册的 jar 资源
 * 句柄——实例不可达后调用方无法代为 close，不自清理则坏包重试时句柄线性累积。
 *
 * <p>观测方式：同包子类覆写 loadPluginConfig 注入失败、覆写 close() 记录调用（静态旗标）——
 * 构造抛错后实例不可达，实例级断言不可行；close() 为虚调用，自清理路径经记录旗标可观测。
 * 成功构造路径以带合法 plugin.json 的临时 jar 直构基类验证不自清理。
 */
public class TestPluginClassLoaderConstructorCleanup {

    /** 构造期 loadPluginConfig 抛错的探针类加载器（记录 close 是否被自清理路径调用） */
    static class ThrowingConfigClassLoader extends PluginClassLoader {
        static final AtomicInteger closeCalls = new AtomicInteger();

        ThrowingConfigClassLoader(URL[] urls, ClassLoader importClassLoader) {
            super(urls, importClassLoader);
        }

        @Override
        PluginConfig loadPluginConfig() {
            throw new NopException(ERR_PLUGIN_NO_PLUGIN_CLASS_NAME);
        }

        @Override
        public void close() {
            closeCalls.incrementAndGet();
        }
    }

    private static URL probeUrl() {
        try {
            return Paths.get("target", "plugin-test").toAbsolutePath().toUri().toURL();
        } catch (Exception e) {
            throw new IllegalStateException("probe url build fail", e);
        }
    }

    private static URL validPluginJar() throws IOException {
        File dir = new File("target/plugin-test/").getAbsoluteFile();
        dir.mkdirs();
        File jarFile = File.createTempFile("ctor-cleanup-probe-", ".jar", dir);
        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(jarFile))) {
            out.putNextEntry(new JarEntry("nop/plugin.json"));
            out.write(("{\"pluginClassName\":\"io.nop.plugin.jarplugin.MockPlugin\","
                    + "\"importPackages\":[\"io.nop.plugin.test\"]}").getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        return jarFile.toURI().toURL();
    }

    @Test
    @Timeout(60)
    public void testConstructorFailureClosesJarHandlesBeforeRethrow() {
        URL url = probeUrl();
        int before = ThrowingConfigClassLoader.closeCalls.get();

        NopException e = assertThrows(NopException.class,
                () -> new ThrowingConfigClassLoader(new URL[]{url},
                        TestPluginClassLoaderConstructorCleanup.class.getClassLoader()),
                "构造期失败必须原样重抛（错误码语义不变）");
        assertEquals(ERR_PLUGIN_NO_PLUGIN_CLASS_NAME.getErrorCode(), e.getErrorCode());

        assertEquals(before + 1, ThrowingConfigClassLoader.closeCalls.get(),
                "构造期失败必须自清理（close 被调用后重抛）——实例不可达后无调用方能代为释放");
    }

    @Test
    @Timeout(60)
    public void testRepeatedConstructorFailureClosesEveryAttempt() {
        URL url = probeUrl();
        int before = ThrowingConfigClassLoader.closeCalls.get();
        int attempts = 5;

        for (int i = 0; i < attempts; i++) {
            assertThrows(NopException.class,
                    () -> new ThrowingConfigClassLoader(new URL[]{url},
                            TestPluginClassLoaderConstructorCleanup.class.getClassLoader()));
        }

        assertEquals(before + attempts, ThrowingConfigClassLoader.closeCalls.get(),
                "每次失败构造均自清理，坏包重试零句柄累积");
    }

    @Test
    @Timeout(60)
    public void testSuccessfulConstructionDoesNotSelfClose() throws IOException {
        URL jar = validPluginJar();
        int before = ThrowingConfigClassLoader.closeCalls.get();

        PluginClassLoader cl = assertDoesNotThrow(
                () -> new PluginClassLoader(new URL[]{jar},
                        TestPluginClassLoaderConstructorCleanup.class.getClassLoader()),
                "带合法 plugin.json 的构造必须成功");

        assertEquals(before, ThrowingConfigClassLoader.closeCalls.get(),
                "成功构造不得触发自清理路径（close 仅由显式调用发生）");
        cl.close();
    }
}
