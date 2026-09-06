package io.nop.xlang.java.translator;

import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.model.query.FilterOp;
import io.nop.core.reflect.ReflectionManager;
import io.nop.xlang.compare.CompareUnit;
import io.nop.xlang.compare.CorpusV1;
import io.nop.xlang.exec.AndExecutable;
import io.nop.xlang.exec.BlockExecutable;
import io.nop.xlang.exec.CallFuncExecutable;
import io.nop.xlang.exec.CompareOpExecutable;
import io.nop.xlang.exec.DivideExecutable;
import io.nop.xlang.exec.GeExecutable;
import io.nop.xlang.exec.LeExecutable;
import io.nop.xlang.exec.LiteralExecutable;
import io.nop.xlang.exec.MinusExecutable;
import io.nop.xlang.exec.NeExecutable;
import io.nop.xlang.exec.NotExecutable;
import io.nop.xlang.exec.NullExecutable;
import io.nop.xlang.exec.ObjFunctionExecutable;
import io.nop.xlang.exec.PlusExecutable;
import io.nop.xlang.exec.SeqExecutable;
import io.nop.xlang.exec.StaticFunctionExecutable;
import io.nop.xlang.exec.StrictEqExecutable;
import io.nop.xlang.exec.StrictNeExecutable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 表达式子集转译器：子集内每类节点至少 1 个生成源码文本断言（corpus v1 单元 + 合成树），
 * fail-fast 断言（子集外节点报节点类名 + SourceLocation；CallFunc 族局部函数调用被排除）。
 */
public class TestExecToJavaTranslatorSource {

    private static final ExecToJavaTranslator TRANSLATOR = new ExecToJavaTranslator();

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    // ------------------------------------------------------------------
    // corpus v1 静态单元（经标准编译前端取树）
    // ------------------------------------------------------------------

    @Test
    public void testLiteralInt() {
        String code = translateCorpusUnit("literal-int");
        assertTrue(code.contains("// source: xlang-compare/static/literal-int.xpl"), code);
        assertTrue(code.contains("return Integer.valueOf(3);"), code);
    }

    @Test
    public void testLiteralString() {
        String code = translateCorpusUnit("literal-string");
        assertTrue(code.contains("return \"xlang\";"), code);
    }

    @Test
    public void testSlotIdentifier() {
        String code = translateCorpusUnit("slot-identifier");
        assertTrue(code.contains("Object $v0 = null; // slot 0"), code);
        assertTrue(code.contains("$v0 = Integer.valueOf(5);"), code);
        assertTrue(code.contains("return XLangSemantics.plus(XLangSemantics.multiply($v0, Integer.valueOf(2)), Integer.valueOf(1));"),
                code);
    }

    @Test
    public void testArithPlus() {
        String code = translateCorpusUnit("arith-plus");
        assertTrue(code.contains("XLangSemantics.plus(Integer.valueOf(1), XLangSemantics.multiply(Integer.valueOf(2), Integer.valueOf(3)))"),
                code);
    }

    @Test
    public void testArithStringConcat() {
        String code = translateCorpusUnit("arith-string-concat");
        assertTrue(code.contains("return XLangSemantics.plus(XLangSemantics.plus(\"a\", \"b\"), \"!\");"), code);
    }

    @Test
    public void testLogicAndOr() {
        String code = translateCorpusUnit("logic-and-or");
        assertTrue(code.contains("if (XLangSemantics.truthy(Boolean.TRUE)) {"), code);
        assertTrue(code.contains("if (!XLangSemantics.truthy($t0)) {"), code);
        assertTrue(code.contains("return $t1;"), code);
    }

    @Test
    public void testCompareLt() {
        String code = translateCorpusUnit("compare-lt");
        assertTrue(code.contains("XLangSemantics.lt(Integer.valueOf(2), Integer.valueOf(3))"), code);
    }

    @Test
    public void testMethodInstance() {
        String code = translateCorpusUnit("method-instance");
        // 接收者经 GuardNotNull 守卫（提升为临时变量，只求值一次）
        assertTrue(code.contains("Object $t0 = XLangSemantics.guardNotNull(LOC_0,"), code);
        // 接收者 null 短路：null 时实参不求值
        assertTrue(code.contains("if ($t0 == null) {"), code);
        assertTrue(code.contains("XLangSemantics.invokeObjMethod(LOC_0, \"\\\"hello\\\"!.toUpperCase()\", $t0, \"toUpperCase\", new Object[]{}, $scope);"),
                code);
    }

    @Test
    public void testMethodStaticSideEffect() {
        String code = translateCorpusUnit("method-static-side-effect");
        assertTrue(code.contains("XLangSemantics.invokeGlobalFunction(LOC_0,"), code);
        assertTrue(code.contains("\"assign\", new Object[]{\"result\", XLangSemantics.plus(Integer.valueOf(1), Integer.valueOf(2))}, $scope);"),
                code);
    }

