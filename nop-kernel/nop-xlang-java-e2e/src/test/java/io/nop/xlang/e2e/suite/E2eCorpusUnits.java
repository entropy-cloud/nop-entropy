package io.nop.xlang.e2e.suite;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.xlang.ast.XLangOutputMode;
import io.nop.xlang.compare.CompareUnit;
import io.nop.xlang.compare.CompareUnitKind;
import io.nop.xlang.compare.CorpusCoverageA;
import io.nop.xlang.compare.CorpusCoverageB;
import io.nop.xlang.compare.CorpusV1;
import io.nop.xlang.compare.ExpectedOutcome;
import io.nop.xlang.compare.ICompareUnitCompiler;
import io.nop.xlang.compare.RecordedOutputCall;
import io.nop.xlang.xpl.impl.XplModelParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全量三后端对拍套件的语料供给（I12 Phase 1 D1 裁定载体）：
 * <ul>
 * <li><b>静态单元</b>（48 = static 11 + static-a 20 + static-b 17）：corpus 三族（nop-xlang
 * test-jar 单一事实源）枚举，路径重映射到 e2e 物化资源（{@code /test/xlang/e2e/corpus/...}，
 * main classpath + gen task 双清单成员）；<b>三列统一以生产形态 html 模式驱动</b>（与 gen task /
 * 运行时 HtmlXplModelLoader 取树一致）。5 个非 html 语义单元（TAG_TEXT ×1 / TAG_XML ×2 /
 * TAG_NODE ×2）按 html 变体树驱动，<b>预期值按 html 输出重新声明</b>（oracle = 解释器列 html
 * 模式实测输出，经三列一致性交叉验证固化；原 per-unit mode 语义覆盖由既有列测试保持）；
 * 3 个 TAG_NONE collect 单元为显式核验点（html 统一驱动下返回值不变——实测绿，录入本供给）。
 * loc 承载预期（异常 errorLocation + 输出调用 loc）按套件驱动路径重映射。</li>
 * <li><b>动态单元</b>（26 = V1 11 + A 13 + B 2）：测试内构造语料（corpus 单一事实源原样），
 * 编译经 corpus 既有编译器（动态出口），java 列不适用（既定适用性约定）。</li>
 * </ul>
 */
public final class E2eCorpusUnits {

    /** e2e 物化 corpus 资源根（main resources `_vfs`） */
    public static final String CORPUS_VFS_BASE = "/test/xlang/e2e/corpus/";

    private static final String CORPUS_RESOURCE_BASE = "xlang-compare/";

    /** html 变体树单元（5 个非 html 语义单元——预期值重声明集合，D1 必答预列） */
    static final List<String> HTML_VARIANT_UNITS = List.of(
            "xlang-compare/static-b/tpl-text.xpl",
            "xlang-compare/static-b/tpl-xml.xpl",
            "xlang-compare/static-b/tpl-xml-extattrs.xpl",
            "xlang-compare/static-b/tpl-node.xpl",
            "xlang-compare/static-b/tpl-node-simple.xpl");

    /** TAG_NONE collect 核验点单元（html 统一驱动返回值不变——实测绿，显式记录） */
    static final List<String> COLLECT_VERIFY_UNITS = List.of(
            "xlang-compare/static-b/tpl-collect-text.xpl",
            "xlang-compare/static-b/tpl-collect-node.xpl",
            "xlang-compare/static-b/tpl-collect-sql.xpl");

    private E2eCorpusUnits() {
    }

    /** 物化 VFS 路径（corpus 声明路径 → e2e `_vfs` corpus 路径） */
    public static String vfsPath(String corpusPath) {
        return CORPUS_VFS_BASE + corpusPath.substring(CORPUS_RESOURCE_BASE.length());
    }

