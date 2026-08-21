/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.benchmark.xlang;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.DisabledEvalOutput;
import io.nop.core.lang.eval.EvalExprProvider;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XplModel;
import io.nop.xlang.ast.XLangOutputMode;
import io.nop.xlang.backend.EvalStaticBoundExecutable;
import io.nop.xlang.java.backend.JavaEvalExecutionBackend;
import io.nop.xlang.java.gen.EvalMethodConvention;
import io.nop.xlang.java.gen.GeneratedManifestFiles;
import io.nop.xlang.truffle.nodes.XLangRootNode;
import io.nop.xlang.truffle.runtime.XLangContextPool;
import io.nop.xlang.xpl.impl.XplModelParser;
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
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 基准用例①：静态单元三向对比（I12 Phase 2）——同批 46 单元语料（e2e main classpath 生产
 * 形态全集的耗时裁选子集，见 {@link StaticCorpus}）：
 * <ul>
 * <li><b>interpreter 列 = 显式旁路</b>（静态语料为清单成员、经 choke point 会被决策树路由
 * 到 java 后端）——干净解析取树（`XplModelParser` 直驱，不经绑定 hook）经全局执行器直驱；
 * setup 期身份核验：执行体非 bound 包装。</li>
 * <li><b>java 列 = 生产绑定路径</b>——classpath 双清单装载 + `XLang.parseXpl` 绑定 hook +
 * choke point 直通；setup 期身份核验：执行体 = 确定性派生生成类（`Class.forName` 产物）。</li>
 * <li><b>truffle 列 = 翻译 AST 经 CallTarget</b>（Context 池租借稳态；静态分支不路由 truffle
 * ——本列即"静态资源 JVM 形态生成物缺失不借道 truffle"的机会成本对照列）；setup 期身份核验：
 * 翻译 XLangRootNode sourceTree 即请求树。</li>
 * </ul>
 * 每次调用扫全批 46 单元（per-unit 成本 = 报告侧按批大小折算）。
 */
@Fork(1)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
public class StaticThreeWayBenchmark {

    private static final class Prepared {
        final StaticCorpus.Unit unit;
        final IExecutableExpression cleanTree;
        final IExecutableExpression boundExpr;

        Prepared(StaticCorpus.Unit unit, IExecutableExpression cleanTree, IExecutableExpression boundExpr) {
            this.unit = unit;
            this.cleanTree = cleanTree;
            this.boundExpr = boundExpr;
        }
    }

    private List<Prepared> prepared;
    private XLangContextPool pool;

