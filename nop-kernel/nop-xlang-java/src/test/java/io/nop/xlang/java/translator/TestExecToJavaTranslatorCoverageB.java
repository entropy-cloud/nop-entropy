package io.nop.xlang.java.translator;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.EvalScopeImpl;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.javac.jdk.JavaCompileResult;
import io.nop.javac.jdk.JdkJavaCompiler;
import io.nop.xlang.api.ExprEvalAction;
import io.nop.xlang.api.XLang;
import io.nop.xlang.ast.XLangOutputMode;
import io.nop.xlang.compare.CompareValues;
import io.nop.xlang.compare.RecordedOutputCall;
import io.nop.xlang.compare.RecordingEvalOutput;
import io.nop.xlang.exec.BreakExecutable;
import io.nop.xlang.exec.ContinueExecutable;
import io.nop.xlang.exec.LiteralExecutable;
import io.nop.xlang.exec.PlusExecutable;
import io.nop.xlang.exec.SeqExecutable;
import io.nop.xlang.exec.TryExecutable;
import io.nop.xlang.java.gen.GeneratedEvalBinding;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 覆盖 B 转译级单测（I4 Phase 2）：函数/闭包、控制流、输出/节点生成三族的行为级验证
 * （树→转译→测试域编译→执行→与解释器同一棵树执行结果比对），重点覆盖两个 plan 委托不变式：
 *
 * <ul>
 * <li><b>可变 slot 闭包捕获 cell 契约</b>——被闭包捕获的可变 slot（前端转 useRef，slot 值 =
 *     EvalReference 对象）经 captured 数组按引用传递 = 共享 cell，闭包内写、闭包外读与解释器一致；</li>
 * <li><b>ExitMode 传播边界不变式</b>——函数/闭包体内非局部跳转（return/break/continue）不跨
 *     函数边界外泄（生成代码以私有方法边界原生对应；含非根 CallFunc 局部函数调用形态）。</li>
 * </ul>
 *
 * <p>每族 ≥1 真实转译断言（120 类逐类合成树真实转译归 {@code TestExecTranslationCoverageMatrix}
 * 矩阵流；本类承载族级行为语义与源码形态焦点断言）。
 */
public class TestExecToJavaTranslatorCoverageB {

    private static final ExecToJavaTranslator TRANSLATOR = new ExecToJavaTranslator();
    private static final SourceLocation LOC = SourceLocation.fromLine("synthetic-b.xpl", 2);

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    // ------------------------------------------------------------------
    // 工具：标准前端取树（c:script 语句单元 / 模板单元）+ 双执行通路
    // ------------------------------------------------------------------

    private static IExecutableExpression compileScript(String source) {
        SourceLocation loc = SourceLocation.fromPath("synthetic-b/script.xpl");
        // c:script 内联于 xpl 文档：源文本按 XML 转义（corpus 静态资源同口径）
        String escaped = source.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        ExprEvalAction action = XLang.newCompileTool().allowUnregisteredScopeVar(true)
                .compileXpl(loc, "<c:script>" + escaped + "</c:script>");
        return action.getExpr();
    }

    private static IExecutableExpression compileTag(String source, XLangOutputMode mode) {
        SourceLocation loc = SourceLocation.fromPath("synthetic-b/tag.xpl");
        ExprEvalAction action = XLang.newCompileTool().allowUnregisteredScopeVar(true)
                .compileTag(io.nop.core.lang.xml.parse.XNodeParser.instance()
                        .parseFromText(loc, source), mode);
        return action.getExpr();
    }

    private static IEvalScope newScope() {
        return new EvalScopeImpl(new java.util.LinkedHashMap<>());
    }

    private static Object interpret(IExecutableExpression tree, IEvalScope scope) {
        return tree.execute(io.nop.xlang.api.XLang.getExecutor(), new EvalRuntime(scope));
    }

    private static Object interpret(IExecutableExpression tree, IEvalScope scope, IEvalOutput out) {
        return tree.execute(io.nop.xlang.api.XLang.getExecutor(), new EvalRuntime(scope, out));
    }

