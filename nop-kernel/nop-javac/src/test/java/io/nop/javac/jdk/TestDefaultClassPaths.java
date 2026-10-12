package io.nop.javac.jdk;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI plan 2450：getDefaultClassPaths 的环境探测语义钉扎。
 *
 * <p>exec:java 场景（TCCL=realm URLClassLoader）必须收集 realm URL——chronic
 * gen-aop-proxy-fail 的根因就是只读 java.class.path（仅 maven-wrapper.jar）；
 * surefire/IDE 场景（TCCL=AppClassLoader，JDK 9+ 非 URLClassLoader）必须回退
 * java.class.path（旧行为）。
 */
public class TestDefaultClassPaths {

    @Test
    public void testCollectFromTcclUrlClassLoader() throws Exception {
        File jar = Files.createTempFile("probe-lib", ".jar").toFile();
        File dir = Files.createTempDirectory("probe-classes").toFile();
        jar.deleteOnExit();

        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            URLClassLoader realm = new URLClassLoader(
                    new URL[]{jar.toURI().toURL(), dir.toURI().toURL()}, old);
            Thread.currentThread().setContextClassLoader(realm);

            List<String> paths = JdkJavaCompiler.getDefaultClassPaths();

            // 文件 URL 与目录 URL 都被收集（目录是 exec:java 下最常见形态），且排在回退项之前
            int jarIdx = paths.indexOf(jar.getAbsolutePath());
            int dirIdx = paths.indexOf(dir.getAbsolutePath());
            assertTrue(jarIdx >= 0, "jar URL 应被收集: " + paths);
            assertTrue(dirIdx >= 0, "目录 URL 应被收集: " + paths);
            // 回退项 = java.class.path 的分段；收集项应在其之前
            String firstFallback = firstSegment(System.getProperty("java.class.path"));
            int fallbackIdx = paths.indexOf(firstFallback);
            assertTrue(fallbackIdx >= 0, "回退分段应仍在: " + paths);
            assertTrue(jarIdx < fallbackIdx, "收集项应先于回退分段");
            // TCCL 在父链最前，其 URL 顺序保持
            assertTrue(jarIdx < dirIdx, "收集顺序保持 TCCL getURLs 顺序");
        } finally {
            Thread.currentThread().setContextClassLoader(old);
            Files.deleteIfExists(jar.toPath());
        }
    }

    @Test
    public void testFallbackWhenTcclNotUrlClassLoader() {
        // surefire 下 TCCL 即 AppClassLoader（非 URLClassLoader），直接断言回退语义；
        // 不人为换 TCCL，避免构造非 URLClassLoader 加载器的兼容性问题
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        if (tccl instanceof URLClassLoader)
            return; // 环境变异（某些 runner 用 URLClassLoader 做 TCCL）：回退断言不适用

        List<String> paths = JdkJavaCompiler.getDefaultClassPaths();
        String classPath = System.getProperty("java.class.path");
        // 回退必须与旧行为逐位一致：同用 StringHelper.split（保留尾部空段语义）
        List<String> expected = io.nop.commons.util.StringHelper.split(classPath, java.io.File.pathSeparatorChar);
        assertEquals(expected, paths);
    }

    private static String firstSegment(String classPath) {
        int idx = classPath.indexOf(java.io.File.pathSeparator);
        return idx < 0 ? classPath : classPath.substring(0, idx);
    }
}
