/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.java.gen.task;

import io.nop.commons.util.FileHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.xlang.java.gen.task.XlangJavaGenTask.GenerationResult;
import io.nop.xlang.java.gen.task.XlangJavaGenTask.GeneratedUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 构建任务测试（I11 Phase 2，Phase 1 §2/§3/§8 裁定载体）：扫描口径过滤（纳入/排除/`_delta`）、
 * 产物存在性与形态（生成源码 + 双清单 + native reflect 配置）、重生成幂等（write-if-changed
 * 二轮零变更 + check 模式干净）、漂移检测（内容漂移 + 陈旧多余产物）、同形路径折叠唯一性
 * fail-fast（原子性：失败不写盘）。xpl 单元走干净编译（InMemoryTextResource——不依赖 VFS）；
 * xlib 生成链经 {@code TestTagUnitGeneration}/{@code TestTagFunctionBinding} 与 fixture 模块
 * e2e 承载。
 */
public class TestXlangJavaGenTask {

    @TempDir
    static File tempDir;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void reset() {
        CoreInitialization.destroy();
    }

    private static File newProject(String name, String... xplUnits) throws Exception {
        File project = new File(tempDir, name);
        File vfs = new File(project, "src/main/resources/_vfs/test/" + name);
        vfs.mkdirs();
        for (int i = 0; i < xplUnits.length; i += 2)
            Files.writeString(vfs.toPath().resolve(xplUnits[i]), xplUnits[i + 1]);
        return project;
    }

    private static final String EXPR_XPL = "<c:script>1 + 2</c:script>";

    // ---- 扫描口径过滤 ----

    @Test
    public void testScanFiltersByCensusScope() throws Exception {
        File project = newProject("scan");
        File vfs = new File(project, "src/main/resources/_vfs/test/scan");
        Files.writeString(vfs.toPath().resolve("a.xpl"), EXPR_XPL);
        Files.writeString(vfs.toPath().resolve("b.xlib"), "<lib/>");
        Files.writeString(vfs.toPath().resolve("c.xgen"), EXPR_XPL);
        Files.writeString(vfs.toPath().resolve("d.xrun"), EXPR_XPL);
        Files.writeString(vfs.toPath().resolve("e.xtask"), EXPR_XPL);
        Files.writeString(vfs.toPath().resolve("f.xmeta"), "<meta/>");
        File delta = new File(vfs, "_delta/x");
        delta.mkdirs();
        Files.writeString(delta.toPath().resolve("g.xpl"), EXPR_XPL);

        Map<String, File> included = new java.util.TreeMap<>();
        Map<String, Integer> excluded = new java.util.TreeMap<>();
        XlangJavaGenTask.scanVfs(new File(project, "src/main/resources/_vfs"), "", included, excluded);

        assertTrue(included.containsKey("/test/scan/a.xpl"), "xpl must be included");
        assertTrue(included.containsKey("/test/scan/b.xlib"), "xlib must be included");
        assertFalse(included.containsKey("/test/scan/c.xgen"), "xgen must be excluded (build-time only)");
        assertFalse(included.containsKey("/test/scan/d.xrun"), "xrun must be excluded (build-time only)");
        assertFalse(included.containsKey("/test/scan/e.xtask"), "xtask must be excluded (first landing)");
        assertFalse(included.containsKey("/test/scan/f.xmeta"), "non-executable xdsl must be excluded");
        assertFalse(included.containsKey("/test/scan/_delta/x/g.xpl"),
                "_delta subtree must be excluded (base-tree only)");
        // 排除类型显式清点（不静默）
        assertEquals(1, excluded.get("xgen"));
        assertEquals(1, excluded.get("xrun"));
        assertEquals(1, excluded.get("xtask"));
        assertEquals(1, excluded.get("xmeta"));
        assertEquals(0, excluded.getOrDefault("xpl", 0));
        assertEquals(0, excluded.getOrDefault("xlib", 0));
    }

    // ---- 产物存在性与形态 ----

