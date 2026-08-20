package io.nop.xlang.compare;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.xlang.api.ExprEvalAction;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.ast.definition.ScopeVarDefinition;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.api.core.ApiErrors.ERR_CONVERT_TO_TYPE_FAIL;
import static io.nop.xlang.XLangErrors.ERR_EXEC_GET_PROP_ON_NULL_OBJ;

/**
 * corpus 覆盖 A（I3）：A 五族 + 并入残余的对应类别单元（`CorpusV1` 22 单元与既有类别不动）。
 *
 * <p>静态形态：测试资源 `xlang-compare/static-a/*.xpl`（c:script 表达式编译单元，经标准编译前端取树）。
 * 动态形态：运行时字符串经既有动态编译出口取树，含两变体——注册可变 scope var 的编译 scope
 * （产生 {@code ScopeSelfInc/ScopeSelfDec/ScopeAssign} 平凡标识符形态；静态单元的 `$scope.x` 成员访问
 * 形态产生 {@code ScopeSelfAssign}）与 `compileTemplateExpr`（产生 {@code ConcatExecutable}）。
 *
 * <p>不可经表达式出口产生的节点族（引用族/InitRef/EnhanceRef/BindVar/Cast/Getter 族/Setter/MakeProperty/
 * VarStatus/DebugIdentifier/GuardNotEmpty/CloneLiteral/BetweenOp/AssertOp/Range/ResolvedObjFunction，
 * 见 I3 plan Phase 1 产生路径盘点）不进 corpus，由转译级合成树测试覆盖（无静默跳过）。
 *
 * <p>单元 schema 四字段齐备（I1 口径）：源 / 初始求值现场声明 / 预期（返回值+副作用或异常：错误码+源位置）/
 * 列适用性（{@link CompareUnitKind} 派生）。
 */
public final class CorpusCoverageA {
    public static final String CATEGORY_SCOPE_CHAIN = "a-scope-chain";
    public static final String CATEGORY_TYPE_OP = "a-type-op";
    public static final String CATEGORY_OBJ_COLLECTION = "a-obj-collection";
    public static final String CATEGORY_BINDING_GUARD_DEBUG = "a-binding-guard-debug";
    public static final String CATEGORY_SLOT_WRITE = "a-slot-write";
    public static final String CATEGORY_RESIDUAL_OPS = "a-residual-ops";

    public static final List<String> CATEGORIES = Collections.unmodifiableList(Arrays.asList(
            CATEGORY_SCOPE_CHAIN, CATEGORY_TYPE_OP, CATEGORY_OBJ_COLLECTION,
            CATEGORY_BINDING_GUARD_DEBUG, CATEGORY_SLOT_WRITE, CATEGORY_RESIDUAL_OPS));

    private static final String STATIC_RESOURCE_BASE = "xlang-compare/static-a/";
    private static final String DYNAMIC_PATH_BASE = "xlang-compare/dynamic-a/";

    private static final String ERR_GET_PROP_ON_NULL = ERR_EXEC_GET_PROP_ON_NULL_OBJ.getErrorCode();
    private static final String ERR_CONVERT_FAIL = ERR_CONVERT_TO_TYPE_FAIL.getErrorCode();

    private final List<CompareUnit> units = new ArrayList<>();