    @Test
    public void testExceptionMethodEmbedsSourceLocationConstant() {
        String code = translateCorpusUnit("exception-method");
        assertTrue(code.contains("private static final SourceLocation LOC_0 = SourceLocation.fromLine(\"xlang-compare/static/exception-method.xpl\", 2, 0);"),
                code);
        assertTrue(code.contains("XLangSemantics.invokeObjMethod(LOC_0,"), code);
    }

    @Test
    public void testCombo() {
        String code = translateCorpusUnit("combo");
        assertTrue(code.contains("XLangSemantics.gt($v0, Integer.valueOf(2))"), code);
        assertTrue(code.contains("XLangSemantics.eq(XLangSemantics.plus(\"v\", $v0), \"v3\")"), code);
        // And 左操作数为调用形态：先提升为临时变量（只求值一次），再进真值分支
        assertTrue(code.contains("Object $t0 = XLangSemantics.gt($v0, Integer.valueOf(2));"), code);
        assertTrue(code.contains("if (XLangSemantics.truthy($t0)) {"), code);
    }

    @Test
    public void testProgramEntryShape() {
        String code = translateCorpusUnit("literal-int");
        assertTrue(code.contains("package io.nop.xlang.gen;"), code);
        assertTrue(code.contains("public static Object execute(IEvalScope $scope) {"), code);
        assertTrue(code.contains("public final class Gen_xlang_compare_static_literal_int_xpl {"), code);
    }

    // ------------------------------------------------------------------
    // 合成树（corpus 未直接产生形态的子集节点类）
    // ------------------------------------------------------------------

    private static final SourceLocation LOC = SourceLocation.fromLine("synthetic.x", 1);

    private static String translateSynthetic(String path, IExecutableExpression tree) {
        return TRANSLATOR.translate(path, tree).getCode();
    }

    @Test
    public void testMinusAndDivide() {
        String code = translateSynthetic("synthetic/arith.xpl", new MinusExecutable(LOC,
                LiteralExecutable.build(LOC, 5), LiteralExecutable.build(LOC, 2)));
        assertTrue(code.contains("return XLangSemantics.minus(Integer.valueOf(5), Integer.valueOf(2));"), code);

        code = translateSynthetic("synthetic/arith.xpl", new DivideExecutable(LOC,
                LiteralExecutable.build(LOC, 6), LiteralExecutable.build(LOC, 2)));
        assertTrue(code.contains("return XLangSemantics.divide(Integer.valueOf(6), Integer.valueOf(2));"), code);
    }

    @Test
    public void testComparisonVariants() {
        String code = translateSynthetic("synthetic/ne.xpl", new NeExecutable(LOC,
                LiteralExecutable.build(LOC, 1), LiteralExecutable.build(LOC, 2)));
        assertTrue(code.contains("XLangSemantics.ne("), code);

        code = translateSynthetic("synthetic/ge.xpl", new GeExecutable(LOC,
                LiteralExecutable.build(LOC, 1), LiteralExecutable.build(LOC, 2)));
        assertTrue(code.contains("XLangSemantics.ge("), code);

        code = translateSynthetic("synthetic/le.xpl", new LeExecutable(LOC,
                LiteralExecutable.build(LOC, 1), LiteralExecutable.build(LOC, 2)));
        assertTrue(code.contains("XLangSemantics.le("), code);

        // Strict 变体与宽松变体 live 同实现（均走 xlangEq 族共享 helper）
        code = translateSynthetic("synthetic/seq.xpl", new StrictEqExecutable(LOC,
                LiteralExecutable.build(LOC, 1), LiteralExecutable.build(LOC, 1)));
        assertTrue(code.contains("XLangSemantics.eq("), code);

        code = translateSynthetic("synthetic/sne.xpl", new StrictNeExecutable(LOC,
                LiteralExecutable.build(LOC, 1), LiteralExecutable.build(LOC, 1)));
        assertTrue(code.contains("XLangSemantics.ne("), code);
    }

    @Test
    public void testNot() {
        String code = translateSynthetic("synthetic/not.xpl",
                new NotExecutable(LOC, LiteralExecutable.build(LOC, Boolean.TRUE)));
        assertTrue(code.contains("return !XLangSemantics.truthy(Boolean.TRUE);"), code);
    }

    @Test
    public void testCompareOp() {
        String code = translateSynthetic("synthetic/filter-op.xpl",
                new CompareOpExecutable(LOC, FilterOp.GT,
                        LiteralExecutable.build(LOC, 1), LiteralExecutable.build(LOC, 2)));
        assertTrue(code.contains("io.nop.core.model.query.FilterOp.gt.getBiPredicate().test(Integer.valueOf(1), Integer.valueOf(2))"),
                code);
    }