    /** 生成代码执行（入口方法按参数个数区分：$out 单元经第二隐参注入录制缓冲）。 */
    private static Object executeGenerated(IExecutableExpression tree, IEvalScope scope, IEvalOutput out) {
        GeneratedJavaSource src = TRANSLATOR.translate("synthetic-b/exec-" + COUNTER.incrementAndGet() + ".xpl", tree);
        JdkJavaCompiler compiler = new JdkJavaCompiler();
        List<String> classPaths = JdkJavaCompiler.getDefaultClassPaths();
        JavaCompileResult result = compiler.compile(src.getClassName(), src.getCode(), classPaths);
        assertTrue(result.isSuccess(), () -> "generated source compile failed: " + result.getErrorMessage()
                + "\n==== generated code ====\n" + src.getCode());
        Class<?> clazz = result.getGeneratedClass(src.getClassName());
        try {
            Method entry = GeneratedEvalBinding.findEntryMethod(clazz);
            if (entry.getParameterCount() == 2)
                return entry.invoke(null, scope, out);
            return entry.invoke(null, scope);
        } catch (Exception e) {
            Throwable cause = e instanceof java.lang.reflect.InvocationTargetException ? e.getCause() : e;
            if (cause instanceof RuntimeException)
                throw (RuntimeException) cause;
            throw new IllegalStateException("generated code execution failed", cause);
        }
    }

    private static final AtomicInteger COUNTER = new AtomicInteger();

    /** 双通路行为断言：同一棵树，解释器执行 vs 生成代码执行，返回值类型敏感相等。 */
    private static void assertBothSame(IExecutableExpression tree, Object expected) {
        IEvalScope scopeForGen = newScope();
        Object generated = executeGenerated(tree, scopeForGen, null);
        Object interpreted = interpret(tree, newScope());
        assertTrue(CompareValues.typedEquals(expected, interpreted),
                "interpreter must produce expected value first: expected=" + expected + ", was=" + interpreted);
        assertTrue(CompareValues.typedEquals(expected, generated),
                "generated code diverges from expected: expected=" + expected + ", was=" + generated);
    }

    // ------------------------------------------------------------------
    // 函数/闭包族：cell 契约 + ExitMode 边界（含局部函数调用形态）
    // ------------------------------------------------------------------

    /**
     * <b>cell 行为级测试</b>（plan Phase 2 委托）：被闭包捕获的可变 slot 写读经共享 cell——
     * 闭包内对外层可变变量赋值，闭包外读取必须可见（EvalReference 对象经 captured 数组按引用传递）。
     */
    @Test
    public void testClosureMutableSlotCellContract() {
        // 闭包内写（x 被捕获且被写 → 前端转 useRef：slot 值 = EvalReference 对象 = 共享 cell），
        // 闭包外读取必须看到累计写（cell 按引用传递）
        IExecutableExpression tree = compileScript(
                "let x = 1; let inc = () => { x = x + 1 }; inc(); inc(); x");
        assertBothSame(tree, 3);

        // 同一闭包内写读（cell 读写往返）
        IExecutableExpression tree2 = compileScript(
                "let x = 1; let f = u => { x = x + 1; return x }; f(0); f(0); x");
        assertBothSame(tree2, 3);

        // 局部函数读取外层变量、外层捕获后写（帧共享路径）：写后读可见
        IExecutableExpression tree3 = compileScript(
                "let x = 1; function g(){ return x }; x = 5; g()");
        assertBothSame(tree3, 5);
    }

    /**
     * <b>ExitMode 边界行为级测试</b>（plan Phase 2 委托）：函数/闭包体内非局部跳转不外泄——
     * return 在函数内循环中触发，仅退出函数（返回值 = 跳转承载值），不截断外层程序。
     */
    @Test
    public void testExitModeBoundaryReturnInsideLoopInFunction() {
        // 局部函数声明 + 调用（非根 CallFuncExecutable 形态）：return i 在函数内 for 循环中
        IExecutableExpression tree = compileScript(
                "function f(){ for(let i=0;i<3;i++){ if(i==1) return i } return -1 } f()");
        assertBothSame(tree, 1);

        // 函数在循环中被调用：每次调用的 return 不影响外层循环停走
        IExecutableExpression tree2 = compileScript(
                "let s = 0; function f(i){ if(i%2==0) return i*10; return 1 }"
                        + " for(let i=0;i<4;i++){ s = s + f(i) } s");
        assertBothSame(tree2, 22);

        // 箭头函数（闭包形态）内 return：不外泄
        IExecutableExpression tree3 = compileScript(
                "let f = (a) => { for(let i=0;i<3;i++){ if(i==a) return i*100 } return -1 }; f(2)");
        assertBothSame(tree3, 200);
    }

