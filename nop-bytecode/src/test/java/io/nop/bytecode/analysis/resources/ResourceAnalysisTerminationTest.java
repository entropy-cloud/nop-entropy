package io.nop.bytecode.analysis.resources;

import io.nop.bytecode.NopBytecodeException;
import io.nop.bytecode.TestCompiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * R1.4（plan 2367）：P1-1.10-002 死循环回归 + P1-1.10-001 分析失败响亮化。
 * <p>
 * 审计实证（1.10 报告）：ObligationToken 缺 equals/hashCode 时，资源引用跨循环回边存活的
 * shape（开在 for/while 之前、循环体内引用）使 Frame.mergeFrom 的 Objects.equals 恒 false
 * → worklist 永不排空（commons-io 实测 463s）。纯 if/else 菱形无回边不会触发（复审实证勘误）。
 */
public class ResourceAnalysisTerminationTest {

    @TempDir
    Path tmp;

    /** equals 语义：三字段（kind/nullState/openSites）决定相等性；hash 一致；TOP 与 NEW_MARKER 区分。 */
    @Test
    void tokenEqualitySemantics() {
        ObligationToken a = ObligationToken.tracked(ObligationToken.NullState.DEF, 3);
        ObligationToken b = ObligationToken.tracked(ObligationToken.NullState.DEF, 3);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());

        assertNotEquals(a, ObligationToken.tracked(ObligationToken.NullState.MAYBE, 3));
        assertNotEquals(a, ObligationToken.tracked(ObligationToken.NullState.DEF, 4));
        assertNotEquals(ObligationToken.TOP, ObligationToken.NEW_MARKER);

        // merge component-union is reflected in equality
        ObligationToken merged = ObligationToken.merge(
                ObligationToken.tracked(ObligationToken.NullState.DEF, 1),
                ObligationToken.tracked(ObligationToken.NullState.DEF, 2));
        assertEquals(merged, ObligationToken.tracked(ObligationToken.NullState.DEF,
                new java.util.TreeSet<>(List.of(1, 2))));
        assertEquals(a, ObligationToken.merge(a, a), "merge with itself is identity (stable equality)");
    }

    /**
     * 死循环回归（P1-1.10-002）：资源开在 for 循环之前、循环体内引用（回边携带 tracked
     * token 跨越循环头）。修复前该 shape 不收敛（事实挂死）；修复后在有界时间内终止并收敛。
     */
    @Test
    void loopBackEdgeShapeTerminates() throws Exception {
        String src = """
                import java.io.FileInputStream;
                public class LoopToy {
                    static int readLoop(String p, int n) throws Exception {
                        FileInputStream in = new FileInputStream(p);
                        int total = 0;
                        for (int i = 0; i < n; i++) {
                            total += in.read();
                        }
                        return total;
                    }
                }
                """;
        Path classes = tmp.resolve("loop-classes");
        TestCompiler.compile("17", classes, "LoopToy.java", src);
        byte[] cls = Files.readAllBytes(classes.resolve("LoopToy.class"));

        // fixpoint must terminate inside a bounded budget (unfixed code hangs here forever)
        List<UnclosedResourceFinding> findings = assertTimeoutPreemptively(java.time.Duration.ofSeconds(30),
                () -> new ResourceLeakAnalyzer().analyze(cls));

        assertEquals(1, findings.size(), "resource open before loop and never closed -> exactly 1 leak");
    }

    /** P1-1.10-001：截断 class 经分析层必须抛 NopBytecodeException（响亮化），不得逃逸裸 RuntimeException。 */
    @Test
    void truncatedClassFailsLoudAsNopBytecodeException() throws Exception {
        String src = """
                public class TruncToy {
                    int add(int a, int b) { return a + b; }
                    int mul(int a, int b) { return a * b; }
                    int sub(int a, int b) { return a - b; }
                }
                """;
        Path classes = tmp.resolve("trunc-classes");
        TestCompiler.compile("17", classes, "TruncToy.java", src);
        byte[] full = Files.readAllBytes(classes.resolve("TruncToy.class"));

        // cut inside the methods section: collect-level parsing (header/constant pool/fields)
        // survives, analysis-level accept() hits the truncation
        byte[] truncated = Arrays.copyOf(full, full.length - 24);

        assertThrows(NopBytecodeException.class, () -> new ResourceLeakAnalyzer().analyze(truncated));
        assertThrows(NopBytecodeException.class,
                () -> new io.nop.bytecode.analysis.nullflow.NullflowAnalyzer().analyze(truncated));
    }
}
