package io.nop.xlang.compare;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.sql.SQL;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.xlang.api.ExprEvalAction;
import io.nop.xlang.api.XLang;
import io.nop.xlang.ast.XLangOutputMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.xlang.XLangErrors.ERR_EXEC_INVOKE_METHOD_FAIL;
import static io.nop.xlang.XLangErrors.ERR_EXEC_THROW_EXCEPTION;

/**
 * corpus 覆盖 B（I4）：函数/闭包、控制流、输出/节点生成三族对应类别单元
 * （`CorpusV1`/`CorpusCoverageA` 既有单元与类别不动）。
 *
 * <p>静态形态：测试资源 `xlang-compare/static-b/*.xpl`——c:script 语句单元（函数/控制流族，
 * 经 `compileXpl` 取树）与<b>模板单元</b>（输出族，含输出语义，经 `compileTag(node, outputMode)`
 * 取树；Phase 1 §8 定稿：`$out` 第二隐参执行通路的载体，java 列按参数个数注入录制缓冲）。
 * 动态形态：`xlang-compare/dynamic-b/` 表达式串经 `compileFullExpr` 取树。
 *
 * <p><b>动态产生缺席记录</b>（显式，非静默跳过）：控制流语句族（For/While/DoWhile/Switch/Break/
 * Continue/Return/Try/Throw）与输出族全部节点（Output 与 Gen、Collect、EscapeOutput 各子族）不可经
 * 动态编译出口产生（`compileFullExpr` 仅表达式、`compileTemplateExpr` 产 Concat）；
 * `EscapeOutputExecutable` 静态单元亦不可低成本产生（escapeXml 全局函数在测试域未注册）——
 * 上述均由矩阵合成树真实转译覆盖（`TestExecTranslationCoverageMatrix` 逐类流）。
 *
 * <p>单元 schema 四字段齐备（I1 口径）；类别 → 必含节点规则与 B 范围白名单
 * （{@code ExecNodeBaseline.javaTargetSet()} 单一事实源 + CorpusV1 白名单回退）。
 */
public final class CorpusCoverageB {
    public static final String CATEGORY_FUNCTION_CLOSURE = "b-function-closure";
    public static final String CATEGORY_CONTROL_FLOW = "b-control-flow";
    public static final String CATEGORY_OUTPUT_NODE_GEN = "b-output-node-gen";

    public static final List<String> CATEGORIES = Collections.unmodifiableList(Arrays.asList(
            CATEGORY_FUNCTION_CLOSURE, CATEGORY_CONTROL_FLOW, CATEGORY_OUTPUT_NODE_GEN));

    private static final String STATIC_RESOURCE_BASE = "xlang-compare/static-b/";
    private static final String DYNAMIC_PATH_BASE = "xlang-compare/dynamic-b/";

    private static final String ERR_INVOKE_METHOD_FAIL = ERR_EXEC_INVOKE_METHOD_FAIL.getErrorCode();
    private static final String ERR_THROW = ERR_EXEC_THROW_EXCEPTION.getErrorCode();

