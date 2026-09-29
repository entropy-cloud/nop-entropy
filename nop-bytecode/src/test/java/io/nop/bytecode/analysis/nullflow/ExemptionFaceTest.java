package io.nop.bytecode.analysis.nullflow;

import io.nop.bytecode.TestCompiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Formal-dialect exemption-face lock (roadmap item 6): the three exemption forms must yield
 * zero findings, and the ACMP-null form (null == x / cast-compare / ternary null) must refine.
 *
 * <p>Bytecode-shape attribution (javac-verified): {@code assert x != null} compiles to
 * IFNONNULL (+ $assertionsDisabled guard), {@code if (x == null) throw new <exception>} compiles
 * to IFNULL + ATHROW — both covered by the v1 IFNULL/IFNONNULL dialect; the ACMP form
 * ({@code null == x} literal-first, which javac does not fold) is the formal-dialect addition.
 * The platform NopException guard is shape-identical to the IllegalStateException fixture below
 * (IFNULL + ATHROW), so no api-core compile dependency is needed.
 */
class ExemptionFaceTest {

    private static final String TOY = """
            public class ExemptToy {
                static int viaAssert(String s) {
                    assert s != null : "s required";
                    return s.length();
                }
                static int viaIfNullThrow(String s) {
                    if (s == null) throw new IllegalStateException("s required");
                    return s.length();
                }
                static String viaRequireNonNull(String s) {
                    return java.util.Objects.requireNonNull(s).trim();
                }
                static int viaNullLiteralFirstCompare(Object x, String fallback) {
                    if (null == x) {
                        return fallback.length();
                    }
                    return x.hashCode();
                }
                static int unguarded(String s) {
                    return s.length();
                }
            }
            """;

    @Test
    void exemptionFacesYieldZeroFindingsAndUnguardedIsReported(@TempDir Path tmp) throws Exception {
        Path classes = tmp.resolve("exempt-classes");
        TestCompiler.compile("17", classes, "ExemptToy.java", TOY);
        byte[] cls = Files.readAllBytes(classes.resolve("ExemptToy.class"));

        List<DerefFinding> findings = new NullflowAnalyzer().analyze(cls);
        Map<String, Long> byMethod = findings.stream()
                .collect(Collectors.groupingBy(DerefFinding::methodName, Collectors.counting()));

        assertEquals(0, byMethod.getOrDefault("viaAssert", 0L), "assert guard is an exemption face");
        assertEquals(0, byMethod.getOrDefault("viaIfNullThrow", 0L),
                "if-null-throw guard (NopException shape) is an exemption face");
        assertEquals(0, byMethod.getOrDefault("viaRequireNonNull", 0L), "requireNonNull gate is an exemption face");
        // null == x true-branch refines x to NULL (fallback.length() hit), x.hashCode() on the
        // nonnull branch is refined to NONNULL via the ACMP edge -> only the NULL-side deref hits
        assertEquals(1, byMethod.getOrDefault("viaNullLiteralFirstCompare", 0L),
                "ACMP refinement: fallback (NULL side) deref is a true positive, x.hashCode() is guarded");
        assertEquals(1, byMethod.getOrDefault("unguarded", 0L), "unguarded deref is a true positive");
    }

    @Test
    void acmpBothPolaritiesAcrossLocalShapesAreExempted(@TempDir Path tmp) throws Exception {
        // Lock the ACMP dialect on both failure axes (audit Blocker 1 polarity / Blocker 2
        // provenance): statement-if forms compile inverted — javac emits IF_ACMPNE for
        // `null == x` and IF_ACMPEQ for `null != x`. Four methods cover the four failing
        // axis combinations; a fifth (ne x static-local-0) completes the ne matrix.
        String src = """ 
            public class AcmpShapes {
                static int eqLocal0(Object x) {
                    if (null == x) return -1;
                    return x.hashCode();
                }
                static int neLocalNon0(Object a, Object x) {
                    if (null != x) return x.hashCode();
                    return -1;
                }
                int eqInstance(Object x) {
                    if (null == x) return -1;
                    return x.hashCode();
                }
                int neInstance(Object a, Object x) {
                    if (null != x) return x.hashCode();
                    return -1;
                }
                static int neLocal0(Object x) {
                    if (null != x) return x.hashCode();
                    return -1;
                }
            }
            """.replace("\\n", "\n");
        Path classes = tmp.resolve("acmp-classes");
        TestCompiler.compile("17", classes, "AcmpShapes.java", src);
        List<DerefFinding> findings = new NullflowAnalyzer().analyze(
                Files.readAllBytes(classes.resolve("AcmpShapes.class")));
        assertTrue(findings.isEmpty(), "all six null==/null!= guarded derefs exempted, got: " + findings);
    }

    @Test
    void exemptionCountersExposedForFpControlData(@TempDir Path tmp) throws Exception {
        Path classes = tmp.resolve("exempt-classes");
        TestCompiler.compile("17", classes, "ExemptToy.java", TOY);
        byte[] cls = Files.readAllBytes(classes.resolve("ExemptToy.class"));

        ClassReaderHolder holder = analyzeWithStats(cls);
        assertTrue(holder.stats.guardRefinements() >= 2,
                "assert + if-null-throw guards refine locals (ACMP counted separately): " + holder.stats);
        assertTrue(holder.stats.requireNonNullGates() >= 1, "requireNonNull gate counted");
        assertTrue(holder.stats.acmpRefinements() >= 1, "ACMP-null refinement counted");
    }

    record Stats(long guardRefinements, long requireNonNullGates, long acmpRefinements) { }

    private record ClassReaderHolder(Stats stats, List<DerefFinding> findings) { }

    private ClassReaderHolder analyzeWithStats(byte[] cls) throws Exception {
        org.objectweb.asm.ClassReader cr = new org.objectweb.asm.ClassReader(cls);
        org.objectweb.asm.tree.ClassNode cn = new org.objectweb.asm.tree.ClassNode();
        cr.accept(cn, 0);
        long guards = 0, gates = 0, acmps = 0;
        for (org.objectweb.asm.tree.MethodNode mn : cn.methods) {
            if ((mn.access & (org.objectweb.asm.Opcodes.ACC_ABSTRACT
                    | org.objectweb.asm.Opcodes.ACC_NATIVE)) != 0) continue;
            io.nop.bytecode.kernel.cfg.MethodCfg cfg =
                    new io.nop.bytecode.kernel.cfg.MethodCfgBuilder().build(mn);
            NullnessSemantics sem = new NullnessSemantics(mn, cn.name.replace('/', '.'), mn.name, f -> { });
            new io.nop.bytecode.kernel.dataflow.ForwardSolver(mn, cfg, sem).solve();
            guards += sem.guardRefinements();
            gates += sem.requireNonNullGates();
            acmps += sem.acmpRefinements();
        }
        return new ClassReaderHolder(new Stats(guards, gates, acmps),
                new NullflowAnalyzer().analyze(cls));
    }
}