    /** 全部 74 单元（48 静态物化 + 26 动态），静态路径已重映射 */
    public static List<CompareUnit> allUnits() {
        List<CompareUnit> units = new ArrayList<>();
        for (CompareUnit u : CorpusV1.units())
            units.add(adapt(u));
        for (CompareUnit u : CorpusCoverageA.units())
            units.add(adapt(u));
        for (CompareUnit u : CorpusCoverageB.units())
            units.add(adapt(u));
        return units;
    }

    /** 单元适配：静态单元路径重映射 + 预期值适配（loc 重映射 / html 变体重声明）；动态单元原样 */
    static CompareUnit adapt(CompareUnit unit) {
        if (unit.getKind() == CompareUnitKind.DYNAMIC)
            return unit;
        String corpusPath = unit.getSourceLocationPath();
        String path = vfsPath(corpusPath);
        return new CompareUnit(unit.getName(), CompareUnitKind.STATIC, path, path,
                unit.getInputVars(), adaptExpectation(corpusPath, unit.getExpectation()),
                unit.getCategory());
    }

    static ExpectedOutcome adaptExpectation(String corpusPath, ExpectedOutcome expectation) {
        // html 变体单元：按 html 输出重新声明（返回值 null + TEXT 扁平化输出序列）；
        // oracle = 解释器列 html 模式实测（三列交叉验证后固化，见套件三列一致性断言）
        if (HTML_VARIANT_UNITS.contains(corpusPath))
            return htmlVariantExpectation(corpusPath);
        // 其余单元（40 SCRIPT + 3 collect）：返回值/副作用断言语义不变，仅 loc 路径重映射
        return remapLocs(expectation);
    }

    private static ExpectedOutcome remapLocs(ExpectedOutcome expectation) {
        ExpectedOutcome rebuilt = expectation.isExpectException()
                ? ExpectedOutcome.exception(expectation.getErrorCode())
                : ExpectedOutcome.returnValue(expectation.getReturnValue());
        if (expectation.hasExpectedScopeVars())
            rebuilt.scopeVars(new LinkedHashMap<>(expectation.getExpectedScopeVars()));
        if (expectation.hasExpectedOutputCalls())
            rebuilt.outputCalls(expectation.getExpectedOutputCalls().stream()
                    .map(E2eCorpusUnits::remapCallLoc).toArray(RecordedOutputCall[]::new));
        if (expectation.getExpectedErrorLocation() != null) {
            SourceLocation loc = expectation.getExpectedErrorLocation();
            rebuilt.errorLocation(SourceLocation.fromLine(vfsPath(loc.getPath()), loc.getLine(), loc.getCol()));
        }
        return rebuilt;
    }

    private static RecordedOutputCall remapCallLoc(RecordedOutputCall call) {
        SourceLocation loc = call.getLoc();
        if (loc == null || loc.getPath() == null)
            return call;
        return new RecordedOutputCall(call.getOp(),
                SourceLocation.fromLine(vfsPath(loc.getPath()), loc.getLine(), loc.getCol()),
                call.getValue(), call.getAttrs());
    }

