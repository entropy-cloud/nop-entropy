package io.nop.bytecode.analysis.resources;

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
import java.util.List;

/**
 * Resource obligation analysis entry point (plan 06 v1): parses one class file and runs the
 * obligation dataflow over every concrete method, producing unclosed-resource findings on
 * method-exit paths.
 *
 * <p>v1 faces: two registry families (Closeable NEW + JDBC factory acquire; Lock deferred),
 * name-list conservative type resolution, implicit-exception-propagation exits not reported.
 */
public final class ResourceLeakAnalyzer {

    private final ResourceRegistry registry = new ResourceRegistry();

    public List<UnclosedResourceFinding> analyze(byte[] classBytes) {
        ClassReader reader = new ClassReader(classBytes);
        ClassNode cn = new ClassNode();
        reader.accept(cn, 0);
        String className = cn.name.replace('/', '.');
        List<UnclosedResourceFinding> all = new ArrayList<>();
        for (MethodNode mn : cn.methods) {
            all.addAll(analyzeMethod(className, mn));
        }
        return all;
    }

    public List<UnclosedResourceFinding> analyze(InputStream in) {
        try {
            ClassReader reader = new ClassReader(in);
            ClassNode cn = new ClassNode();
            reader.accept(cn, 0);
            String className = cn.name.replace('/', '.');
            List<UnclosedResourceFinding> all = new ArrayList<>();
            for (MethodNode mn : cn.methods) {
                all.addAll(analyzeMethod(className, mn));
            }
            return all;
        } catch (java.io.IOException e) {
            throw new NopBytecodeException("Failed to read class bytes for resource analysis", e);
        }
    }

    public List<UnclosedResourceFinding> analyzeMethod(String className, MethodNode mn) {
        if ((mn.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) {
            return List.of();
        }
        MethodCfg cfg = new MethodCfgBuilder().build(mn);
        // solver revisits instructions as in-frames merge — findings dedup via Set (same as nullflow)
        java.util.Set<UnclosedResourceFinding> hitSet = new java.util.HashSet<>();
        ResourceSemantics sem = new ResourceSemantics(mn, className, mn.name, registry, hitSet::add);
        new ForwardSolver(mn, cfg, sem).solve();
        List<UnclosedResourceFinding> ordered = new ArrayList<>(hitSet);
        ordered.sort((a, b) -> {
            int c = a.className().compareTo(b.className());
            if (c != 0) return c;
            c = a.methodName().compareTo(b.methodName());
            if (c != 0) return c;
            return a.ref().compareTo(b.ref());
        });
        return ordered;
    }
}