    /** 承载"局部函数声明 + 调用"形态（非根 CallFuncExecutable）的单元（I4 Phase 1 §2 裁定语料）。 */
    public static final Set<String> LOCAL_FUNCTION_FORM_UNITS = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList("fn-local-call", "exception-fn-throw")));

    private final List<CompareUnit> units = new ArrayList<>();

    /** 单元编译形态（静态 = 脚本单元 / 模板单元×输出模式；动态 = full-expr）。 */
    enum UnitForm {
        SCRIPT, TAG_NONE, TAG_TEXT, TAG_XML, TAG_NODE, DYNAMIC
    }

    private static final Map<String, UnitForm> UNIT_FORMS = new LinkedHashMap<>();

    private CorpusCoverageB() {
        Map<String, Object> extVars = new LinkedHashMap<>();
        extVars.put("cnt", scopeVars("b", 2));

        // ---- b-function-closure（含局部函数声明 + 调用形态语料——Phase 1 §2 裁定承载单元）----
        staticUnit("fn-local-call", CATEGORY_FUNCTION_CLOSURE, ExpectedOutcome.returnValue(6));
        staticUnit("fn-closure-cell", CATEGORY_FUNCTION_CLOSURE, ExpectedOutcome.returnValue(3));
        staticUnit("fn-arrow-call", CATEGORY_FUNCTION_CLOSURE, ExpectedOutcome.returnValue(9));
        staticUnit("exception-fn-throw", CATEGORY_FUNCTION_CLOSURE,
                ExpectedOutcome.exception(ERR_INVOKE_METHOD_FAIL)
                        .errorLocation(SourceLocation.fromLine(
                                STATIC_RESOURCE_BASE + "exception-fn-throw.xpl", 2, 21)));
        dynamicUnit("dyn-iife", CATEGORY_FUNCTION_CLOSURE, ExpectedOutcome.returnValue(9),
                "((x) => x + 8)(1)");
        dynamicUnit("dyn-closure", CATEGORY_FUNCTION_CLOSURE, ExpectedOutcome.returnValue(3),
                "let f = x => x + 1; f(2)");

        // ---- b-control-flow（含异常语义单元：错误码 + 预期源位置）----
        staticUnit("ctrl-for-break-continue", CATEGORY_CONTROL_FLOW, ExpectedOutcome.returnValue(4));
        staticUnit("ctrl-switch", CATEGORY_CONTROL_FLOW, ExpectedOutcome.returnValue(20));
        staticUnit("ctrl-forin", CATEGORY_CONTROL_FLOW, ExpectedOutcome.returnValue("ab"));
        staticUnit("ctrl-loops", CATEGORY_CONTROL_FLOW, ExpectedOutcome.returnValue(106));
        staticUnit("exception-throw", CATEGORY_CONTROL_FLOW,
                ExpectedOutcome.exception(ERR_THROW)
                        .errorLocation(SourceLocation.fromLine(
                                STATIC_RESOURCE_BASE + "exception-throw.xpl", 2, 0)));

        // ---- b-output-node-gen（模板单元：$out 通路，期望值 = 输出调用序列——副作用域承载）----
        staticUnit("tpl-text", CATEGORY_OUTPUT_NODE_GEN, ExpectedOutcome.returnValue(null)
                .outputCalls(
                        call(RecordedOutputCall.Op.TEXT, null, "a"),
                        call(RecordedOutputCall.Op.VALUE, loc("tpl-text.xpl", 1, 37), 1),
                        call(RecordedOutputCall.Op.TEXT, null, "a"),
                        call(RecordedOutputCall.Op.VALUE, loc("tpl-text.xpl", 1, 37), 2)),
                UnitForm.TAG_TEXT, null);
        staticUnit("tpl-xml", CATEGORY_OUTPUT_NODE_GEN, ExpectedOutcome.returnValue(null)
                .outputCalls(
                        call(RecordedOutputCall.Op.TEXT, loc("tpl-xml.xpl", 1, 2), "\n<div a=\"1\""),
                        call(RecordedOutputCall.Op.TEXT, null, " "),
                        call(RecordedOutputCall.Op.TEXT, null, "b"),
                        call(RecordedOutputCall.Op.TEXT, loc("tpl-xml.xpl", 1, 15), "=\""),
                        call(RecordedOutputCall.Op.TEXT, loc("tpl-xml.xpl", 1, 15), "5"),
                        call(RecordedOutputCall.Op.TEXT, null, "\""),
                        call(RecordedOutputCall.Op.TEXT, loc("tpl-xml.xpl", 1, 2), ">x</div>")),
                UnitForm.TAG_XML, null);
        staticUnit("tpl-xml-extattrs", CATEGORY_OUTPUT_NODE_GEN, extVars, ExpectedOutcome.returnValue(null)
                .outputCalls(
                        call(RecordedOutputCall.Op.TEXT, loc("tpl-xml-extattrs.xpl", 1, 2), "\n<div a=\"1\""),
                        call(RecordedOutputCall.Op.TEXT, null, " "),
                        call(RecordedOutputCall.Op.TEXT, null, "b"),
                        call(RecordedOutputCall.Op.TEXT, null, "=\""),
                        call(RecordedOutputCall.Op.TEXT, loc("tpl-xml-extattrs.xpl", 1, 23), "2"),
                        call(RecordedOutputCall.Op.TEXT, null, "\""),
                        call(RecordedOutputCall.Op.TEXT, loc("tpl-xml-extattrs.xpl", 1, 2), ">x</div>")),
                UnitForm.TAG_XML, null);
        staticUnit("tpl-node", CATEGORY_OUTPUT_NODE_GEN, ExpectedOutcome.returnValue(null)
                .outputCalls(
                        nodeCall(RecordedOutputCall.Op.BEGIN_NODE, loc("tpl-node.xpl", 1, 2), "a",
                                scopeVars("x", "1")),
                        nodeCall(RecordedOutputCall.Op.BEGIN_NODE, loc("tpl-node.xpl", 1, 11), "b",
                                scopeVars()),
                        call(RecordedOutputCall.Op.VALUE, loc("tpl-node.xpl", 1, 13), "t"),
                        nodeCall(RecordedOutputCall.Op.END_NODE, null, "b", null),
                        nodeCall(RecordedOutputCall.Op.END_NODE, null, "a", null)),
                UnitForm.TAG_NODE, null);
        staticUnit("tpl-node-simple", CATEGORY_OUTPUT_NODE_GEN, ExpectedOutcome.returnValue(null)
                .outputCalls(
                        nodeCall(RecordedOutputCall.Op.SIMPLE_NODE, loc("tpl-node-simple.xpl", 1, 2), "a",
                                scopeVars("x", "1"))),
                UnitForm.TAG_NODE, null);
        staticUnit("tpl-collect-text", CATEGORY_OUTPUT_NODE_GEN, ExpectedOutcome.returnValue("a3c"),
                UnitForm.TAG_NONE, null);
        staticUnit("tpl-collect-node", CATEGORY_OUTPUT_NODE_GEN,
                ExpectedOutcome.returnValue(expectedCollectNode()), UnitForm.TAG_NONE, null);
        staticUnit("tpl-collect-sql", CATEGORY_OUTPUT_NODE_GEN,
                ExpectedOutcome.returnValue(new SQL("select 1")), UnitForm.TAG_NONE, null);
    }

    public static CorpusCoverageB instance() {
        return new CorpusCoverageB();
    }

    public static List<CompareUnit> units() {
        return instance().units;
    }

    static UnitForm unitFormOf(String path) {
        UnitForm form = UNIT_FORMS.get(path);
        return form == null ? UnitForm.SCRIPT : form;
    }

    /**
     * 单元的编译输出模式（I10 生产绑定路径测试消费：经 {@code XLang.parseXpl} 取树时须与
     * corpus 编译器同模式，保证树结构一致供指纹校验与三层对拍）。
     */
    public static XLangOutputMode outputModeOf(String path) {
        switch (unitFormOf(path)) {
            case TAG_TEXT:
                return XLangOutputMode.text;
            case TAG_XML:
                return XLangOutputMode.xml;
            case TAG_NODE:
                return XLangOutputMode.node;
            default:
                return XLangOutputMode.none;
        }
    }

    /**
     * 类别 → 该类别单元的树必须满足的节点规则（每条规则一组等价类，满足其一即可）。
     * b-function-closure 含 CallFuncExecutable（局部函数调用形态的承载类——非根形态与程序入口
     * 同类名，规则强度由基线测试的强成员断言 + LOCAL_FUNCTION_FORM_UNITS 计数断言补足）。
     */
    public static List<Set<String>> getRequiredNodeRules(String category) {
        switch (category) {
            case CATEGORY_FUNCTION_CLOSURE:
                return list(setOf("VarFunctionExecutable", "VarExecutableFunction",
                        "LazyCompiledExecutableFunction", "FunctionalAdapterExecutable",
                        "CallFuncWithClosureExecutable", "BuildFuncRefExecutable",
                        "BuildClosureBodyExecutable", "LocationFunction", "CallFuncExecutable"));
            case CATEGORY_CONTROL_FLOW:
                return list(setOf("IfExecutable", "SwitchExecutable", "ForExecutable", "ForInExecutable",
                        "ForOfExecutable", "WhileExecutable", "DoWhileExecutable", "BreakExecutable",
                        "ContinueExecutable", "ReturnExecutable", "TryExecutable",
                        "ThrowErrorCodeExecutable", "ThrowExceptionExecutable"));
            case CATEGORY_OUTPUT_NODE_GEN:
                return list(setOf("OutputTextExecutable", "OutputValueExecutable", "OutputXmlAttrExecutable",
                        "OutputXmlExtAttrsExecutable", "GenNodeExecutable", "GenNodeAttrExecutable",
                        "GenXJsonExecutable", "CollectJsonExecutable", "CollectNodeExecutable",
                        "CollectSqlExecutable", "CollectTextExecutable", "EscapeOutputExecutable"));
            default:
                throw new IllegalArgumentException("unknown category: " + category);
        }
    }

    /**
     * 类别的强成员集（排除结构性载体 CallFuncExecutable）：每类别至少一个单元的树包含
     * 强成员（防止规则经程序入口包装被平凡满足——基线测试断言）。
     */
    public static Set<String> getStrongMembers(String category) {
        Set<String> rule = new LinkedHashSet<>(getRequiredNodeRules(category).get(0));
        rule.remove("CallFuncExecutable");
        return rule;
    }

    /**
     * corpus 覆盖 B 树允许出现的节点类 = 基线已归属类（java 目标集 + 结构性载体与载荷类——
     * B 族语料的树含 {@code ExecutableFunction} 函数对象载荷，其为已分类的排除分区成员，
     * 不属越界）+ CorpusV1 白名单回退。出现未归属节点 = 单元越界（新鲜度红灯同源判据）。
     */
    public static boolean isAllowedNodeClass(String name) {
        String topName = name.indexOf('$') >= 0 ? name.substring(0, name.indexOf('$')) : name;
        if (ExecNodeBaseline.isClassified(topName))
            return true;
        return CorpusV1.isAllowedNodeClass(name);
    }

    private void staticUnit(String name, String category, ExpectedOutcome expectation) {
        staticUnit(name, category, null, expectation, UnitForm.SCRIPT, null);
    }

    private void staticUnit(String name, String category, ExpectedOutcome expectation,
                            UnitForm form, Void marker) {
        staticUnit(name, category, null, expectation, form, marker);
    }

    private void staticUnit(String name, String category, Map<String, Object> inputVars,
                            ExpectedOutcome expectation) {
        staticUnit(name, category, inputVars, expectation, UnitForm.SCRIPT, null);
    }

    private void staticUnit(String name, String category, Map<String, Object> inputVars,
                            ExpectedOutcome expectation, UnitForm form, Void marker) {
        String path = STATIC_RESOURCE_BASE + name + ".xpl";
        units.add(new CompareUnit(name + "-static-b", CompareUnitKind.STATIC, readResource(path), path,
                inputVars == null ? Map.of() : inputVars, expectation, category));
        UNIT_FORMS.put(path, form);
    }

    private void dynamicUnit(String name, String category, ExpectedOutcome expectation, String source) {
        String path = DYNAMIC_PATH_BASE + name + ".expr";
        units.add(new CompareUnit(name + "-dynamic-b", CompareUnitKind.DYNAMIC, source, path,
                Map.of(), expectation, category));
        UNIT_FORMS.put(path, UnitForm.DYNAMIC);
    }

    private static RecordedOutputCall call(RecordedOutputCall.Op op, SourceLocation loc, Object value) {
        return new RecordedOutputCall(op, loc, value);
    }

    private static RecordedOutputCall nodeCall(RecordedOutputCall.Op op, SourceLocation loc, String tagName,
                                               Map<String, Object> attrs) {
        return new RecordedOutputCall(op, loc, tagName, attrs);
    }

    private static SourceLocation loc(String name, int line, int col) {
        return SourceLocation.fromLine(STATIC_RESOURCE_BASE + name, line, col);
    }

    /** collect-node 期望值：与 CollectXNodeHandler 的虚拟根 "_" 结构一致（值相等按 xml() 内容）。 */
    private static XNode expectedCollectNode() {
        return XNodeParser.instance().parseFromText(
                SourceLocation.fromPath(STATIC_RESOURCE_BASE + "tpl-collect-node.xpl"),
                "<_><a x='1'>t</a></_>");
    }

    static Map<String, Object> scopeVars(Object... kv) {
        Map<String, Object> ret = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2)
            ret.put((String) kv[i], kv[i + 1]);
        return ret;
    }

    public static String readResource(String path) {
        return CorpusCoverageA.readResource(path);
    }

    private static Set<String> setOf(String... names) {
        return new LinkedHashSet<>(Arrays.asList(names));
    }

    @SafeVarargs
    private static List<Set<String>> list(Set<String>... rule) {
        return new ArrayList<>(Arrays.asList(rule));
    }

    /**
     * corpus 覆盖 B 编译器：脚本单元经 `compileXpl` 取树；模板单元经
     * `compileTag(XNode parse, outputMode)` 标准前端取树（Phase 1 §8：outputMode=none 拒绝输出、
     * text/xml/node 模式承载输出语义）；动态单元经 `compileFullExpr`。
     */
    public static final class Compiler implements ICompareUnitCompiler {
        public static final Compiler INSTANCE = new Compiler();

        @Override
        public IExecutableExpression compile(CompareUnit unit) {
            SourceLocation loc = SourceLocation.fromPath(unit.getSourceLocationPath());
            switch (unitFormOf(unit.getSourceLocationPath())) {
                case TAG_NONE:
                    return compileTag(loc, unit, XLangOutputMode.none).getExpr();
                case TAG_TEXT:
                    return compileTag(loc, unit, XLangOutputMode.text).getExpr();
                case TAG_XML:
                    return compileTag(loc, unit, XLangOutputMode.xml).getExpr();
                case TAG_NODE:
                    return compileTag(loc, unit, XLangOutputMode.node).getExpr();
                case DYNAMIC:
                    return XLang.newCompileTool().allowUnregisteredScopeVar(true)
                            .compileFullExpr(loc, unit.getSource()).getExpr();
                case SCRIPT:
                default:
                    return XLang.newCompileTool().allowUnregisteredScopeVar(true)
                            .compileXpl(loc, readResource(unit.getSourceLocationPath())).getExpr();
            }
        }

        private static ExprEvalAction compileTag(SourceLocation loc, CompareUnit unit, XLangOutputMode mode) {
            return XLang.newCompileTool().allowUnregisteredScopeVar(true)
                    .compileTag(XNodeParser.instance().parseFromText(loc, unit.getSource()), mode);
        }
    }
}