    private static ExpectedOutcome htmlVariantExpectation(String corpusPath) {
        String name = corpusPath.substring(corpusPath.lastIndexOf('/') + 1, corpusPath.length() - 4);
        ExpectedOutcome expectation = ExpectedOutcome.returnValue(null);
        if ("tpl-text".equals(name)) {
            expectation.outputCalls(
                    call(RecordedOutputCall.Op.TEXT, null, "a"),
                    call(RecordedOutputCall.Op.TEXT, loc(name, 1, 37), "1"),
                    call(RecordedOutputCall.Op.TEXT, null, "a"),
                    call(RecordedOutputCall.Op.TEXT, loc(name, 1, 37), "2"));
        } else if ("tpl-xml".equals(name)) {
            expectation.outputCalls(
                    call(RecordedOutputCall.Op.TEXT, loc(name, 1, 2), "\n<div a=\"1\""),
                    call(RecordedOutputCall.Op.TEXT, null, " "),
                    call(RecordedOutputCall.Op.TEXT, null, "b"),
                    call(RecordedOutputCall.Op.TEXT, loc(name, 1, 15), "=\""),
                    call(RecordedOutputCall.Op.TEXT, loc(name, 1, 15), "5"),
                    call(RecordedOutputCall.Op.TEXT, null, "\""),
                    call(RecordedOutputCall.Op.TEXT, loc(name, 1, 2), ">x</div>"));
        } else if ("tpl-xml-extattrs".equals(name)) {
            expectation.outputCalls(
                    call(RecordedOutputCall.Op.TEXT, loc(name, 1, 2), "\n<div a=\"1\""),
                    call(RecordedOutputCall.Op.TEXT, null, " "),
                    call(RecordedOutputCall.Op.TEXT, null, "b"),
                    call(RecordedOutputCall.Op.TEXT, null, "=\""),
                    call(RecordedOutputCall.Op.TEXT, loc(name, 1, 23), "2"),
                    call(RecordedOutputCall.Op.TEXT, null, "\""),
                    call(RecordedOutputCall.Op.TEXT, loc(name, 1, 2), ">x</div>"));
        } else if ("tpl-node".equals(name)) {
            expectation.outputCalls(
                    call(RecordedOutputCall.Op.TEXT, loc(name, 1, 2), "\n<a x=\"1\">\n<b>t</b></a>"));
        } else if ("tpl-node-simple".equals(name)) {
            expectation.outputCalls(
                    call(RecordedOutputCall.Op.TEXT, loc(name, 1, 2), "\n<a x=\"1\"></a>"));
        } else {
            throw new IllegalArgumentException("not an html-variant unit: " + corpusPath);
        }
        return expectation;
    }

    private static RecordedOutputCall call(RecordedOutputCall.Op op, SourceLocation loc, Object value) {
        return new RecordedOutputCall(op, loc, value);
    }

    private static SourceLocation loc(String fileName, int line, int col) {
        return SourceLocation.fromLine(vfsPath(CORPUS_RESOURCE_BASE + "static-b/" + fileName + ".xpl"), line, col);
    }

    /**
     * 套件编译器：静态单元 = e2e 物化资源干净解析（html 生产模式，不经绑定 hook）；
     * 动态单元 = corpus 既有编译器（动态出口取树）。
     */
    public static final class SuiteCompiler implements ICompareUnitCompiler {
        private final Map<String, ICompareUnitCompiler> dynamicCompilers = new LinkedHashMap<>();

        public SuiteCompiler() {
            for (CompareUnit u : CorpusV1.units())
                if (u.getKind() == CompareUnitKind.DYNAMIC)
                    dynamicCompilers.put(u.getName(), CorpusV1.Compiler.INSTANCE);
            for (CompareUnit u : CorpusCoverageA.units())
                if (u.getKind() == CompareUnitKind.DYNAMIC)
                    dynamicCompilers.put(u.getName(), CorpusCoverageA.Compiler.INSTANCE);
            for (CompareUnit u : CorpusCoverageB.units())
                if (u.getKind() == CompareUnitKind.DYNAMIC)
                    dynamicCompilers.put(u.getName(), CorpusCoverageB.Compiler.INSTANCE);
        }

        @Override
        public IExecutableExpression compile(CompareUnit unit) {
            if (unit.getKind() == CompareUnitKind.STATIC) {
                // 物化资源干净解析（与 gen task / 生产装载同模式 html；单一资源路径供给三列）
                return new XplModelParser().outputModel(XLangOutputMode.html)
                        .parseFromResource(VirtualFileSystem.instance().getResource(unit.getSourceLocationPath()))
                        .getExpr();
            }
            ICompareUnitCompiler compiler = dynamicCompilers.get(unit.getName());
            if (compiler == null)
                throw new IllegalStateException("no dynamic compiler registered for unit: " + unit.getName());
            return compiler.compile(unit);
        }
    }
}
