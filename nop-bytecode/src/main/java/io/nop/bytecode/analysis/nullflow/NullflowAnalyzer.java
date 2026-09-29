package io.nop.bytecode.analysis.nullflow;

import io.nop.bytecode.NopBytecodeException;
import io.nop.bytecode.kernel.cfg.MethodCfg;
import io.nop.bytecode.kernel.cfg.MethodCfgBuilder;
import io.nop.bytecode.kernel.dataflow.ForwardSolver;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Null-flow analysis entry point: parses one class file and runs the nullness dataflow over
 * every concrete method, producing a deduplicated dereference-finding list.
 *
 * <p>Dialect (roadmap item 6 formal): branch-sensitive for IFNULL/IFNONNULL plus ACMP-with-null
 * (the conditions that can carry a null fact); exemption faces locked by ExemptionFaceTest.
 */
public final class NullflowAnalyzer {

    /** Analyze one class file (raw bytes). */
    public List<DerefFinding> analyze(byte[] classBytes) {
        ClassReader reader = new ClassReader(classBytes);
        ClassNode cn = new ClassNode();
        reader.accept(cn, 0);
        String className = cn.name.replace('/', '.');
        List<DerefFinding> all = new ArrayList<>();
        for (MethodNode mn : cn.methods) {
            all.addAll(analyzeMethod(className, mn));
        }
        return all;
    }

    /** Analyze one class file from a stream. */
    public List<DerefFinding> analyze(InputStream in) {
        try {
            ClassReader reader = new ClassReader(in);
            ClassNode cn = new ClassNode();
            reader.accept(cn, 0);
            String className = cn.name.replace('/', '.');
            List<DerefFinding> all = new ArrayList<>();
            for (MethodNode mn : cn.methods) {
                all.addAll(analyzeMethod(className, mn));
            }
            return all;
        } catch (java.io.IOException e) {
            throw new NopBytecodeException("Failed to read class bytes for nullflow analysis", e);
        }
    }

    public List<DerefFinding> analyzeMethod(String className, MethodNode mn) {
        if ((mn.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) {
            return List.of();
        }
        MethodCfg cfg = new MethodCfgBuilder().build(mn);
        Set<DerefFinding> hits = new HashSet<>();
        NullnessSemantics sem = new NullnessSemantics(mn, className, mn.name, hits::add);
        ForwardSolver solver = new ForwardSolver(mn, cfg, sem);
        solver.solve();
        List<DerefFinding> out = new ArrayList<>(hits);
        out.sort((a, b) -> {
            int c = a.className().compareTo(b.className());
            if (c != 0) return c;
            c = a.methodName().compareTo(b.methodName());
            if (c != 0) return c;
            return Integer.compare(a.insnIndex(), b.insnIndex());
        });
        return out;
    }
}