    private CorpusCoverageA() {
        Map<String, Object> xVars = scopeVars("x", 5);
        Map<String, Object> nVars = scopeVars("n", 5);
        Map<String, Object> dVars = scopeVars("d", new Date(0));

        // ---- 静态单元（五族每族 ≥1，硬要求）----
        staticUnit("scope-chain", CATEGORY_SCOPE_CHAIN, xVars, ExpectedOutcome.returnValue(15));
        staticUnit("scope-assign", CATEGORY_SCOPE_CHAIN, nVars,
                ExpectedOutcome.returnValue(7).scopeVars(after(nVars, "n", 7)));
        staticUnit("scope-self-assign", CATEGORY_SCOPE_CHAIN, nVars,
                ExpectedOutcome.returnValue(8).scopeVars(after(nVars, "n", 8)));
        staticUnit("global-var", CATEGORY_SCOPE_CHAIN, ExpectedOutcome.returnValue(3));
        staticUnit("type-convert", CATEGORY_TYPE_OP, ExpectedOutcome.returnValue(13));
        staticUnit("type-instanceof", CATEGORY_TYPE_OP, dVars, ExpectedOutcome.returnValue(Boolean.TRUE));
        staticUnit("typeof", CATEGORY_TYPE_OP, ExpectedOutcome.returnValue("java.lang.Integer"));
        staticUnit("obj-new", CATEGORY_OBJ_COLLECTION, ExpectedOutcome.returnValue(0));
        staticUnit("list-spread", CATEGORY_OBJ_COLLECTION, ExpectedOutcome.returnValue(3));
        staticUnit("map-access", CATEGORY_OBJ_COLLECTION, ExpectedOutcome.returnValue(3));
        staticUnit("map-write", CATEGORY_OBJ_COLLECTION, ExpectedOutcome.returnValue(30));
        staticUnit("exception-prop", CATEGORY_OBJ_COLLECTION,
                ExpectedOutcome.exception(ERR_GET_PROP_ON_NULL)
                        .errorLocation(SourceLocation.fromLine(STATIC_RESOURCE_BASE + "exception-prop.xpl", 3)));
        staticUnit("binding-array", CATEGORY_BINDING_GUARD_DEBUG, ExpectedOutcome.returnValue(3));
        staticUnit("binding-object", CATEGORY_BINDING_GUARD_DEBUG, ExpectedOutcome.returnValue(6));
        staticUnit("debug-call", CATEGORY_BINDING_GUARD_DEBUG, ExpectedOutcome.returnValue("abc"));
        staticUnit("slot-write", CATEGORY_SLOT_WRITE, ExpectedOutcome.returnValue(18));
        staticUnit("residual-ops", CATEGORY_RESIDUAL_OPS, xVars, ExpectedOutcome.returnValue(-2));
        staticUnit("null-checks", CATEGORY_RESIDUAL_OPS, xVars, ExpectedOutcome.returnValue(Boolean.TRUE));
        staticUnit("prop-in", CATEGORY_RESIDUAL_OPS, ExpectedOutcome.returnValue(Boolean.TRUE));
        staticUnit("exception-convert", CATEGORY_TYPE_OP,
                ExpectedOutcome.exception(ERR_CONVERT_FAIL)
                        .errorLocation(SourceLocation.fromLine(STATIC_RESOURCE_BASE + "exception-convert.xpl", 2)));

        // ---- 动态单元（按自然产生能力配比）----
        // 可变 scope var 编译形态：ScopeSelfInc/ScopeSelfDec/ScopeAssign 平凡形态的唯一产生出口（能力记录）
        dynamicUnit("scope-self-inc-dec", CATEGORY_SCOPE_CHAIN, xVars,
                ExpectedOutcome.returnValue(6).scopeVars(after(xVars, "x", 6)),
                CompileMode.MUTABLE_SCOPE_VAR, "x++; x--; x += 1; x");
        dynamicUnit("scope-self-assign-plain", CATEGORY_SCOPE_CHAIN, xVars,
                ExpectedOutcome.returnValue(8).scopeVars(after(xVars, "x", 8)),
                CompileMode.MUTABLE_SCOPE_VAR, "x += 3; x");
        dynamicUnit("scope-assign-plain", CATEGORY_SCOPE_CHAIN, xVars,
                ExpectedOutcome.returnValue(5).scopeVars(after(xVars, "x", 5)),
                CompileMode.MUTABLE_SCOPE_VAR, "x = 5; x");
        // template 出口：ConcatExecutable 的唯一产生形态
        dynamicUnit("template-concat", CATEGORY_RESIDUAL_OPS, ExpectedOutcome.returnValue("a3c"),
                CompileMode.TEMPLATE, "a${1 + 2}c");
        // full-expr 出口镜像（源文本与静态单元同构）
        dynamicUnit("scope-chain", CATEGORY_SCOPE_CHAIN, xVars, ExpectedOutcome.returnValue(15),
                CompileMode.STANDARD, null);
        dynamicUnit("type-convert", CATEGORY_TYPE_OP, ExpectedOutcome.returnValue(13),
                CompileMode.STANDARD, null);
        dynamicUnit("typeof", CATEGORY_TYPE_OP, ExpectedOutcome.returnValue("java.lang.Integer"),
                CompileMode.STANDARD, null);
        dynamicUnit("list-spread", CATEGORY_OBJ_COLLECTION, ExpectedOutcome.returnValue(3),
                CompileMode.STANDARD, null);
        dynamicUnit("map-write", CATEGORY_OBJ_COLLECTION, ExpectedOutcome.returnValue(30),
                CompileMode.STANDARD, null);
        dynamicUnit("binding-array", CATEGORY_BINDING_GUARD_DEBUG, ExpectedOutcome.returnValue(3),
                CompileMode.STANDARD, null);
        dynamicUnit("slot-write", CATEGORY_SLOT_WRITE, ExpectedOutcome.returnValue(18),
                CompileMode.STANDARD, null);
        dynamicUnit("residual-ops", CATEGORY_RESIDUAL_OPS, xVars, ExpectedOutcome.returnValue(-2),
                CompileMode.STANDARD, null);
        dynamicUnit("exception-convert", CATEGORY_TYPE_OP,
                ExpectedOutcome.exception(ERR_CONVERT_FAIL)
                        .errorLocation(SourceLocation.fromLine(DYNAMIC_PATH_BASE + "exception-convert.expr", 1)),
                CompileMode.STANDARD, null);
    }

