/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.java.gen;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.config.IConfigReference;
import io.nop.core.initialize.CoreInitialization;
import io.nop.xlang.backend.EvalBackendObservation;
import io.nop.xlang.backend.EvalBackendRegistry;
import io.nop.xlang.java.backend.JavaEvalExecutionBackend;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static io.nop.xlang.XLangConfigs.CFG_XLANG_EXECUTION_JAVA_BACKEND_REQUIRE_MANIFEST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 双清单文件装载器测试（I11 Phase 2，Phase 1 §4/§5 裁定载体）：行协议解析、多 jar 聚合
 * （getResources 级——双源 URLClassLoader 模拟）、同键异条目/同形路径折叠冲突 fail-fast、
 * 坏行 fail-fast、供给闭环（绿：清单在场 → 双缝填充；缺省：无清单静默空态）、漏跑判别子
 * 红/绿（require-manifest + 清单缺席 → 不可用条目 + 全局 WARN + 指标）。
 */
public class TestGeneratedManifestFiles {

    private static final String FP_A = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private static final String FP_B = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff";

    private static IConfigReference<Boolean> requireSwitch;

    private static Boolean requireBaseline;

    private static ListAppender<ILoggingEvent> appender;

    private static Logger observationLogger;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
        requireSwitch = CFG_XLANG_EXECUTION_JAVA_BACKEND_REQUIRE_MANIFEST;
        requireBaseline = requireSwitch.get();
        observationLogger = (Logger) LoggerFactory.getLogger(EvalBackendObservation.class);
    }

    @AfterAll
    public static void reset() {
        AppConfig.getConfigProvider().updateConfigValue(requireSwitch, requireBaseline);
        GeneratedManifestFiles.clearSupplies();
        CoreInitialization.destroy();
    }

    @BeforeEach
    public void setUp() {
        GeneratedManifestFiles.clearSupplies();
        AppConfig.getConfigProvider().updateConfigValue(requireSwitch, false);
        appender = new ListAppender<>();
        appender.start();
        observationLogger.addAppender(appender);
    }

    @AfterEach
    public void tearDown() {
        observationLogger.detachAppender(appender);
        appender.stop();
        AppConfig.getConfigProvider().updateConfigValue(requireSwitch, false);
        GeneratedManifestFiles.clearSupplies();
    }

    private static Path writeManifestRoot(Path root, String scanList, String generatedClasses) throws Exception {
        Path dir = root.resolve(GeneratedManifestFiles.GENERATED_CLASSES_PATH).getParent();
        Files.createDirectories(dir);
        if (scanList != null)
            Files.writeString(dir.resolve("xlang-java-static-scan.txt"), scanList);
        if (generatedClasses != null)
            Files.writeString(dir.resolve("xlang-java-generated-classes.txt"), generatedClasses);
        return root;
    }

    private static ClassLoader loaderOf(Path... roots) throws Exception {
        URL[] urls = new URL[roots.length];
        for (int i = 0; i < roots.length; i++)
            urls[i] = roots[i].toUri().toURL();
        return new URLClassLoader(urls, null);
    }

    @Test
    public void testScanListAggregationAcrossMultipleSources() throws Exception {
        Path rootA = Files.createTempDirectory("manifest-a");
        Path rootB = Files.createTempDirectory("manifest-b");
        writeManifestRoot(rootA, "/a/u1.xpl\n/a/u2.xpl\n", null);
        writeManifestRoot(rootB, "/b/t.xlib#Tag\n/a/u1.xpl\n", null);
        Set<String> scan = GeneratedManifestFiles.loadScanList(loaderOf(rootA, rootB));
        assertEquals(Set.of("/a/u1.xpl", "/a/u2.xpl", "/b/t.xlib#Tag"), scan,
                "multi-jar aggregation dedups identical keys");
    }

    @Test
    public void testManifestAggregationAndLookup() throws Exception {
        Path rootA = Files.createTempDirectory("manifest-a");
        Path rootB = Files.createTempDirectory("manifest-b");
        writeManifestRoot(rootA, null, "/a/u1.xpl\tio.nop.xlang.gen.Gen__a_u1_xpl\t" + FP_A + "\n");
        writeManifestRoot(rootB, null,
                "/a/u1.xpl\tio.nop.xlang.gen.Gen__a_u1_xpl\t" + FP_A + "\n"
                        + "/b/t.xlib#Tag\tio.nop.xlang.gen.Gen__b_t_xlib_Tag\t" + FP_B + "\n");
        GeneratedClassManifest manifest = GeneratedManifestFiles
                .loadGeneratedClassManifest(loaderOf(rootA, rootB));
        assertEquals(2, manifest.size(), "identical duplicate entries dedup");
        assertEquals("io.nop.xlang.gen.Gen__a_u1_xpl", manifest.find("/a/u1.xpl").getClassName());
        assertEquals(FP_B, manifest.find("/b/t.xlib#Tag").getTreeFingerprint());
        assertNull(manifest.find("/missing"));
    }

    @Test
    public void testConflictingEntriesForSameKeyFailFast() throws Exception {
        Path rootA = Files.createTempDirectory("manifest-a");
        Path rootB = Files.createTempDirectory("manifest-b");
        writeManifestRoot(rootA, null, "/a/u1.xpl\tio.nop.xlang.gen.Gen__a_u1_xpl\t" + FP_A + "\n");
        writeManifestRoot(rootB, null, "/a/u1.xpl\tio.nop.xlang.gen.Gen__a_u1_xpl\t" + FP_B + "\n");
        assertThrows(io.nop.api.core.exceptions.NopException.class,
                () -> GeneratedManifestFiles.loadGeneratedClassManifest(loaderOf(rootA, rootB)),
                "same key with divergent fingerprint must fail fast");
    }

    @Test
    public void testSameFormPathFoldingCollisionFailsFast() throws Exception {
        Path root = Files.createTempDirectory("manifest-f");
        writeManifestRoot(root, null,
                "/a-b.xpl\tio.nop.xlang.gen.Gen__a_b_xpl\t" + FP_A + "\n"
                        + "/a_b.xpl\tio.nop.xlang.gen.Gen__a_b_xpl\t" + FP_B + "\n");
        assertThrows(io.nop.api.core.exceptions.NopException.class,
                () -> GeneratedManifestFiles.loadGeneratedClassManifest(loaderOf(root)),
                "different keys folding to same generated class must fail fast");
    }

    @Test
    public void testMalformedLineFailsFast() throws Exception {
        Path root = Files.createTempDirectory("manifest-bad");
        writeManifestRoot(root, null, "/a/u1.xpl io.nop.xlang.gen.Gen__a_u1_xpl\n");
        assertThrows(io.nop.api.core.exceptions.NopException.class,
                () -> GeneratedManifestFiles.loadGeneratedClassManifest(loaderOf(root)),
                "non-TAB line must fail fast");
    }

    @Test
    public void testInstallSuppliesGreenPathFillsSeams() throws Exception {
        Path root = Files.createTempDirectory("manifest-green");
        writeManifestRoot(root, "/a/u1.xpl\n", "/a/u1.xpl\tio.nop.xlang.gen.Gen__a_u1_xpl\t" + FP_A + "\n");
        boolean found = GeneratedManifestFiles.installSupplies(loaderOf(root));
        assertTrue(found);
        assertTrue(EvalBackendRegistry.instance().findStaticBackend().isStaticCandidate("/a/u1.xpl"));
        assertTrue(JavaEvalExecutionBackend.instance().getStaticScanList().contains("/a/u1.xpl"));
        // require-manifest=true 且清单在场：绿（无不可用条目、无 WARN）
        AppConfig.getConfigProvider().updateConfigValue(requireSwitch, true);
        GeneratedManifestFiles.clearSupplies();
        assertTrue(GeneratedManifestFiles.installSupplies(loaderOf(root)));
        assertTrue(JavaEvalExecutionBackend.instance().isAvailable());
        assertTrue(appender.list.stream().noneMatch(e -> e.getLevel() == Level.WARN));
    }

    @Test
    public void testMissedRunRedPathMarksUnavailableAndWarns() throws Exception {
        AppConfig.getConfigProvider().updateConfigValue(requireSwitch, true);
        // 空 classpath（无清单文件）= 漏跑形态
        ClassLoader empty = new URLClassLoader(new URL[0], null);
        double before = EvalBackendObservation.degradationCount(JavaEvalExecutionBackend.BACKEND_ID,
                EvalBackendObservation.REASON_CODEGEN_PIPELINE_MISSED);
        boolean found = GeneratedManifestFiles.installSupplies(empty);
        assertFalse(found);
        assertFalse(JavaEvalExecutionBackend.instance().isAvailable(), "missed-run must mark backend unavailable");
        assertEquals(EvalBackendObservation.REASON_CODEGEN_PIPELINE_MISSED,
                JavaEvalExecutionBackend.instance().getUnavailableReason());
        assertEquals(before + 1, EvalBackendObservation.degradationCount(JavaEvalExecutionBackend.BACKEND_ID,
                EvalBackendObservation.REASON_CODEGEN_PIPELINE_MISSED));
        assertTrue(appender.list.stream().anyMatch(e -> e.getLevel() == Level.WARN
                        && e.getFormattedMessage().contains(EvalBackendObservation.LOG_MESSAGE_KEY)
                        && e.getFormattedMessage().contains(EvalBackendObservation.REASON_CODEGEN_PIPELINE_MISSED)),
                "global WARN with message key and reason must be emitted");
        // 全部资源走动态路径：扫描清单空 → 非成员 → 动态路径
        assertFalse(EvalBackendRegistry.instance().findStaticBackend().isStaticCandidate("/any.xpl"));
    }

    @Test
    public void testDefaultEmptyStateStaysSilent() throws Exception {
        // 缺省 require=false：清单缺席 = 合法空态（静默——I9/I10 护栏）
        ClassLoader empty = new URLClassLoader(new URL[0], null);
        boolean found = GeneratedManifestFiles.installSupplies(empty);
        assertFalse(found);
        assertTrue(JavaEvalExecutionBackend.instance().isAvailable(), "empty state must stay available and silent");
        assertTrue(appender.list.stream().noneMatch(e -> e.getLevel() == Level.WARN));
        assertFalse(EvalBackendRegistry.instance().findStaticBackend().isStaticCandidate("/any.xpl"));
    }
}
