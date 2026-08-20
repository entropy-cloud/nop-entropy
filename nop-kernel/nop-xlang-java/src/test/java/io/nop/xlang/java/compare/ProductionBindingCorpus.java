package io.nop.xlang.java.compare;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.resource.impl.InMemoryTextResource;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XplModel;
import io.nop.xlang.ast.XLangOutputMode;
import io.nop.xlang.compare.CompareUnit;
import io.nop.xlang.compare.CompareUnitKind;
import io.nop.xlang.compare.CorpusCoverageA;
import io.nop.xlang.compare.CorpusCoverageB;
import io.nop.xlang.compare.CorpusV1;
import io.nop.xlang.compare.ExpectedOutcome;
import io.nop.xlang.compare.RecordedOutputCall;
import io.nop.xlang.xpl.impl.XplModelParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 生产绑定路径测试的 corpus 静态单元供给（I10 Phase 1 D6 裁定载体）：
 * 覆盖 A / 覆盖 B / CorpusV1 的全部静态单元（20 + 17 + 11 = 48），以模型加载入口
 * （{@code XplModelParser}/{@code XLang.parseXpl}——绑定 hook 所在路径）为取树正道。
 *
 * <p>路径口径：VFS 标准路径（前导 '/'，{@code InMemoryTextResource} 校验要求）= corpus 声明路径
 * 加前导斜杠；预期（异常源位置 path）同步重映射——harness 断言的 path/line/col 与本供给一致。
 * corpus 各编译器的逐单元输出模式经 {@link CorpusCoverageB#outputModeOf} 消费（A/V1 = none）。
 */
public final class ProductionBindingCorpus {

    /** corpus 静态单元（path = 清单键与 SourceLocation path；mode = 编译输出模式） */
    public static final class StaticUnit {
        private final CompareUnit unit;
        private final String path;
        private final String source;
        private final XLangOutputMode mode;

        StaticUnit(CompareUnit unit, String source, XLangOutputMode mode) {
            this(unit, unit.getSourceLocationPath(), source, mode);
        }

        StaticUnit(CompareUnit unit, String path, String source, XLangOutputMode mode) {
            this.unit = unit;
            this.path = stdPath(path);
            this.source = source;
            this.mode = mode;
        }

        public CompareUnit getUnit() {
            return unit;
        }

        /** VFS 标准路径（清单键、扫描清单成员、SourceLocation path 三者一致） */
        public String getPath() {
            return path;
        }

        public String getSource() {
            return source;
        }

        public XLangOutputMode getMode() {
            return mode;
        }
    }

    /** 独立测试单元构造（corpus 外的自有单元，如租户隔离测试单元） */
    public static StaticUnit standalone(String path, String source, XLangOutputMode mode) {
        return new StaticUnit(null, path, source, mode);
    }

    private ProductionBindingCorpus() {
    }

    public static String stdPath(String corpusPath) {
        return corpusPath.startsWith("/") ? corpusPath : "/" + corpusPath;
    }

    public static List<StaticUnit> staticUnits() {
        List<StaticUnit> units = new ArrayList<>();
        collect(CorpusCoverageA.units(), units);
        collect(CorpusCoverageB.units(), units);
        collect(CorpusV1.units(), units);
        return units;
    }

    private static void collect(List<CompareUnit> corpus, List<StaticUnit> out) {
        for (CompareUnit unit : corpus) {
            if (unit.getKind() != CompareUnitKind.STATIC)
                continue;
            String path = unit.getSourceLocationPath();
            String source = readCorpusSource(path);
            out.add(new StaticUnit(unit, source, outputModeOf(path)));
        }
    }

    private static String readCorpusSource(String path) {
        if (path.startsWith("xlang-compare/static-a/"))
            return CorpusCoverageA.readResource(path);
        if (path.startsWith("xlang-compare/static-b/"))
            return CorpusCoverageB.readResource(path);
        return CorpusV1.readResource(path);
    }

    static XLangOutputMode outputModeOf(String corpusPath) {
        if (corpusPath.startsWith("xlang-compare/static-b/"))
            return CorpusCoverageB.outputModeOf(corpusPath);
        return XLangOutputMode.none;
    }

    /**
     * 模型加载入口取树（干净编译，不经绑定 hook）：与 {@code XLang.parseXpl} 同装载语义
     * （同一 XplModelParser），供夹具生成、指纹计算与 harness 解释器列基准。
     */
    public static IExecutableExpression compileCanonicalTree(StaticUnit unit) {
        return parseClean(unit).getExpr();
    }

    /** 模型加载入口装载（干净编译，不经绑定 hook） */
    public static XplModel parseClean(StaticUnit unit) {
        return new XplModelParser().outputModel(unit.getMode())
                .parseFromResource(new InMemoryTextResource(unit.getPath(), unit.getSource()));
    }

    /** 模型加载入口装载（经绑定 hook——生产绑定路径执行体） */
    public static XplModel loadBound(StaticUnit unit) {
        return XLang.parseXpl(new InMemoryTextResource(unit.getPath(), unit.getSource()), unit.getMode());
    }

    /**
     * corpus 单元的预期重映射（VFS 标准路径口径）：异常源位置 path 加前导斜杠（line/col 不变）。
     */
    public static ExpectedOutcome remapExpectation(ExpectedOutcome expectation) {
        ExpectedOutcome rebuilt;
        if (expectation.isExpectException()) {
            rebuilt = ExpectedOutcome.exception(expectation.getErrorCode());
        } else {
            rebuilt = ExpectedOutcome.returnValue(expectation.getReturnValue());
        }
        if (expectation.hasExpectedScopeVars())
            rebuilt.scopeVars(new LinkedHashMap<>(expectation.getExpectedScopeVars()));
        if (expectation.hasExpectedOutputCalls())
            rebuilt.outputCalls(expectation.getExpectedOutputCalls().stream()
                    .map(ProductionBindingCorpus::remapOutputCall)
                    .toArray(RecordedOutputCall[]::new));
        if (expectation.getExpectedErrorLocation() != null) {
            SourceLocation loc = expectation.getExpectedErrorLocation();
            rebuilt.errorLocation(SourceLocation.fromLine(stdPath(loc.getPath()), loc.getLine(), loc.getCol()));
        }
        return rebuilt;
    }

    private static RecordedOutputCall remapOutputCall(RecordedOutputCall call) {
        SourceLocation loc = call.getLoc();
        if (loc == null || loc.getPath() == null || loc.getPath().startsWith("/"))
            return call;
        return new RecordedOutputCall(call.getOp(),
                SourceLocation.fromLine(stdPath(loc.getPath()), loc.getLine(), loc.getCol()),
                call.getValue(), call.getAttrs());
    }
}
