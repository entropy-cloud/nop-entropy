package io.nop.xlang.compare;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.xlang.api.ExprEvalAction;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XLangCompileTool;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.xlang.XLangErrors.ERR_EXEC_INVOKE_METHOD_FAIL;

/**
 * corpus v1：表达式子集 6 类（字面量 / slot 标识符 / 算术 / 逻辑 / 比较 / 简单方法调用）
 * × 静态/动态双形态，每类每形态 ≥1 单元，另含跨类组合单元（静态/动态各 ≥1）。
 *
 * <p>静态形态：测试资源 `xlang-compare/static/*.xpl`（c:script 表达式编译单元），
 * 经标准编译前端（XplCompiler + 宏展开 + LexicalScopeAnalysis）取树。
 * 动态形态：运行时字符串表达式经既有动态编译出口（XLangCompileTool.compileSimpleExpr /
 * compileFullExpr 系）取树，编译时传入非 null 合成 SourceLocation。
 *
 * <p>子集语义边界（与 I2/I5 转译范围对齐）：算术类含字符串拼接语义单元（PlusExecutable
 * 的 String 分支）；简单方法调用 = 宿主方法反射分派（ObjFunctionExecutable/StaticFunctionExecutable
 * 系，含静态方法调用），不含 XLang 局部函数定义/调用与函数字面量（CallFunc 族/闭包，属 I4）。
 *
 * <p>结构性载体注记：let 声明（slot 写，I3 族）与 CallFuncExecutable 程序入口包装是
 * slot 标识符读取与 xpl script 单元的唯一最小产生载体；ReturnNullExecutable（语句位置
 * 表达式的编译包装）与 GuardNotNullExecutable（成员调用的接收者 null 守卫包装）为标准
 * 编译前端对子集语义自动生成的包装节点。本 corpus 以"body 仅含子集节点 + 结构性载体"
 * 为口径；单元按其标识符读取/表达式语义归类（见 {@link #getRequiredNodeClasses}）。
 */
public final class CorpusV1 {
    public static final String CATEGORY_LITERAL = "literal";
    public static final String CATEGORY_SLOT_IDENTIFIER = "slot-identifier";
    public static final String CATEGORY_ARITHMETIC = "arithmetic";
    public static final String CATEGORY_LOGIC = "logic";
    public static final String CATEGORY_COMPARISON = "comparison";
    public static final String CATEGORY_METHOD_CALL = "method-call";
    public static final String CATEGORY_COMBO = "combo";

    public static final List<String> CATEGORIES = Collections.unmodifiableList(Arrays.asList(
            CATEGORY_LITERAL, CATEGORY_SLOT_IDENTIFIER, CATEGORY_ARITHMETIC, CATEGORY_LOGIC,
            CATEGORY_COMPARISON, CATEGORY_METHOD_CALL));

    private static final String ERR_INVOKE_METHOD_FAIL = ERR_EXEC_INVOKE_METHOD_FAIL.getErrorCode();

    private static final String STATIC_RESOURCE_BASE = "xlang-compare/static/";
    private static final String DYNAMIC_PATH_BASE = "xlang-compare/dynamic/";

    private final List<CompareUnit> units = new ArrayList<>();