    /** break/continue 在函数内循环中 = 循环边界内跳转，不外泄函数/程序边界。 */
    @Test
    public void testExitModeBoundaryBreakContinueInFunction() {
        IExecutableExpression tree = compileScript(
                "function f(){ let r=0; for(let i=0;i<3;i++){ if(i==1) break; r=r+i } return r } f()");
        assertBothSame(tree, 0);

        IExecutableExpression tree2 = compileScript(
                "function f(){ let r=0; for(let i=0;i<5;i++){ if(i==2) continue; r=r+i } return r } f()");
        assertBothSame(tree2, 8);
    }

    /** 换缓冲生成体内 return（ExitMode cell 通道）：c:collect 体内 return，收集值经调用点分派承载。 */
    @Test
    public void testExitModeReturnInsideCollectBody() {
        IExecutableExpression tree = compileTag(
                "<c:collect outputMode='text'>a<c:if test='${true}'>${1+1}</c:if>c</c:collect>",
                XLangOutputMode.none);
        Object interpreted = interpret(tree, newScope(), new RecordingEvalOutput());
        Object generated = executeGenerated(tree, newScope(), new RecordingEvalOutput());
        assertEquals(interpreted, generated);
    }

    /** 模板单元顶层 c:return（编译为 ReturnExecutable）：值经入口 return 承载。 */
    @Test
    public void testExitModeReturnInTemplateLoop() {
        IExecutableExpression tree = compileTag(
                "<c:for var='v' items='${[3,7,9]}'><c:if test='${v&gt;5}'><c:return value='${v}'/></c:if></c:for>",
                XLangOutputMode.none);
        assertBothSame(tree, 7);
    }

    /** 局部函数调用形态源码级断言：非根 CallFunc 下降为 $fn_k 私有方法直调。 */
    @Test
    public void testLocalFunctionCallFormSourceShape() {
        String code = TRANSLATOR.translate("synthetic-b/local-fn.xpl",
                compileScript("function f(a){return a*2} f(1)+f(2)")).getCode();
        assertTrue(code.contains("private static Object $fn_1("), code);
        assertTrue(code.contains("$fn_1($scope"), code);
    }

    // ------------------------------------------------------------------
    // 控制流族：行为级（循环/分支/switch + 异常）
    // ------------------------------------------------------------------

    @Test
    public void testControlFlowForBreakContinue() {
        IExecutableExpression tree = compileScript(
                "let s=0; for(let i=0;i<5;i++){ if(i==2) continue; if(i==4) break; s=s+i }; s");
        assertBothSame(tree, 4);
    }

    /**
     * XLang script 的 switch 语义：case 体为块语句、无 fallthrough（前端不变式——break 仅限循环内，
     * 语义 = 每 case 块独立终止）。fallthrough 变体经矩阵合成树覆盖。
     */
    @Test
    public void testControlFlowSwitch() {
        IExecutableExpression tree = compileScript(
                "let x=2; let r=0; switch(x){case 1: {r=10} case 2: {r=20} default: {r=30}}; r");
        assertBothSame(tree, 20);

        IExecutableExpression hitFirst = compileScript(
                "let x=1; let r=0; switch(x){case 1: {r=10} case 2: {r=20} default: {r=30}}; r");
        assertBothSame(hitFirst, 10);

        IExecutableExpression dflt = compileScript(
                "let x=9; let r=0; switch(x){case 1: {r=10} case 2: {r=20} default: {r=30}}; r");
        assertBothSame(dflt, 30);
    }

