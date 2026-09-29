package io.nop.bytecode.bench;

import io.nop.bytecode.analysis.nullflow.NullflowAnalyzer;
import io.nop.bytecode.kernel.cfg.MethodCfg;
import io.nop.bytecode.kernel.cfg.MethodCfgBuilder;
import io.nop.bytecode.kernel.dataflow.ForwardSolver;
import io.nop.bytecode.analysis.nullflow.NullnessSemantics;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Kernel benchmarks over the deterministic CorpusGenerator corpus (20 classes x 15 methods,
 * fixed — see docs/perf-baseline.md). Mirrors nop-lint's perf-baseline JMH profile:
 * avgt, fork 1, warmup 3x1s, measurement 5x1s.
 */
@BenchmarkMode(Mode.AverageTime)
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
public class NullflowKernelBenchmark {

    private List<byte[]> corpus;
    private List<ClassNode> parsed;

    @Setup(Level.Trial)
    public void setup() {
        corpus = CorpusGenerator.generate();
        parsed = new java.util.ArrayList<>();
        for (byte[] bytes : corpus) {
            ClassReader cr = new ClassReader(bytes);
            ClassNode cn = new ClassNode();
            cr.accept(cn, 0);
            parsed.add(cn);
        }
    }

    /** Parsing only: ClassReader accept over the full corpus. */
    @Benchmark
    public int parseCorpus() {
        int methods = 0;
        for (byte[] bytes : corpus) {
            ClassReader cr = new ClassReader(bytes);
            ClassNode cn = new ClassNode();
            cr.accept(cn, 0);
            methods += cn.methods.size();
        }
        return methods;
    }

    /** CFG construction over all methods of the pre-parsed corpus. */
    @Benchmark
    public int buildCfg() {
        int nodes = 0;
        for (ClassNode cn : parsed) {
            for (MethodNode mn : cn.methods) {
                MethodCfg cfg = new MethodCfgBuilder().build(mn);
                nodes += cfg.instructionCount();
            }
        }
        return nodes;
    }

    /** End-to-end nullflow analysis (CFG + abstract interpretation) over the full corpus. */
    @Benchmark
    public int runNullflow() {
        int hits = 0;
        NullflowAnalyzer analyzer = new NullflowAnalyzer();
        for (byte[] bytes : corpus) {
            hits += analyzer.analyze(bytes).size();
        }
        return hits;
    }

    /** Dataflow only (CFG reused from pre-parsed corpus) — isolates the solver from parsing. */
    @Benchmark
    public int solveDataflowOnly() {
        int states = 0;
        for (ClassNode cn : parsed) {
            for (MethodNode mn : cn.methods) {
                if ((mn.access & (org.objectweb.asm.Opcodes.ACC_ABSTRACT
                        | org.objectweb.asm.Opcodes.ACC_NATIVE)) != 0) continue;
                MethodCfg cfg = new MethodCfgBuilder().build(mn);
                NullnessSemantics sem = new NullnessSemantics(mn, cn.name.replace('/', '.'), mn.name,
                        f -> { });
                ForwardSolver solver = new ForwardSolver(mn, cfg, sem);
                solver.solve();
                states++;
            }
        }
        return states;
    }
}