    public static CorpusCoverageA instance() {
        return new CorpusCoverageA();
    }

    public static List<CompareUnit> units() {
        return instance().units;
    }

    /**
     * 类别 → 该类别单元的树必须满足的节点规则（每条规则一组等价类，满足其一即可）。
     */
    public static List<Set<String>> getRequiredNodeRules(String category) {
        switch (category) {
            case CATEGORY_SCOPE_CHAIN:
                return list(setOf("ScopeIdentifierExecutable", "GlobalVarExecutable",
                        "ScopeAssignExecutable", "ScopeSelfAssignExecutable",
                        "ScopeSelfIncExecutable", "ScopeSelfDecExecutable"));
            case CATEGORY_TYPE_OP:
                return list(setOf("ConvertExecutable", "ConvertWithDefaultExecutable",
                        "InstanceOfExecutable", "TypeOfExecutable", "CastExecutable"));
            case CATEGORY_OBJ_COLLECTION:
                return list(setOf("NewObjectExecutable", "NewListExecutable", "NewMapExecutable",
                        "GetPropertyExecutable", "StaticGetterGetPropertyExecutable",
                        "SetPropertyExecutable", "GetAttrExecutable", "SetAttrExecutable",
                        "SelfAssignPropertyExecutable", "SelfAssignAttrExecutable",
                        "ListItemExecutable", "MapItemExecutable"));
            case CATEGORY_BINDING_GUARD_DEBUG:
                return list(setOf("ArrayBindingAssignExecutable", "ObjectBindingAssignExecutable",
                        "BindVarExecutable", "GuardNotEmptyExecutable",
                        "DebugExecutable", "DebugIdentifierExecutable", "VarStatusExecutable"));
            case CATEGORY_SLOT_WRITE:
                return list(setOf("SelfAssignExecutable", "SelfAssignAttrExecutable",
                        "SelfAssignPropertyExecutable", "SelfIncExecutable", "SelfDecExecutable",
                        "SlotAssignExecutable"));
            case CATEGORY_RESIDUAL_OPS:
                return list(setOf("CloneLiteralExecutable", "NegExecutable", "BitNotExecutable",
                        "NullCoalesceExecutable", "BetweenOpExecutable", "AssertOpExecutable",
                        "ConcatExecutable", "RangeExecutable", "PropInExecutable",
                        "EqNullExecutable", "NeNullExecutable", "StrictEqNullExecutable",
                        "StrictNeNullExecutable", "BinaryExecutable", "ResolvedObjFunctionExecutable"));
            default:
                throw new IllegalArgumentException("unknown category: " + category);
        }
    }

    /**
     * corpus 覆盖 A 树允许出现的节点类 = {@code ExecNodeBaseline.registeredTarget()}（I2 子集 +
     * A 五族 + 并入残余，单一事实源，嵌套同族变体按宿主顶层类归并）+ CorpusV1 白名单回退
     * （结构性载体注记）。出现白名单外节点 = 单元越界（含 B 族节点 = I3 纪律失败）。
     */
    public static boolean isAllowedNodeClass(String name) {
        String topName = name.indexOf('$') >= 0 ? name.substring(0, name.indexOf('$')) : name;
        if (ExecNodeBaseline.registeredTarget().contains(topName))
            return true;
        return CorpusV1.isAllowedNodeClass(name);
    }