    private CorpusV1() {
        Map<String, Object> assignResult = new LinkedHashMap<>();
        assignResult.put("result", 3);

        unit("literal-int", CompareUnitKind.STATIC, CATEGORY_LITERAL,
                ExpectedOutcome.returnValue(3));
        unit("literal-string", CompareUnitKind.STATIC, CATEGORY_LITERAL,
                ExpectedOutcome.returnValue("xlang"));
        unit("slot-identifier", CompareUnitKind.STATIC, CATEGORY_SLOT_IDENTIFIER,
                ExpectedOutcome.returnValue(11));
        unit("arith-plus", CompareUnitKind.STATIC, CATEGORY_ARITHMETIC,
                ExpectedOutcome.returnValue(7));
        unit("arith-string-concat", CompareUnitKind.STATIC, CATEGORY_ARITHMETIC,
                ExpectedOutcome.returnValue("ab!"));
        unit("logic-and-or", CompareUnitKind.STATIC, CATEGORY_LOGIC,
                ExpectedOutcome.returnValue(Boolean.TRUE));
        unit("compare-lt", CompareUnitKind.STATIC, CATEGORY_COMPARISON,
                ExpectedOutcome.returnValue(Boolean.TRUE));
        unit("method-instance", CompareUnitKind.STATIC, CATEGORY_METHOD_CALL,
                ExpectedOutcome.returnValue("HELLO"));
        unit("method-static-side-effect", CompareUnitKind.STATIC, CATEGORY_METHOD_CALL,
                ExpectedOutcome.returnValue(null).scopeVars(assignResult));
        unit("exception-method", CompareUnitKind.STATIC, CATEGORY_METHOD_CALL,
                ExpectedOutcome.exception(ERR_INVOKE_METHOD_FAIL)
                        .errorLocation(SourceLocation.fromLine(STATIC_RESOURCE_BASE + "exception-method.xpl", 2)));
        unit("combo", CompareUnitKind.STATIC, CATEGORY_COMBO,
                ExpectedOutcome.returnValue(Boolean.TRUE));

        unit("literal-int", CompareUnitKind.DYNAMIC, CATEGORY_LITERAL,
                ExpectedOutcome.returnValue(3));
        unit("literal-string", CompareUnitKind.DYNAMIC, CATEGORY_LITERAL,
                ExpectedOutcome.returnValue("xlang"));
        unit("slot-identifier", CompareUnitKind.DYNAMIC, CATEGORY_SLOT_IDENTIFIER,
                ExpectedOutcome.returnValue(11));
        unit("arith-plus", CompareUnitKind.DYNAMIC, CATEGORY_ARITHMETIC,
                ExpectedOutcome.returnValue(7));
        unit("arith-string-concat", CompareUnitKind.DYNAMIC, CATEGORY_ARITHMETIC,
                ExpectedOutcome.returnValue("ab!"));
        unit("logic-and-or", CompareUnitKind.DYNAMIC, CATEGORY_LOGIC,
                ExpectedOutcome.returnValue(Boolean.TRUE));
        unit("compare-lt", CompareUnitKind.DYNAMIC, CATEGORY_COMPARISON,
                ExpectedOutcome.returnValue(Boolean.TRUE));
        unit("method-instance", CompareUnitKind.DYNAMIC, CATEGORY_METHOD_CALL,
                ExpectedOutcome.returnValue("HELLO"));
        unit("method-static-side-effect", CompareUnitKind.DYNAMIC, CATEGORY_METHOD_CALL,
                ExpectedOutcome.returnValue(null).scopeVars(assignResult));
        unit("exception-method", CompareUnitKind.DYNAMIC, CATEGORY_METHOD_CALL,
                ExpectedOutcome.exception(ERR_INVOKE_METHOD_FAIL)
                        .errorLocation(SourceLocation.fromLine(DYNAMIC_PATH_BASE + "exception-method.expr", 1)));
        unit("combo", CompareUnitKind.DYNAMIC, CATEGORY_COMBO,
                ExpectedOutcome.returnValue(Boolean.TRUE));
    }

    public static CorpusV1 instance() {
        return new CorpusV1();
    }

    public static List<CompareUnit> units() {
        return instance().units;
    }

    /**
     * 类别 → 该类别单元的树必须满足的节点规则（每条规则一组等价类，满足其一即可）。
     * repo-observable 的类别覆盖证据。
     */
    public static List<Set<String>> getRequiredNodeRules(String category) {
        switch (category) {
            case CATEGORY_LITERAL:
                return list(setOf("LiteralExecutable"));
            case CATEGORY_SLOT_IDENTIFIER:
                return list(setOf("SlotIdentifierExecutable"));
            case CATEGORY_ARITHMETIC:
                return list(setOf("PlusExecutable"));
            case CATEGORY_LOGIC:
                return list(setOf("AndExecutable"));
            case CATEGORY_COMPARISON:
                return list(setOf("LtExecutable"));
            case CATEGORY_METHOD_CALL:
                return list(setOf("ObjFunctionExecutable", "FunctionExecutable", "StaticFunctionExecutable"));
            case CATEGORY_COMBO:
                return list(setOf("SlotIdentifierExecutable"), setOf("GtExecutable"), setOf("PlusExecutable"),
                        setOf("EqExecutable"), setOf("AndExecutable"));
            default:
                throw new IllegalArgumentException("unknown category: " + category);
        }
    }

