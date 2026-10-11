package io.nop.code.service.eval;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.code.dao.entity.NopCodeDependency;
import io.nop.code.service.api.ICodeIndexService;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * N8.1 影响分析评测（真实链路，P1-7.4-002 / plan 2402）：变更文件经真实
 * {@code CodeIndexService.triggerIncrementalIndex} → 生产 {@code computeAffectedFiles}
 * （{@code nop_code_dependency} 双向 2-hop）产出 affected 集，再与 ground-truth 对比算 F1。
 * <p>
 * 与旧版（测试内自实现 BFS + 自定预期）不同，本评测的 actual 完全来自生产链路，
 * 因此能检出传播方向/深度回归：change A 场景下反向-only 实现会返回空集（F1=0）。
 * <p>
 * <b>边界</b>：依赖边（A→B、B→C）由测试手工播种到 `nop_code_dependency`；本评测验证生产
 * 传播算法与声明面，<b>不</b>验证 DAO 查询的方向性/import resolver 的建边行为。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.FALSE)
public class ImpactAnalysisF1EvalTest extends JunitAutoTestCase {

    @Inject
    ICodeIndexService codeIndexService;

    @Inject
    IDaoProvider daoProvider;

    @TempDir
    Path tempDir;

    private static final String C_V1 = """
            package demo;

            public class C {
                public String leaf(String s) { return s; }
            }
            """;

    private static final String C_V2 = """
            package demo;

            public class C {
                public String leaf(String s) { return s.trim(); }
            }
            """;

    private static final String B_JAVA = """
            package demo;

            public class B {
                private C c = new C();

                public String mid(String s) { return c.leaf(s); }
            }
            """;

    private static final String A_V1 = """
            package demo;

            public class A {
                private B b = new B();

                public String top(String s) { return b.mid(s); }
            }
            """;

    private static final String A_V2 = """
            package demo;

            public class A {
                private B b = new B();

                public String top(String s) { return b.mid(s) + "!"; }
            }
            """;

    private static void writeJavaFile(Path dir, String name, String content) throws Exception {
        Path file = dir.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private Path writeProject(String cContent, String aContent) throws Exception {
        Path src = tempDir.resolve("src-" + System.nanoTime());
        writeJavaFile(src.resolve("demo"), "C.java", cContent);
        writeJavaFile(src.resolve("demo"), "B.java", B_JAVA);
        writeJavaFile(src.resolve("demo"), "A.java", aContent);
        return src;
    }

    private void seedDependencyGraph(String indexId) {
        IEntityDao<NopCodeDependency> depDao = daoProvider.daoFor(NopCodeDependency.class);
        NopCodeDependency bToC = depDao.newEntity();
        bToC.setIndexId(indexId);
        bToC.setSourceFilePath("demo/B.java");
        bToC.setTargetFilePath("demo/C.java");
        NopCodeDependency aToB = depDao.newEntity();
        aToB.setIndexId(indexId);
        aToB.setSourceFilePath("demo/A.java");
        aToB.setTargetFilePath("demo/B.java");
        depDao.saveEntity(bToC);
        depDao.saveEntity(aToB);
        depDao.flushSession();
    }

    private static String baseName(String path) {
        int idx = path.lastIndexOf('/');
        return idx >= 0 ? path.substring(idx + 1) : path;
    }

    private static double f1(Set<String> expected, Set<String> actual) {
        if (expected.isEmpty() && actual.isEmpty()) {
            return 1.0;
        }
        long tp = actual.stream().filter(expected::contains).count();
        if (tp == 0) {
            return 0;
        }
        double precision = (double) tp / actual.size();
        double recall = (double) tp / expected.size();
        return 2 * precision * recall / (precision + recall);
    }

    private double evaluate(String indexId, Path src, String modifiedFile,
                            String newContent, Set<String> expected) throws Exception {
        writeJavaFile(src.resolve("demo"), modifiedFile, newContent);

        int changed = codeIndexService.triggerIncrementalIndex(indexId,
                "file:" + src.toAbsolutePath().toString().replace('\\', '/'), null);
        assertTrue(changed > 0, "the modified file must be re-indexed");

        List<String> affectedRaw = codeIndexService.getLastIncrementalAffectedFiles(indexId);
        Set<String> actual = new LinkedHashSet<>();
        for (String path : affectedRaw) {
            actual.add(baseName(path));
        }
        double f1 = f1(expected, actual);
        System.out.println("  change " + modifiedFile + " → F1=" + f1
                + " expected=" + expected + " actual=" + actual);
        codeIndexService.deleteIndex(indexId);
        return f1;
    }

    @Test
    void changeLeafPropagatesToImporters() throws Exception {
        String indexId = "n81-impact-leaf";
        Path src = writeProject(C_V1, A_V1);
        codeIndexService.indexDirectory(indexId, src.toString(), null);
        seedDependencyGraph(indexId);

        // change C (leaf): 2-hop bidirectional surface = importers B and A
        double f1 = evaluate(indexId, src, "C.java", C_V2, Set.of("A.java", "B.java"));
        assertFalse(Double.isNaN(f1));
        assertTrue(f1 >= 0.5, "leaf-change impact F1 must be >= 0.5, got: " + f1);
    }

    @Test
    void changeRootPropagatesToDependencies() throws Exception {
        String indexId = "n81-impact-root";
        Path src = writeProject(C_V1, A_V1);
        codeIndexService.indexDirectory(indexId, src.toString(), null);
        seedDependencyGraph(indexId);

        // change A (root): the production hop surface is bidirectional, so A's import
        // dependency chain B -> C is part of the affected set. A reverse-only
        // implementation would return an empty set here (F1=0).
        double f1 = evaluate(indexId, src, "A.java", A_V2, Set.of("B.java", "C.java"));
        assertFalse(Double.isNaN(f1));
        assertTrue(f1 >= 0.5, "root-change impact F1 must be >= 0.5, got: " + f1);
    }
}
