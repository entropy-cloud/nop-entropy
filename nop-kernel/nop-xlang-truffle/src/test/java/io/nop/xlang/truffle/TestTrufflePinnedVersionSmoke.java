package io.nop.xlang.truffle;

import com.oracle.truffle.api.TruffleLanguage;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 钉版冒烟复核（roadmap I5 / 设计 truffle 02 §二 Q5 配方钉死，自动化形态）：
 *
 * <ul>
 * <li>truffle-api 代表 class（{@code com.oracle.truffle.api.TruffleLanguage}）class major version 期望 61（Java 17）；</li>
 * <li>truffle-api 与 polyglot 两坐标解析 jar 的 {@code META-INF/versions} 目录列表期望仅 {@code versions/9}
 *     与 {@code versions/21}（或无 overlay 目录）——保证 stock JDK 21 基线兼容不被钉线内版本破坏；</li>
 * <li>truffle-dsl-processor 不在运行时 classpath（annotationProcessorPaths 构建期专用），其 jar 级检查为
 *     构建期人工口径（本 plan 执行时 25.2.4 实测：无 META-INF/versions overlay，记录见当日 log），
 *     本测试改以 dsl-processor 产物（provider 服务文件）在 Phase 2 起由
 *     {@code TestXLangLanguageRegistration} 验证。</li>
 * </ul>
 *
 * <p>若钉 25.x 线内更新版本，本测试随构建自动重跑同口径检查（配方不漂移）。
 */
public class TestTrufflePinnedVersionSmoke {

    @Test
    public void testTruffleApiClassMajorVersion() throws IOException {
        JarFile jar = jarOf(TruffleLanguage.class);
        JarEntry entry = jar.getJarEntry("com/oracle/truffle/api/TruffleLanguage.class");
        assertNotNull(entry, "TruffleLanguage.class must exist in truffle-api jar");
        int major = classMajor(jar, entry);
        assertEquals(61, major, "truffle-api TruffleLanguage.class must be class major 61 (Java 17), jar=" + jar.getName());
        jar.close();
    }

    @Test
    public void testMultiReleaseOverlayVersions() throws IOException {
        assertOverlay(jarOf(TruffleLanguage.class));
        assertOverlay(jarOf(org.graalvm.polyglot.Context.class));
    }

    /**
     * 空 Context + Engine 构建在 stock JDK 21 可运行（语言注册发现归 Phase 2 的
     * {@code TestXLangLanguageRegistration}）。
     */
    @Test
    public void testEmptyEngineAndContextBootOnStockJdk() {
        Engine engine = Engine.create();
        try {
            assertNotNull(engine);
            try (Context context = Context.newBuilder().engine(engine).build()) {
                assertNotNull(context);
            }
        } finally {
            engine.close();
        }
    }

    private static void assertOverlay(JarFile jar) throws IOException {
        List<String> versionDirs = new ArrayList<>();
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            String name = entries.nextElement().getName();
            if (name.startsWith("META-INF/versions/") && name.length() > "META-INF/versions/".length()) {
                String rest = name.substring("META-INF/versions/".length());
                int slash = rest.indexOf('/');
                if (slash > 0) {
                    String dir = rest.substring(0, slash);
                    if (!versionDirs.contains(dir))
                        versionDirs.add(dir);
                }
            }
        }
        for (String dir : versionDirs)
            assertTrue(dir.equals("9") || dir.equals("21"),
                    "unexpected multi-release overlay version directory " + dir + " in jar " + jar.getName()
                            + " (only 9 and 21 allowed for JDK 21 baseline)");
        jar.close();
    }

    private static JarFile jarOf(Class<?> markerClass) throws IOException {
        Path jarPath = classpathJarPath(markerClass);
        return new JarFile(jarPath.toFile());
    }

    static Path classpathJarPath(Class<?> markerClass) {
        String codeSource = markerClass.getProtectionDomain().getCodeSource().getLocation().getFile();
        assertTrue(codeSource.endsWith(".jar"), "expected jar classpath entry, was: " + codeSource);
        String decoded = java.net.URLDecoder.decode(codeSource, StandardCharsets.UTF_8);
        return Paths.get(decoded);
    }

    private static int classMajor(JarFile jar, JarEntry entry) throws IOException {
        try (InputStream in = jar.getInputStream(entry)) {
            byte[] header = in.readNBytes(8);
            assertEquals(0xCA, header[0] & 0xFF, "magic");
            assertEquals(0xFE, header[1] & 0xFF, "magic");
            assertEquals(0xBA, header[2] & 0xFF, "magic");
            assertEquals(0xBE, header[3] & 0xFF, "magic");
            return ((header[6] & 0xFF) << 8) | (header[7] & 0xFF);
        }
    }
}