    /**
     * corpus v1 树允许出现的节点类：子集节点 + 结构性载体（程序入口/序列/块/slot 写/
     * 语句包装/成员调用 null 守卫）。出现白名单外节点 = 单元越界（子集纪律失败）。
     */
    public static boolean isAllowedNodeClass(String name) {
        switch (name) {
            case "LiteralExecutable":
            case "SlotIdentifierExecutable":
            case "PlusExecutable":
            case "MinusExecutable":
            case "MultiplyExecutable":
            case "DivideExecutable":
            case "AndExecutable":
            case "OrExecutable":
            case "NotExecutable":
            case "CompareOpExecutable":
            case "EqExecutable":
            case "NeExecutable":
            case "GtExecutable":
            case "GeExecutable":
            case "LtExecutable":
            case "LeExecutable":
            case "StrictEqExecutable":
            case "StrictNeExecutable":
            case "NullExecutable":
            case "CallFuncExecutable":
            case "BlockExecutable":
            case "SeqExecutable":
            case "SlotAssignExecutable":
            case "ReturnNullExecutable":
            case "GuardNotNullExecutable":
                return true;
            default:
                return name.startsWith("SeqExecutable$")
                        || name.startsWith("ObjFunctionExecutable")
                        || name.startsWith("FunctionExecutable")
                        || name.startsWith("StaticFunctionExecutable");
        }
    }

    private void unit(String name, CompareUnitKind kind, String category, ExpectedOutcome expectation) {
        if (kind == CompareUnitKind.STATIC) {
            String path = STATIC_RESOURCE_BASE + name + ".xpl";
            units.add(new CompareUnit(name + "-static", kind, path, path, Map.of(), expectation, category));
        } else {
            String path = DYNAMIC_PATH_BASE + name + ".expr";
            units.add(new CompareUnit(name + "-dynamic", kind, dynamicSource(name), path,
                    Map.of(), expectation, category));
        }
    }

    private String dynamicSource(String name) {
        String staticText = readResource(STATIC_RESOURCE_BASE + name + ".xpl");
        String body = staticText.replace("<c:script>", "").replace("</c:script>", "").trim();
        return StringHelper.unescapeXml(body);
    }

    public static String readResource(String path) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null)
            cl = CorpusV1.class.getClassLoader();
        try (InputStream in = cl.getResourceAsStream(path)) {
            if (in == null)
                throw new IllegalArgumentException("corpus resource not found: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("read corpus resource failed: " + path, e);
        }
    }

    private static Set<String> setOf(String... names) {
        return new LinkedHashSet<>(Arrays.asList(names));
    }

    @SafeVarargs
    private static List<Set<String>> list(Set<String>... rule) {
        return new ArrayList<>(Arrays.asList(rule));
    }

    /**
     * corpus v1 编译器：静态单元经标准编译前端取树；动态单元经动态编译出口取树。
     */
    public static final class Compiler implements ICompareUnitCompiler {
        public static final Compiler INSTANCE = new Compiler();

        private static final Set<String> FULL_EXPR_UNIT_NAMES = setOf("slot-identifier-dynamic", "combo-dynamic");

        @Override
        public IExecutableExpression compile(CompareUnit unit) {
            if (unit.getKind() == CompareUnitKind.STATIC)
                return compileStatic(unit);
            return compileDynamic(unit);
        }

        private IExecutableExpression compileStatic(CompareUnit unit) {
            String source = readResource(unit.getSource());
            SourceLocation loc = SourceLocation.fromPath(unit.getSourceLocationPath());
            ExprEvalAction action = XLang.newCompileTool().compileXpl(loc, source);
            return action.getExpr();
        }

        private IExecutableExpression compileDynamic(CompareUnit unit) {
            SourceLocation loc = SourceLocation.fromPath(unit.getSourceLocationPath());
            XLangCompileTool tool = XLang.newCompileTool().allowUnregisteredScopeVar(true);
            ExprEvalAction action = FULL_EXPR_UNIT_NAMES.contains(unit.getName())
                    ? tool.compileFullExpr(loc, unit.getSource())
                    : tool.compileSimpleExpr(loc, unit.getSource());
            return action.getExpr();
        }
    }
}