    @Test
    public void testNullExecutable() {
        String code = translateSynthetic("synthetic/null.xpl", NullExecutable.NULL);
        assertTrue(code.contains("return null;"), code);
    }

    @Test
    public void testSeqAndBlock() {
        String code = translateSynthetic("synthetic/seq.xpl", SeqExecutable.valueOf(LOC,
                new IExecutableExpression[]{LiteralExecutable.build(LOC, 1), LiteralExecutable.build(LOC, 2)}));
        assertTrue(code.contains("return Integer.valueOf(2);"), code);

        code = translateSynthetic("synthetic/block.xpl", BlockExecutable.valueOf(LOC,
                new IExecutableExpression[]{LiteralExecutable.build(LOC, 1), LiteralExecutable.build(LOC, 2)}));
        assertTrue(code.contains("return null;"), code);
    }

    @Test
    public void testObjFunctionNullReceiverShortCircuit() {
        String code = translateSynthetic("synthetic/obj-fn.xpl", ObjFunctionExecutable.build(LOC,
                LiteralExecutable.build(LOC, "ab"), "length", false, new IExecutableExpression[0]));
        assertTrue(code.contains("if (\"ab\" == null) {"), code);
        assertTrue(code.contains("XLangSemantics.invokeObjMethod(LOC_0, \"\\\"ab\\\".length()\", \"ab\", \"length\", new Object[]{}, $scope);"),
                code);
    }

    @Test
    public void testStaticFunctionExecutable() {
        String code = translateSynthetic("synthetic/static-fn.xpl", new StaticFunctionExecutable(LOC,
                "java.lang.Math", "max", false,
                ReflectionManager.instance().getClassModel(Math.class).getStaticMethodsByName("max"),
                new IExecutableExpression[]{LiteralExecutable.build(LOC, 3), LiteralExecutable.build(LOC, 4)}));
        assertTrue(code.contains("XLangSemantics.invokeStaticMethodResolved(LOC_0,"), code);
        assertTrue(code.contains("\"java.lang.Math\", \"max\", false, new Object[]{Integer.valueOf(3), Integer.valueOf(4)}, $scope)"),
                code);
    }

    @Test
    public void testAndExecutableShortCircuitShape() {
        String code = translateSynthetic("synthetic/and.xpl", new AndExecutable(LOC,
                LiteralExecutable.build(LOC, Boolean.TRUE), LiteralExecutable.build(LOC, "x")));
        assertTrue(code.contains("if (XLangSemantics.truthy(Boolean.TRUE)) {"), code);
        assertTrue(code.contains("$t0 = \"x\";"), code);
        assertTrue(code.contains("$t0 = Boolean.TRUE;"), code);
    }

    /**
     * check2 P2：Float NaN/±Infinity 不是合法数字字面量（String.valueOf 为 "NaN"/"Infinity"），
     * 必须特判为 Float 类静态常量——否则生成不可编译源码（javac 失败难定位 / --check 模式漂移）。
     */
    @Test
    public void testFloatNaNAndInfinityLiterals() {
        String code = translateSynthetic("synthetic/float-nan.xpl",
                LiteralExecutable.build(LOC, Float.NaN));
        assertTrue(code.contains("return Float.NaN;"), code);

        code = translateSynthetic("synthetic/float-pos-inf.xpl",
                LiteralExecutable.build(LOC, Float.POSITIVE_INFINITY));
        assertTrue(code.contains("return Float.POSITIVE_INFINITY;"), code);

        code = translateSynthetic("synthetic/float-neg-inf.xpl",
                LiteralExecutable.build(LOC, Float.NEGATIVE_INFINITY));
        assertTrue(code.contains("return Float.NEGATIVE_INFINITY;"), code);

        // 有限 Float 字面量不受影响（1.5F 合法）
        code = translateSynthetic("synthetic/float-finite.xpl",
                LiteralExecutable.build(LOC, 1.5f));
        assertTrue(code.contains("return Float.valueOf(1.5F);"), code);
    }

    /**
     * check2 P3：wrapCallFuncException 首参（stackObj）传 LOC 常量（SourceLocation 语义载体，
     * toString 携带 path:line:col），与解释器传节点对象（toString 为 display@loc）的契约对齐；
     * display 字符串只作 ARG_EXPR 参数。
     */
    @Test
    public void testWrapCallFuncExceptionPassesLocationConstantAsStackObj() {
        CallFuncExecutable localCall = new CallFuncExecutable(LOC, "myFn",
                new String[]{"x"}, new IExecutableExpression[0], LiteralExecutable.build(LOC, 1));
        IExecutableExpression tree = new PlusExecutable(LOC,
                LiteralExecutable.build(LOC, 1), localCall);
        String code = TRANSLATOR.translate("synthetic/stack-obj.xpl", tree).getCode();
        // 首参必须以 LOC_ 常量开头，而非 display 字符串字面量
        int idx = code.indexOf("XLangSemantics.wrapCallFuncException(");
        assertTrue(idx >= 0, code);
        String callSite = code.substring(idx, Math.min(code.length(), idx + 90));
        assertTrue(callSite.startsWith("XLangSemantics.wrapCallFuncException(LOC_"), callSite);
    }