    private void staticUnit(String name, String category, ExpectedOutcome expectation) {
        staticUnit(name, category, null, expectation);
    }

    private void staticUnit(String name, String category, Map<String, Object> inputVars,
                            ExpectedOutcome expectation) {
        String path = STATIC_RESOURCE_BASE + name + ".xpl";
        units.add(new CompareUnit(name + "-static-a", CompareUnitKind.STATIC, readResource(path), path,
                inputVars == null ? Map.of() : inputVars, expectation, category));
    }

    private void dynamicUnit(String name, String category, ExpectedOutcome expectation,
                             CompileMode mode, String explicitSource) {
        dynamicUnit(name, category, null, expectation, mode, explicitSource);
    }

    private void dynamicUnit(String name, String category, Map<String, Object> inputVars,
                             ExpectedOutcome expectation, CompileMode mode, String explicitSource) {
        String path = DYNAMIC_PATH_BASE + name + ".expr";
        String source = explicitSource != null ? explicitSource : dynamicSourceFromStatic(name);
        units.add(new CompareUnit(name + "-dynamic-a", CompareUnitKind.DYNAMIC, source, path,
                inputVars == null ? Map.of() : inputVars, expectation, category));
        compileModes.put(path, mode);
    }

    private static final Map<String, CompileMode> compileModes = new LinkedHashMap<>();

    enum CompileMode {
        STANDARD, MUTABLE_SCOPE_VAR, TEMPLATE
    }

    static CompileMode compileModeOf(String path) {
        CompileMode mode = compileModes.get(path);
        return mode == null ? CompileMode.STANDARD : mode;
    }

    static Map<String, Object> scopeVars(Object... kv) {
        Map<String, Object> ret = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2)
            ret.put((String) kv[i], kv[i + 1]);
        return ret;
    }

    private static Map<String, Object> after(Map<String, Object> base, String key, Object value) {
        Map<String, Object> ret = new LinkedHashMap<>(base);
        ret.put(key, value);
        return ret;
    }

    private static String dynamicSourceFromStatic(String name) {
        String staticText = readResource(STATIC_RESOURCE_BASE + name + ".xpl");
        String body = staticText.replace("<c:script>", "").replace("</c:script>", "").trim();
        return io.nop.commons.util.StringHelper.unescapeXml(body);
    }

    public static String readResource(String path) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null)
            cl = CorpusCoverageA.class.getClassLoader();
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
     * corpus 覆盖 A 编译器：静态单元经标准编译前端取树；动态单元按单元形态选择动态编译出口
     * （标准 full-expr / 可变 scope var 编译 scope / template 出口）。
     */
    public static final class Compiler implements ICompareUnitCompiler {
        public static final Compiler INSTANCE = new Compiler();

        @Override
        public IExecutableExpression compile(CompareUnit unit) {
            if (unit.getKind() == CompareUnitKind.STATIC)
                return compileStatic(unit);
            return compileDynamic(unit);
        }

        private IExecutableExpression compileStatic(CompareUnit unit) {
            String source = readResource(unit.getSourceLocationPath());
            SourceLocation loc = SourceLocation.fromPath(unit.getSourceLocationPath());
            ExprEvalAction action = XLang.newCompileTool().compileXpl(loc, source);
            return action.getExpr();
        }

        private IExecutableExpression compileDynamic(CompareUnit unit) {
            SourceLocation loc = SourceLocation.fromPath(unit.getSourceLocationPath());
            switch (compileModeOf(unit.getSourceLocationPath())) {
                case MUTABLE_SCOPE_VAR: {
                    XLangCompileTool tool = XLang.newCompileTool().allowUnregisteredScopeVar(true);
                    tool.getScope().registerScopeVarDefinition(
                            ScopeVarDefinition.mutable("x", null), false);
                    return tool.compileFullExpr(loc, unit.getSource()).getExpr();
                }
                case TEMPLATE:
                    return XLang.newCompileTool().allowUnregisteredScopeVar(true)
                            .compileTemplateExpr(loc, unit.getSource()).getExpr();
                case STANDARD:
                default:
                    return XLang.newCompileTool().allowUnregisteredScopeVar(true)
                            .compileFullExpr(loc, unit.getSource()).getExpr();
            }
        }
    }
}