    @Test
    public void testControlFlowForInOfWhileDoWhile() {
        assertBothSame(compileScript("let s=''; for(let k in {a:1,b:2}){ s=s+k }; s"), "ab");
        assertBothSame(compileScript("let s=0; for(let v of [1,2,3]){ s=s+v }; s"), 6);
        assertBothSame(compileScript("let i=0; let s=0; while(i<4){ s=s+i; i++ }; s"), 6);
        assertBothSame(compileScript("let i=5; let s=0; do { s=s+i; i++ } while(i<4); s"), 5);
    }

    /** 异常语义（B 族错误传播）：'x'.charAt(9) 异常经生成代码抛出，错误码与解释器一致。 */
    @Test
    public void testControlFlowExceptionPropagation() {
        IExecutableExpression tree = compileScript("'x'.charAt(9)");
        io.nop.api.core.exceptions.NopEvalException interpreted = assertThrows(
                io.nop.api.core.exceptions.NopEvalException.class, () -> interpret(tree, newScope()));
        io.nop.api.core.exceptions.NopEvalException generated = assertThrows(
                io.nop.api.core.exceptions.NopEvalException.class,
                () -> executeGenerated(tree, newScope(), null));
        assertEquals(interpreted.getErrorCode(), generated.getErrorCode());
        assertEquals(interpreted.getErrorLocation(), generated.getErrorLocation());
    }

    /** TryExecutable 无前端产生路径（Phase 1 盘点）→ 合成树源码级断言：catch 承载 + 总是重抛。 */
    @Test
    public void testTryExecutableSyntheticShape() {
        IExecutableExpression boom = LiteralExecutable.build(LOC, 1);
        // catch 体执行后 adapt 重抛（live 语义忠实保留：try 直译总是重抛）；exceptionSlot 需入口帧
        IExecutableExpression tree = new TryExecutable(LOC, boom, 1,
                LiteralExecutable.build(LOC, 2), null);
        GeneratedJavaSource src = TRANSLATOR.translate("synthetic-b/try.xpl", wrapEntry(tree, "b", "e"));
        assertTrue(src.getCode().contains("catch (java.lang.Exception"), src.getCode());
        assertTrue(src.getCode().contains("NopException.adapt"), src.getCode());
        assertTrue(src.getCode().contains("$v1 = "), src.getCode());
    }

    // ------------------------------------------------------------------
    // 输出/节点生成族：$out 通路行为级（输出调用序列与解释器逐事件一致）
    // ------------------------------------------------------------------

    @Test
    public void testOutputTextValueViaOutParam() {
        // 文本模板（text 模式）：静态文本段 + 表达式值段交错（OutputText/OutputValue 调用序列）
        IExecutableExpression tree = compileTag(
                "<c:for var='v' items='${[1,2]}'>a${v}</c:for>", XLangOutputMode.text);
        RecordingEvalOutput interpretedOut = new RecordingEvalOutput();
        Object interpreted = interpret(tree, newScope(), interpretedOut);
        RecordingEvalOutput generatedOut = new RecordingEvalOutput();
        Object generated = executeGenerated(tree, newScope(), generatedOut);
        assertEquals(interpreted, generated);
        assertEquals(interpretedOut.getCalls(), generatedOut.getCalls());
        RecordedOutputCall text = generatedOut.getCalls().stream()
                .filter(c -> c.getOp() == RecordedOutputCall.Op.TEXT).findFirst().orElseThrow();
        assertEquals("a", text.getValue());
        RecordedOutputCall value = generatedOut.getCalls().stream()
                .filter(c -> c.getOp() == RecordedOutputCall.Op.VALUE).findFirst().orElseThrow();
        assertEquals(1, value.getValue());
    }

    @Test
    public void testOutputXmlAttrViaOutParam() {
        // xml 模式：属性经 outputXmlAttr 以 text 事件流发射（live 语义），值段经 value 事件
        IExecutableExpression tree = compileTag("<div a='1' b='${2+3}'>x</div>", XLangOutputMode.xml);
        RecordingEvalOutput interpretedOut = new RecordingEvalOutput();
        interpret(tree, newScope(), interpretedOut);
        RecordingEvalOutput generatedOut = new RecordingEvalOutput();
        executeGenerated(tree, newScope(), generatedOut);
        assertEquals(interpretedOut.getCalls(), generatedOut.getCalls());
        StringBuilder text = new StringBuilder();
        for (RecordedOutputCall call : generatedOut.getCalls()) {
            if (call.getOp() == RecordedOutputCall.Op.TEXT)
                text.append(call.getValue());
        }
        assertTrue(text.toString().contains("b=\"5\""), "xml attr output must contain escaped attr: " + text);
    }