    @Test
    public void testGenerateProducesAllProducts() throws Exception {
        File project = newProject("gen", "expr.xpl", EXPR_XPL, "tpl.xpl", "<c:unit>hi ${1 + 1}</c:unit>");
        Files.writeString(project.toPath().resolve("pom.xml"),
                "<project><groupId>io.github.entropy-cloud</groupId><artifactId>test-gen</artifactId></project>");
        GenerationResult result = XlangJavaGenTask.generate(project, false);
        assertEquals(2, result.getUnits().size());

        File genDir = new File(project, XlangJavaGenTask.GEN_SOURCE_DIR);
        for (GeneratedUnit unit : result.getUnits()) {
            File src = new File(genDir, unit.getClassName()
                    .substring(unit.getClassName().lastIndexOf('.') + 1) + ".java");
            assertTrue(src.isFile(), "generated source must exist: " + src);
            assertEquals(unit.getCode(), FileHelper.readText(src, null));
            assertTrue(unit.getCode().contains("// source: " + unit.getKey()));
        }

        File manifestDir = new File(project, XlangJavaGenTask.MANIFEST_DIR);
        File scanList = new File(manifestDir, "xlang-java-static-scan.txt");
        File manifest = new File(manifestDir, "xlang-java-generated-classes.txt");
        assertTrue(scanList.isFile() && manifest.isFile());
        String scanText = FileHelper.readText(scanList, null);
        assertTrue(scanText.contains("/test/gen/expr.xpl"));
        assertTrue(scanText.contains("/test/gen/tpl.xpl"));
        assertTrue(scanText.indexOf("/test/gen/expr.xpl") < scanText.indexOf("/test/gen/tpl.xpl"),
                "scan list must be sorted");
        for (GeneratedUnit unit : result.getUnits())
            assertTrue(FileHelper.readText(manifest, null).contains(
                    unit.getKey() + "\t" + unit.getClassName() + "\t" + unit.getTreeFingerprint()));

        File reflect = new File(project,
                "src/main/resources/META-INF/native-image/io.github.entropy-cloud/test-gen/reflect-config.json");
        assertTrue(reflect.isFile(), "native reflect config must be a task product");
        String reflectText = FileHelper.readText(reflect, null);
        assertTrue(reflectText.contains("allPublicMethods"), "reflect entries must enable public methods");
        for (GeneratedUnit unit : result.getUnits())
            assertTrue(reflectText.contains(unit.getClassName()));
    }

    // ---- 重生成幂等（验收第三项的单元载体；e2e 复跑归 fixture 模块） ----

    @Test
    public void testRegenerationIsIdempotent() throws Exception {
        File project = newProject("idem", "expr.xpl", EXPR_XPL);
        GenerationResult first = XlangJavaGenTask.generate(project, false);
        assertFalse(first.getDrifts().isEmpty(), "first run writes products (drifts = writes)");
        GenerationResult second = XlangJavaGenTask.generate(project, false);
        assertTrue(second.getDrifts().isEmpty(), "second run must be write-free (byte-identical products)");
        GenerationResult check = XlangJavaGenTask.generate(project, true);
        assertTrue(check.isClean(), "check mode must report clean after generation");
    }

    @Test
    public void testCheckModeDetectsDriftAndStaleProducts() throws Exception {
        File project = newProject("drift", "expr.xpl", EXPR_XPL);
        XlangJavaGenTask.generate(project, false);

        // 内容漂移
        File genDir = new File(project, XlangJavaGenTask.GEN_SOURCE_DIR);
        File[] genFiles = genDir.listFiles();
        File victim = genFiles[0];
        Files.writeString(victim.toPath(), FileHelper.readText(victim, null) + "\n// hand edit\n");
        assertTrue(XlangJavaGenTask.generate(project, true).getDrifts().size() == 1,
                "content drift must be detected");

        // 陈旧多余产物
        XlangJavaGenTask.generate(project, false);
        Files.writeString(new File(genDir, "Gen__stale_leftover.java").toPath(), "// stale\n");
        assertTrue(XlangJavaGenTask.generate(project, true).getDrifts().size() == 1,
                "stale product must be detected");
    }

    // ---- 同形路径唯一性 fail-fast（原子性：不写盘） ----

    @Test
    public void testSameFormPathFoldingFailsFastWithoutWriting() throws Exception {
        File project = newProject("fold", "a-b.xpl", EXPR_XPL, "a_b.xpl", EXPR_XPL);
        assertThrows(IllegalStateException.class, () -> XlangJavaGenTask.generate(project, false),
                "folding collision must fail fast");
        File genDir = new File(project, XlangJavaGenTask.GEN_SOURCE_DIR);
        assertFalse(genDir.isDirectory() && genDir.listFiles() != null && genDir.listFiles().length > 0,
                "failed generation must not leave partial products");
    }

    // ---- 指纹-转译次序硬规则（S8）：清单指纹 = 先于转译取（translate 会 force-compile 惰性载荷） ----

    @Test
    public void testManifestFingerprintStableAcrossRuns() throws Exception {
        // 两个独立工程同 VFS 标准路径（指纹混合源位置 path——同路径同内容必同指纹、同生成码）
        File p1 = new File(tempDir, "order1");
        File p2 = new File(tempDir, "order2");
        for (File p : new File[]{p1, p2}) {
            File vfs = new File(p, "src/main/resources/_vfs/test/order");
            vfs.mkdirs();
            Files.writeString(vfs.toPath().resolve("expr.xpl"), EXPR_XPL);
        }
        GenerationResult r1 = XlangJavaGenTask.generate(p1, false);
        GenerationResult r2 = XlangJavaGenTask.generate(p2, false);
        assertEquals(1, r1.getUnits().size());
        assertEquals(r1.getUnits().get(0).getTreeFingerprint(), r2.getUnits().get(0).getTreeFingerprint(),
                "identical source at identical std path must fingerprint identically");
        assertEquals(r1.getUnits().get(0).getCode(), r2.getUnits().get(0).getCode(),
                "generated code must be identical for identical input (regeneration idempotence across projects)");
    }
}
