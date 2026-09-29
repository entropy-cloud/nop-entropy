package io.nop.bytecode.kernel;

import io.nop.bytecode.TestCompiler;
import io.nop.bytecode.analysis.nullflow.NullflowAnalyzer;
import io.nop.bytecode.analysis.nullflow.NullnessSemantics;
import io.nop.bytecode.bench.CorpusGenerator;
import io.nop.bytecode.kernel.cfg.MethodCfg;
import io.nop.bytecode.kernel.cfg.MethodCfgBuilder;
import io.nop.bytecode.kernel.dataflow.ForwardSolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.BasicVerifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Oracle shape audit (substrate ADR §5 discipline): for every method in both test corpora
 * (javac-compiled fixtures and the synthetic CorpusGenerator corpus), compare the self-built
 * engine's per-instruction stack height against ASM's {@code Analyzer + BasicVerifier}
 * (slot-summed entry sizes). Any divergence fails the test.
 */
class OracleShapeAuditTest {

    private static final String FIXTURE = """
            package fixtures;
            import java.util.List;
            public class MultiShape {
                private String field = "x";
                public int branches(String s, int n) {
                    int acc = 0;
                    if (s == null) return -1;
                    for (int i = 0; i < n; i++) {
                        acc += s.charAt(i % s.length());
                    }
                    switch (n % 4) {
                        case 0: acc++; break;
                        case 1: acc--; break;
                        default: acc += 2;
                    }
                    try {
                        acc += s.hashCode() / (n + 1);
                    } catch (RuntimeException e) {
                        acc = 0;
                    }
                    return acc;
                }
                public String calls(List<String> xs, int i) {
                    String s = xs.get(i);
                    Runnable r = () -> System.out.println(s);
                    r.run();
                    return String.valueOf(java.util.Objects.requireNonNull(s)).trim() + field;
                }
            }
            """;

    @Test
    void oracleAgreesOnJavacFixtures(@TempDir Path tmp) throws Exception {
        Path classes = tmp.resolve("fixture-classes");
        TestCompiler.compile("17", classes, "MultiShape.java", FIXTURE);
        try (var walk = Files.walk(classes)) {
            for (Path p : walk.filter(f -> f.toString().endsWith(".class")).toList()) {
                auditClassBytes(Files.readAllBytes(p));
            }
        }
    }

    @Test
    void oracleAgreesOnSyntheticCorpus() {
        for (byte[] classBytes : CorpusGenerator.generate()) {
            auditClassBytes(classBytes);
        }
        auditClassBytes(CorpusGenerator.generateApi());
    }

    private void auditClassBytes(byte[] bytes) {
        ClassReader cr = new ClassReader(bytes);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);
        for (MethodNode mn : cn.methods) {
            if ((mn.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) continue;
            String divergence = auditMethod(cn.name, mn);
            assertTrue(divergence == null, cn.name + "." + mn.name + mn.desc + ": " + divergence);
        }
    }

    /** Returns null when shapes agree, otherwise a description of the first divergence. */
    private String auditMethod(String owner, MethodNode mn) {
        try {
            org.objectweb.asm.tree.analysis.Frame<BasicValue>[] oracle =
                    new Analyzer<BasicValue>(new BasicVerifier()).analyze(owner, mn);
            MethodCfg cfg = new MethodCfgBuilder().build(mn);
            NullnessSemantics sem = new NullnessSemantics(mn, owner, mn.name, f -> { });
            ForwardSolver solver = new ForwardSolver(mn, cfg, sem);
            solver.solve();
            for (int i = 0; i < mn.instructions.size(); i++) {
                if (oracle[i] == null || solver.inFrame(i) == null) continue;
                int oracleSlots = 0;
                for (int k = 0; k < oracle[i].getStackSize(); k++) {
                    oracleSlots += oracle[i].getStack(k).getSize();
                }
                int mine = solver.inFrame(i).sp() - solver.inFrame(i).base();
                if (oracleSlots != mine) {
                    return "insn " + i + " op=" + mn.instructions.get(i).getOpcode()
                            + " mine=" + mine + " oracle=" + oracleSlots;
                }
            }
            return null;
        } catch (Exception e) {
            return "AUDIT-EXC " + e;
        }
    }
}