    @Test
    public void testCollectTextBehavior() {
        IExecutableExpression tree = compileTag(
                "<c:collect outputMode='text'>a${1+2}c</c:collect>", XLangOutputMode.none);
        Object interpreted = interpret(tree, newScope(), new RecordingEvalOutput());
        Object generated = executeGenerated(tree, newScope(), new RecordingEvalOutput());
        assertEquals(interpreted, generated);
        assertEquals("a3c", generated);
    }

    @Test
    public void testGenNodeCollectNodeBehavior() {
        IExecutableExpression tree = compileTag(
                "<c:collect outputMode='node'><a x='1'>t</a></c:collect>", XLangOutputMode.none);
        Object interpreted = interpret(tree, newScope(), new RecordingEvalOutput());
        Object generated = executeGenerated(tree, newScope(), new RecordingEvalOutput());
        assertEquals(interpreted.toString(), generated.toString());
    }

    @Test
    public void testGenNodeViaOutParam() {
        IExecutableExpression tree = compileTag("<a x='1'><b>t</b></a>", XLangOutputMode.node);
        RecordingEvalOutput interpretedOut = new RecordingEvalOutput();
        interpret(tree, newScope(), interpretedOut);
        RecordingEvalOutput generatedOut = new RecordingEvalOutput();
        executeGenerated(tree, newScope(), generatedOut);
        assertEquals(interpretedOut.getCalls(), generatedOut.getCalls());
    }

    // ------------------------------------------------------------------
    // fail-fast 反证（新边界）
    // ------------------------------------------------------------------

    /** 表达式位置的控制跳转节点 = 前端不可能形态，显式 fail-fast（矩阵同口径反证的行为级复核）。 */
    @Test
    public void testFailFastJumpInExpressionPosition() {
        PlusExecutable breakInExpr = new PlusExecutable(LOC,
                LiteralExecutable.build(LOC, 1), new BreakExecutable(LOC));
        io.nop.api.core.exceptions.NopEvalException err = assertThrows(
                io.nop.api.core.exceptions.NopEvalException.class,
                () -> TRANSLATOR.translate("synthetic-b/break-expr.xpl", breakInExpr));
        assertEquals("nop.err.xlang.exec.translate-unsupported-node", err.getErrorCode());

        PlusExecutable continueInExpr = new PlusExecutable(LOC,
                LiteralExecutable.build(LOC, 1), new ContinueExecutable(LOC));
        assertThrows(io.nop.api.core.exceptions.NopEvalException.class,
                () -> TRANSLATOR.translate("synthetic-b/continue-expr.xpl", continueInExpr));
    }

    /** 无循环语境的 break = 前端拒绝形态，转译显式 fail-fast（不静默通过）。 */
    @Test
    public void testFailFastBreakOutsideLoop() {
        IExecutableExpression tree = SeqExecutable.valueOf(LOC, new IExecutableExpression[]{
                new BreakExecutable(LOC), LiteralExecutable.build(LOC, 1)});
        io.nop.api.core.exceptions.NopEvalException err = assertThrows(
                io.nop.api.core.exceptions.NopEvalException.class,
                () -> TRANSLATOR.translate("synthetic-b/break-noop.xpl", wrapEntry(tree)));
        assertTrue(String.valueOf(err.getParam("detail")).contains("outside loop"), err.toString());
    }

    private static io.nop.xlang.exec.CallFuncExecutable wrapEntry(IExecutableExpression body, String... slotNames) {
        return new io.nop.xlang.exec.CallFuncExecutable(LOC, "__fn_1", slotNames,
                IExecutableExpression.EMPTY_EXPRS, body);
    }
}