    @Setup(Level.Trial)
    public void setup() {
        CoreInitialization.initialize();
        try {
            if (!GeneratedManifestFiles.installSupplies(getClass().getClassLoader()))
                throw new IllegalStateException("classpath dual manifests missing (build pipeline products)");
            if (!JavaEvalExecutionBackend.instance().isAvailable())
                throw new IllegalStateException("java backend unavailable (manifests not loaded)");
            StaticCorpus.verifyAgainstProductionManifest(
                    JavaEvalExecutionBackend.instance().getStaticScanList());

            prepared = new ArrayList<>();
            for (StaticCorpus.Unit unit : StaticCorpus.timedUnits()) {
                IResource resource = VirtualFileSystem.instance().getResource(unit.getPath());
                if (resource == null)
                    throw new IllegalStateException("corpus resource missing on classpath: " + unit.getPath());

                // 解释器列取树：干净解析（html 生产模式，不经绑定 hook）
                IExecutableExpression cleanTree = new XplModelParser().outputModel(XLangOutputMode.html)
                        .parseFromResource(resource).getExpr();
                if (cleanTree instanceof EvalStaticBoundExecutable)
                    throw new IllegalStateException("interpreter column tree must be clean (non-bound): "
                            + unit.getPath());

                // java 列执行体：生产绑定（parseXpl hook）+ 身份核验（确定性生成类）
                XplModel boundModel = XLang.parseXpl(resource, XLangOutputMode.html);
                if (!(boundModel.getExpr() instanceof EvalStaticBoundExecutable))
                    throw new IllegalStateException("java column model must be bound (manifest member): "
                            + unit.getPath());
                Method entry = (Method) ((EvalStaticBoundExecutable) boundModel.getExpr())
                        .getBinding().getBindingArtifact();
                String expectedFqn = EvalMethodConvention.GENERATED_PACKAGE + '.'
                        + EvalMethodConvention.generatedClassName(unit.getPath());
                if (!expectedFqn.equals(entry.getDeclaringClass().getName()))
                    throw new IllegalStateException("java column identity mismatch: expected=" + expectedFqn
                            + ", was=" + entry.getDeclaringClass().getName());

                prepared.add(new Prepared(unit, cleanTree, boundModel.getExpr()));
            }

            pool = XLangContextPool.open();

            // 三列正确性 + truffle 身份核验（首翻译 sourceTree 即请求树；任何执行异常 fail-fast）
            try (XLangContextPool.Lease lease = pool.lease()) {
                for (Prepared p : prepared) {
                    Object interp = EvalExprProvider.getGlobalExecutor().execute(p.cleanTree,
                            new EvalRuntime(p.unit.newScope(), DisabledEvalOutput.INSTANCE));
                    Object java = XLang.execute(p.boundExpr,
                            new EvalRuntime(p.unit.newScope(), DisabledEvalOutput.INSTANCE));
                    XLangTruffleEvalTranslated t = translate(lease, p);
                    if (!BenchmarkValues.valueEquals(interp, java)
                            || !BenchmarkValues.valueEquals(interp, t.value))
                        throw new IllegalStateException("three-way value divergence at setup: " + p.unit.getPath()
                                + ": interp=" + interp + ", java=" + java + ", truffle=" + t.value);
                }
            }
        } catch (RuntimeException e) {
            CoreInitialization.destroy();
            throw e;
        }
    }

    private static final class XLangTruffleEvalTranslated {
        final Object value;
        final XLangRootNode root;

        XLangTruffleEvalTranslated(Object value, XLangRootNode root) {
            this.value = value;
            this.root = root;
        }
    }

    private XLangTruffleEvalTranslated translate(XLangContextPool.Lease lease, Prepared p) {
        io.nop.xlang.truffle.eval.XLangTruffleEval.TranslatedEval result = lease.eval(
                p.unit.getPath(), p.cleanTree, p.unit.newScope(), DisabledEvalOutput.INSTANCE);
        if (result.getThrown() != null)
            throw new IllegalStateException("truffle column setup execution failed: " + p.unit.getPath(),
                    result.getThrown());
        XLangRootNode root = result.getUnit().getRootNode();
        if (root.getSourceTree() != p.cleanTree)
            throw new IllegalStateException("truffle column identity mismatch (translated root must derive "
                    + "from the requested tree): " + p.unit.getPath());
        return new XLangTruffleEvalTranslated(result.getReturnValue(), root);
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (pool != null)
            pool.close();
        GeneratedManifestFiles.installSupplies(getClass().getClassLoader());
        CoreInitialization.destroy();
    }

    @Benchmark
    public void interpreterStaticCorpus(Blackhole bh) {
        for (Prepared p : prepared) {
            IEvalScope scope = p.unit.newScope();
            bh.consume(EvalExprProvider.getGlobalExecutor().execute(p.cleanTree,
                    new EvalRuntime(scope, DisabledEvalOutput.INSTANCE)));
        }
    }

    @Benchmark
    public void javaStaticCorpus(Blackhole bh) {
        for (Prepared p : prepared) {
            IEvalScope scope = p.unit.newScope();
            bh.consume(XLang.execute(p.boundExpr, new EvalRuntime(scope, DisabledEvalOutput.INSTANCE)));
        }
    }

    @Benchmark
    public void truffleStaticCorpus(Blackhole bh) {
        try (XLangContextPool.Lease lease = pool.lease()) {
            for (Prepared p : prepared) {
                bh.consume(lease.eval(p.unit.getPath(), p.cleanTree,
                        p.unit.newScope(), DisabledEvalOutput.INSTANCE).getReturnValue());
            }
        }
    }
}