    // ------------------------------------------------------------------
    // fail-fast：子集外节点报节点类名 + SourceLocation；禁止部分生成
    // ------------------------------------------------------------------

    @Test
    public void testFailFastOutOfSubsetNode() {
        // IfExecutable 已并入覆盖 B 转译；新形态边界反例 = 控制跳转节点出现在表达式位置
        // （前端语法不可能形态，I4 覆盖 B 收口后的 fail-fast 边界）
        IExecutableExpression tree = new PlusExecutable(LOC,
                LiteralExecutable.build(LOC, 1),
                new io.nop.xlang.exec.BreakExecutable(LOC));
        NopEvalException err = assertThrows(NopEvalException.class,
                () -> TRANSLATOR.translate("synthetic/break-node.xpl", tree));
        assertEquals("nop.err.xlang.exec.translate-unsupported-node", err.getErrorCode());
        assertTrue(err.getParam("className").toString().contains("BreakExecutable"), err.toString());
        assertTrue(String.valueOf(err.getParam("detail")).contains("expression position"), err.toString());
    }

    @Test
    public void testFailFastNestedCallFuncIsLocalFunctionCall() {
        // 非根 CallFunc = 局部函数调用（I4 裁定并入转译，不再是 fail-fast 边界）——
        // 反例随边界收缩迁移：改判排除类 ReturnScopeValuesExecutable（I4 边缘裁定）保持 fail-fast
        CallFuncExecutable localCall = new CallFuncExecutable(LOC, "myFn",
                new String[]{"x"}, new IExecutableExpression[0], LiteralExecutable.build(LOC, 1));
        IExecutableExpression tree = new PlusExecutable(LOC,
                LiteralExecutable.build(LOC, 1), localCall);
        String code = TRANSLATOR.translate("synthetic/local-call.xpl", tree).getCode();
        assertTrue(code.contains("$fn_1($scope"), code);

        IExecutableExpression excluded = new io.nop.xlang.exec.ReturnScopeValuesExecutable(LOC,
                java.util.Collections.emptyList(),
                java.util.List.of(LiteralExecutable.build(LOC, 1)));
        NopEvalException err = assertThrows(NopEvalException.class,
                () -> TRANSLATOR.translate("synthetic/excluded-node.xpl", excluded));
        assertEquals("nop.err.xlang.exec.translate-unsupported-node", err.getErrorCode());
        assertTrue(err.getParam("className").toString().contains("ReturnScopeValuesExecutable"), err.toString());
    }

    @Test
    public void testFailFastProgramEntryWithArguments() {
        CallFuncExecutable entry = new CallFuncExecutable(LOC, "__fn_1",
                new String[0], new IExecutableExpression[]{LiteralExecutable.build(LOC, 1)},
                LiteralExecutable.build(LOC, 2));
        NopEvalException err = assertThrows(NopEvalException.class,
                () -> TRANSLATOR.translate("synthetic/entry-args.xpl", entry));
        assertEquals("nop.err.xlang.exec.translate-unsupported-node", err.getErrorCode());
    }

    @Test
    public void testFailFastUnsupportedLiteralType() {
        IExecutableExpression tree = LiteralExecutable.build(LOC, new Object());
        NopEvalException err = assertThrows(NopEvalException.class,
                () -> TRANSLATOR.translate("synthetic/literal-obj.xpl", tree));
        assertEquals("nop.err.xlang.exec.translate-unsupported-node", err.getErrorCode());
        assertTrue(err.getParam("className").toString().contains("LiteralExecutable"), err.toString());
    }

    @Test
    public void testFailFastSlotOutsideProgramEntry() {
        IExecutableExpression tree = new io.nop.xlang.exec.SlotIdentifierExecutable(LOC, "x", 0);
        NopEvalException err = assertThrows(NopEvalException.class,
                () -> TRANSLATOR.translate("synthetic/bare-slot.xpl", tree));
        assertEquals("nop.err.xlang.exec.translate-unsupported-node", err.getErrorCode());
    }

    // ------------------------------------------------------------------

    private static String translateCorpusUnit(String name) {
        CompareUnit unit = CorpusV1.units().stream()
                .filter(u -> u.getName().equals(name + "-static"))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("unit not found: " + name));
        IExecutableExpression tree = CorpusV1.Compiler.INSTANCE.compile(unit);
        return TRANSLATOR.translate(unit.getSourceLocationPath(), tree).getCode();
    }
}
