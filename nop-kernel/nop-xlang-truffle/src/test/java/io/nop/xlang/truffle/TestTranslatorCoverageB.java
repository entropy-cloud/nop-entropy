package io.nop.xlang.truffle;

import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.EvalScopeImpl;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.reflect.ReflectionManager;
import io.nop.xlang.api.XLang;
import io.nop.xlang.compare.RecordingEvalOutput;
import io.nop.xlang.exec.CallFuncWithClosureExecutable;
import io.nop.xlang.exec.ExecutableFunction;
import io.nop.xlang.exec.ForExecutable;
import io.nop.xlang.exec.IfExecutable;
import io.nop.xlang.exec.LiteralExecutable;
import io.nop.xlang.exec.BuildFuncRefExecutable;
import io.nop.xlang.exec.SelfIncExecutable;
import io.nop.xlang.exec.SeqExecutable;
import io.nop.xlang.exec.SlotAssignExecutable;
import io.nop.xlang.exec.SlotIdentifierExecutable;
import io.nop.xlang.exec.StaticFunctionExecutable;
import io.nop.xlang.exec.TryExecutable;
import io.nop.xlang.exec.VarExecutableFunction;
import io.nop.xlang.exec.XLangSemantics;
import io.nop.xlang.truffle.eval.XLangTruffleEval;
import io.nop.xlang.truffle.nodes.XExprNode;
import io.nop.xlang.truffle.nodes.XFunctionDispatchNode;
import io.nop.xlang.truffle.nodes.XLangRootNode;
import io.nop.xlang.truffle.nodes.XStaticMethodNode;
import io.nop.xlang.truffle.nodes.XVarFunctionCallNode;
import io.nop.xlang.truffle.translate.ExecToTruffleTranslator;
import io.nop.xlang.truffle.translate.TreeFingerprints;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static io.nop.xlang.XLangErrors.ERR_EXEC_THROW_EXCEPTION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 覆盖 B 翻译级行为单测（plan I7 Phase 2）：每族 ≥1 真实翻译断言 + 两级内联缓存正确性
 * （命中/未命中一致 + 多态超限泛化 + 值身份不误命中 + 可观测缓存状态探针 + 既有节点回填
 * 探针）+ 闭包两拷贝时序载体语义 + 控制流边界/异常族行为 + 输出换缓冲（含异常路径不丢恢复）。
 *
 * <p>执行载体 = 合成树经 {@link XLangTruffleEval}（翻译 AST 经 CallTarget 执行）；期望值 =
 * 同树解释器执行结果或手算 live 语义值（两处均注明）。
 */
public class TestTranslatorCoverageB {

    private static final SourceLocation LOC = SourceLocation.fromLine("coverage-b.xpl", 1);

    private static XLangTruffleEval eval;

    @BeforeAll
    public static void init() {
        io.nop.core.initialize.CoreInitialization.initialize();
        eval = new XLangTruffleEval();
    }

    @AfterAll
    public static void destroy() {
        eval.close();
        io.nop.core.initialize.CoreInitialization.destroy();
    }

    // ------------------------------------------------------------------
    // 驱动工具
    // ------------------------------------------------------------------

    private static Object truffleValue(IExecutableExpression tree, String sourceKey) {
        return truffleValue(tree, sourceKey, new EvalScopeImpl());
    }

    private static Object truffleValue(IExecutableExpression tree, String sourceKey, IEvalScope scope) {
        RecordingEvalOutput out = new RecordingEvalOutput();
        XLangTruffleEval.TranslatedEval result = eval.eval(sourceKey, tree, scope, out);
        if (result.getThrown() != null) {
            if (result.getThrown() instanceof RuntimeException)
                throw (RuntimeException) result.getThrown();
            throw new IllegalStateException("unwrapped non-runtime thrown", result.getThrown());
        }
        return result.getReturnValue();
    }

    private static Throwable truffleThrown(IExecutableExpression tree, String sourceKey) {
        RecordingEvalOutput out = new RecordingEvalOutput();
        XLangTruffleEval.TranslatedEval result = eval.eval(sourceKey, tree, new EvalScopeImpl(), out);
        return result.getThrown();
    }

    private static Object interpreterValue(IExecutableExpression tree) {
        IExpressionExecutor executor = XLang.getExecutor();
        return tree.execute(executor, new EvalRuntime(new EvalScopeImpl()));
    }

    private static IExecutableExpression literal(Object v) {
        return LiteralExecutable.build(LOC, v);
    }

    private static IExecutableExpression slotRead(int slot) {
        return new SlotIdentifierExecutable(LOC, "v" + slot, slot);
    }

    private static IExecutableExpression slotAssign(int slot, Object value) {
        return new SlotAssignExecutable(LOC, "v" + slot, slot, literal(value));
    }

    private static IExecutableExpression slotAssign(int slot, IExecutableExpression expr) {
        return new SlotAssignExecutable(LOC, "v" + slot, slot, expr);
    }

    private static IExecutableExpression seq(IExecutableExpression... exprs) {
        return SeqExecutable.valueOf(LOC, exprs);
    }

    /** 函数值工厂（体 = 常量；BuildFuncRef 载体；targetSlot 0 ← sourceSlot 0 捕获）。 */
    private static ExecutableFunction fnReturning(Object value, String name) {
        return new ExecutableFunction(LOC, LOC, name, 0, 0, new String[]{"c"},
                new IExecutableExpression[0], literal(value));
    }

    /** 读捕获槽的函数（体 = targetSlot 0 读取）。 */
    private static ExecutableFunction fnReadingCapture(String name) {
        return new ExecutableFunction(LOC, LOC, name, 0, 0, new String[]{"c"},
                new IExecutableExpression[0], slotRead(0));
    }

    /** AST 反射遍历（@Child/普通节点字段）——探针定位用（测试域，不进产品代码）。 */
    static <T> List<T> findNodes(Object node, Class<T> type) {
        List<Object> collected = new ArrayList<>();
        collect(node, type, collected, 0);
        List<T> found = new ArrayList<>();
        for (Object item : collected)
            found.add(type.cast(item));
        return found;
    }

    private static void collect(Object node, Class<?> type, List<Object> found, int depth) {
        if (node == null || depth > 64)
            return;
        if (type.isInstance(node))
            found.add(node);
        if (!(node instanceof com.oracle.truffle.api.nodes.Node))
            return;
        for (Class<?> c = node.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()))
                    continue;
                Class<?> ft = field.getType();
                if (!com.oracle.truffle.api.nodes.Node.class.isAssignableFrom(ft)
                        && !ft.isArray())
                    continue;
                field.setAccessible(true);
                try {
                    Object value = field.get(node);
                    if (value instanceof Object[]) {
                        for (Object item : (Object[]) value)
                            collect(item, type, found, depth + 1);
                    } else {
                        collect(value, type, found, depth + 1);
                    }
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // 控制流族（每族 ≥1 + 边界行为）
    // ------------------------------------------------------------------

    @Test
    public void testIfTranslateExecute() {
        // 族代表：If（真值分支取值语义）
        IfExecutable ifExpr = new IfExecutable(LOC, literal(true), literal(1), literal(2));
        assertEquals(1, truffleValue(ifExpr, "b-if.xpl"));
    }

    @Test
    public void testForBreakContinueAndNestedLoopConsumption() {
        // 嵌套循环 break/continue 消费层级：内层 continue 消费于内层，内层 break 消费于内层，
        // 外层 break 消费于外层——与解释器同树同值
        String src = "let s=0; for(let i=0;i<3;i++){ for(let j=0;j<3;j++){ if(j==1) continue; if(i==2) break; s=s+10*i+j } if(i==1) break; }; s";
        IExecutableExpression tree = XLang.newCompileTool().allowUnregisteredScopeVar(true)
                .compileFullExpr(LOC, src).getExpr();
        Object expected = interpreterValue(tree);
        Object actual = truffleValue(tree, "b-loop.xpl");
        assertEquals(expected, actual);
    }

    @Test
    public void testFunctionBoundaryClearsControlFlow() {
        // ExitMode 边界清零：函数体内 return 取值（取 f(1)=5）；break/continue 越界吞没（合成树：
        // 函数体 = Break/Continue，调用结果 null——解释器边界 finally 清零语义对应）
        String src = "function f(a){ if(a==1) return 5; return 9 }; f(1) + f(2)";
        IExecutableExpression tree = XLang.newCompileTool().allowUnregisteredScopeVar(true)
                .compileFullExpr(LOC, src).getExpr();
        Object expected = interpreterValue(tree);
        assertEquals(expected, truffleValue(tree, "b-boundary.xpl"));

        // 合成树：非根 CallFunc 体抛 XLBreak/XLContinue → 调用吞没返回 null
        IExecutableExpression breakCall = new io.nop.xlang.exec.CallFuncExecutable(LOC, "__fn_m",
                new String[0], IExecutableExpression.EMPTY_EXPRS,
                new io.nop.xlang.exec.BreakExecutable(LOC));
        assertEquals(null, truffleValue(breakCall, "b-boundary-break.xpl"));
        IExecutableExpression continueCall = new io.nop.xlang.exec.CallFuncExecutable(LOC, "__fn_m",
                new String[0], IExecutableExpression.EMPTY_EXPRS,
                new io.nop.xlang.exec.ContinueExecutable(LOC));
        assertEquals(null, truffleValue(continueCall, "b-boundary-continue.xpl"));
    }

    @Test
    public void testTryPassesControlFlowAndRunsFinally() {
        // Try 交互：控制流异常穿过 try 体不触发 catch（显式放行）；finally 在 unwind 中执行
        IExecutableExpression body = new IfExecutable(LOC,
                new io.nop.xlang.exec.EqExecutable(LOC, slotRead(0), literal(1)),
                new io.nop.xlang.exec.BreakExecutable(LOC), null);
        IExecutableExpression loop = ForExecutable.valueOf(LOC, null, literal(true),
                new SelfIncExecutable(LOC, "i", 0),
                new TryExecutable(LOC, body, -1, null, slotAssign(1, 99)));
        IExecutableExpression program = programFrame(new String[]{"i", "r"}, seq(
                slotAssign(0, 0), slotAssign(1, 0), loop, slotRead(1)));
        assertEquals(99, truffleValue(program, "b-try-cf.xpl"));
    }

    @Test
    public void testTryCatchTriggersOnRealExceptionOnly() {
        // catch 仅因真实 Exception 触发（非控制流）；必然 adapt 重抛语义保持
        IExecutableExpression tree = new TryExecutable(LOC,
                new io.nop.xlang.exec.ThrowExceptionExecutable(LOC, literal("boom")),
                -1, literal(2), null);
        Throwable thrown = truffleThrown(tree, "b-try-ex.xpl");
        NopEvalException e = assertInstanceOf(NopEvalException.class, thrown);
        assertEquals(ERR_EXEC_THROW_EXCEPTION.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testThrowFamilyTranslates() {
        Throwable thrown = truffleThrown(new io.nop.xlang.exec.ThrowExceptionExecutable(LOC,
                literal("boom")), "b-throw.xpl");
        assertEquals(ERR_EXEC_THROW_EXCEPTION.getErrorCode(),
                assertInstanceOf(NopEvalException.class, thrown).getErrorCode());
    }

    // ------------------------------------------------------------------
    // 函数/闭包族：两拷贝时序载体语义（Phase 1 §4 裁定的行为级验证）
    // ------------------------------------------------------------------

    /** 程序入口帧包装（slot 依赖节点翻译的前提）。 */
    private static IExecutableExpression programFrame(String[] slotNames, IExecutableExpression body) {
        return new io.nop.xlang.exec.CallFuncExecutable(LOC, "__fn_b", slotNames,
                IExecutableExpression.EMPTY_EXPRS, body);
    }

    @Test
    public void testBuildFuncRefCapturesEagerly() {
        // 捕获时拷贝载体（BuildFuncRef）：捕获后外部写不可见——g 在 x=0 时捕获，x 随后写 1/2，
        // 两次调用均读捕获快照（0, 1——第二次调用读到第二次捕获 = 1）；同 CallTarget 双值不误命中
        IExecutableExpression program = programFrame(new String[]{"x", "g", "r", "i"}, seq(
                slotAssign(0, 0),
                slotAssign(2, 0),
                ForExecutable.valueOf(LOC, slotAssign(3, 0),
                        new io.nop.xlang.exec.LtExecutable(LOC, slotRead(3), literal(2)),
                        new SelfIncExecutable(LOC, "i", 3),
                        seq(
                                slotAssign(1, BuildFuncRefExecutable.build(LOC,
                                        fnReadingCapture("f"), new int[]{0}, new int[]{0})),
                                slotAssign(0, new io.nop.xlang.exec.PlusExecutable(LOC,
                                        slotRead(0), literal(1))),
                                slotAssign(2, new io.nop.xlang.exec.PlusExecutable(LOC, slotRead(2),
                                        new VarExecutableFunction(LOC, slotRead(1), false,
                                                new IExecutableExpression[0]))))),
                slotRead(2)));
        // 迭代 1：g 捕获 x=0；x→1；r += g()=0 → r=0。迭代 2：g 捕获 x=1；x→2；r += 1 → r=1
        assertEquals(1, truffleValue(program, "b-capture-time.xpl"));

        // 可观测缓存状态：单一调用点两次命中（directCalls=2，无泛化）——同 CallTarget 不同
        // captured 的函数值共用缓存条目且各自结果正确（值身份不误命中）
        XLangRootNode root = eval.eval("b-capture-time-probe.xpl", program,
                new EvalScopeImpl(), new RecordingEvalOutput()).getUnit().getRootNode();
        List<XVarFunctionCallNode> callNodes = findNodes(root, XVarFunctionCallNode.class);
        assertEquals(1, callNodes.size());
        XFunctionDispatchNode dispatch = callNodes.get(0).getDispatch();
        assertEquals(2, dispatch.getDirectCalls(), "same-target repeat calls must hit the direct path");
        assertEquals(0, dispatch.getGenericCalls(), "no polymorphic overflow at this site");
    }

    @Test
    public void testCallFuncWithClosureCopiesAtCallTime() {
        // 调用时拷贝载体（CallFuncWithClosure）：声明后外部写在调用时可见——x=1 声明，x 改 5，
        // 调用拷当前帧值 → 5
        IExecutableExpression call = new CallFuncWithClosureExecutable(LOC, "__fn_m",
                new String[]{"c"}, IExecutableExpression.EMPTY_EXPRS, slotRead(0),
                new int[]{0}, new int[]{0});
        IExecutableExpression program = programFrame(new String[]{"x"}, seq(
                slotAssign(0, 1),
                slotAssign(0, 5),
                call));
        assertEquals(5, truffleValue(program, "b-call-time.xpl"));
    }

    @Test
    public void testPolymorphicSiteOverflowsToGeneric() {
        // 多态调用点：同一位置先后 4 个不同函数（4 个 CallTarget）——limit=3 内直达命中，
        // 第 4 个超限进泛化路径；结果正确 + 探针可观测（directCalls=3 / genericCalls>=1）
        ExecutableFunction fnA = fnReturning(1, "fa");
        ExecutableFunction fnB = fnReturning(2, "fb");
        ExecutableFunction fnC = fnReturning(3, "fc");
        ExecutableFunction fnD = fnReturning(4, "fd");
        IExecutableExpression call = new VarExecutableFunction(LOC, slotRead(1), false,
                new IExecutableExpression[0]);
        IExecutableExpression assign = new IfExecutable(LOC,
                new io.nop.xlang.exec.EqExecutable(LOC, slotRead(3), literal(0)),
                slotAssign(1, BuildFuncRefExecutable.build(LOC, fnA, new int[0], new int[0])),
                new IfExecutable(LOC,
                        new io.nop.xlang.exec.EqExecutable(LOC, slotRead(3), literal(1)),
                        slotAssign(1, BuildFuncRefExecutable.build(LOC, fnB, new int[0], new int[0])),
                        new IfExecutable(LOC,
                                new io.nop.xlang.exec.EqExecutable(LOC, slotRead(3), literal(2)),
                                slotAssign(1, BuildFuncRefExecutable.build(LOC, fnC, new int[0], new int[0])),
                                slotAssign(1, BuildFuncRefExecutable.build(LOC, fnD, new int[0], new int[0])))));
        IExecutableExpression program = programFrame(new String[]{"r", "f", "t", "i"}, seq(
                slotAssign(0, 0),
                ForExecutable.valueOf(LOC, slotAssign(3, 0),
                        new io.nop.xlang.exec.LtExecutable(LOC, slotRead(3), literal(4)),
                        new SelfIncExecutable(LOC, "i", 3),
                        seq(assign,
                                slotAssign(0, new io.nop.xlang.exec.PlusExecutable(LOC, slotRead(0), call)))),
                slotRead(0)));
        assertEquals(10, truffleValue(program, "b-polymorphic.xpl"));

        XLangRootNode root = eval.eval("b-polymorphic-probe.xpl", program,
                new EvalScopeImpl(), new RecordingEvalOutput()).getUnit().getRootNode();
        XFunctionDispatchNode dispatch = findNodes(root, XVarFunctionCallNode.class).get(0).getDispatch();
        assertEquals(3, dispatch.getDirectCalls(), "first 3 distinct targets must hit direct cache entries");
        assertTrue(dispatch.getGenericCalls() >= 1,
                "4th distinct target must overflow to generic path (limit=3), actual=" + dispatch.getGenericCalls());
    }

    @Test
    public void testInterpreterEquivalenceForClosureUnits() {
        // 同树两执行源等价（解释器 vs truffle）：捕获时/调用时两载体 + 局部函数调用
        String[] sources = {
                "let x = 1; let inc = () => { x = x + 1 }; inc(); inc(); x",
                "((x) => x + 8)(1)",
                "let f = x => x + 1; f(2)",
                "function f(a){return a*2} f(1)+f(2)"
        };
        for (int i = 0; i < sources.length; i++) {
            IExecutableExpression tree = XLang.newCompileTool().allowUnregisteredScopeVar(true)
                    .compileFullExpr(LOC, sources[i]).getExpr();
            Object expected = interpreterValue(tree);
            assertEquals(expected, truffleValue(tree, "b-equiv-" + i + ".xpl"), "source: " + sources[i]);
        }
    }

    // ------------------------------------------------------------------
    // 既有节点回填探针（XStaticMethodNode L2 直达形态 = 编译期常量直持）
    // ------------------------------------------------------------------

    @Test
    public void testStaticMethodNodeBackfillHoldsCompileTimeConstant() {
        StaticFunctionExecutable staticCall = new StaticFunctionExecutable(LOC, "java.lang.Math",
                "max", false,
                ReflectionManager.instance().getClassModel(Math.class).getStaticMethodsByName("max"),
                new IExecutableExpression[]{literal(3), literal(4)});
        // 行为：直持常量 + invokeStaticMethod（非逐调用解析变体）
        assertEquals(4, truffleValue(staticCall, "b-static.xpl"));
        // 接线证据（缓存初始化探针）：节点持有的 methodCollection 与源树实例同一
        XLangRootNode root = eval.eval("b-static-probe.xpl", staticCall,
                new EvalScopeImpl(), new RecordingEvalOutput()).getUnit().getRootNode();
        XStaticMethodNode node = findNodes(root, XStaticMethodNode.class).get(0);
        assertSame(staticCall.getMethodCollection(), node.getMethodCollection(),
                "backfilled node must hold the compile-time constant collection (same instance as source tree)");
    }

    // ------------------------------------------------------------------
    // 输出/节点生成族：换缓冲语义（含异常路径不丢恢复）
    // ------------------------------------------------------------------

    @Test
    public void testCollectTextSwapsBufferAndRestores() {
        // 换缓冲期间输出进收集器、恢复后回主缓冲（主缓冲零调用）
        IExecutableExpression tree = new io.nop.xlang.exec.CollectTextExecutable(LOC, seq(
                new io.nop.xlang.exec.OutputTextExecutable(LOC, "a"),
                new io.nop.xlang.exec.OutputValueExecutable(LOC, literal(1)),
                new io.nop.xlang.exec.OutputTextExecutable(LOC, "c")));
        RecordingEvalOutput out = new RecordingEvalOutput();
        XLangTruffleEval.TranslatedEval result = eval.eval("b-collect.xpl", tree,
                new EvalScopeImpl(), out);
        assertEquals("a1c", result.getReturnValue());
        assertEquals(0, out.getCalls().size(), "main output must stay untouched (swap/restore protocol)");
    }

    @Test
    public void testCollectExceptionPathRestoresBuffer() {
        // 异常路径不丢恢复：body 异常穿透时 restore 先行（主缓冲零调用），异常语义保持
        IExecutableExpression tree = new io.nop.xlang.exec.CollectTextExecutable(LOC, seq(
                new io.nop.xlang.exec.OutputTextExecutable(LOC, "a"),
                new io.nop.xlang.exec.ThrowExceptionExecutable(LOC, literal("boom"))));
        RecordingEvalOutput out = new RecordingEvalOutput();
        XLangTruffleEval.TranslatedEval result = eval.eval("b-collect-ex.xpl", tree,
                new EvalScopeImpl(), out);
        assertNotNull(result.getThrown());
        assertEquals(ERR_EXEC_THROW_EXCEPTION.getErrorCode(),
                assertInstanceOf(NopEvalException.class, result.getThrown()).getErrorCode());
        assertEquals(0, out.getCalls().size(), "output must be restored even on exception unwind");
    }

    @Test
    public void testCollectPendingExitDispatch() {
        // pending exit 分派：collect 体内 return → 收集值为终值（边界清零吞没合并对应）；
        // 循环内 break → 循环消费
        IExecutableExpression returnInCollect = new io.nop.xlang.exec.CollectTextExecutable(LOC, seq(
                new io.nop.xlang.exec.OutputTextExecutable(LOC, "x"),
                new io.nop.xlang.exec.ReturnExecutable(LOC, literal(1))));
        assertEquals("x", truffleValue(returnInCollect, "b-collect-ret.xpl"));

        IExecutableExpression loopBody = new TryExecutable(LOC,
                new io.nop.xlang.exec.CollectTextExecutable(LOC,
                        new io.nop.xlang.exec.BreakExecutable(LOC)),
                -1, null, null);
        // 循环内 break（经 collect cell + inLoop 重抛）→ 循环消费退出
        IExecutableExpression loopProgram = programFrame(new String[]{"i", "r"}, seq(
                slotAssign(1, 0),
                ForExecutable.valueOf(LOC, slotAssign(0, 0), literal(true),
                        new SelfIncExecutable(LOC, "i", 0),
                        seq(loopBody, slotAssign(1, new io.nop.xlang.exec.PlusExecutable(LOC,
                                slotRead(1), literal(1))))),
                slotRead(1)));
        assertEquals(0, truffleValue(loopProgram, "b-collect-break.xpl"),
                "break inside collect body must be consumed by the enclosing loop (r stays 0)");
    }

    @Test
    public void testGenNodeEmitsThroughHandler() {
        // GenNode：DisabledEvalOutput → 收集返回 XNode；正常缓冲 → handler 直发（cast quirk 通道）
        io.nop.xlang.exec.GenNodeExecutable genNode = new io.nop.xlang.exec.GenNodeExecutable(LOC,
                null, literal("div"), new io.nop.xlang.exec.GenNodeAttrExecutable[]{
                new io.nop.xlang.exec.GenNodeAttrExecutable("a", literal("1"))}, null, null);
        RecordingEvalOutput out = new RecordingEvalOutput();
        XLangTruffleEval.TranslatedEval result = eval.eval("b-gennode.xpl", genNode,
                new EvalScopeImpl(), out);
        // 正常缓冲（RecordingEvalOutput implements IXNodeHandler）：simpleNode 直发 + 返回 null
        assertEquals(null, result.getReturnValue());
        assertEquals(1, out.getCalls().size());
        assertEquals(io.nop.xlang.compare.RecordedOutputCall.Op.SIMPLE_NODE, out.getCalls().get(0).getOp());
    }

    // ------------------------------------------------------------------
    // 输出族直译行为（Output 族经 context 窗口缓冲）
    // ------------------------------------------------------------------

    @Test
    public void testOutputFamilyWritesToWindowBuffer() {
        IExecutableExpression tree = seq(
                new io.nop.xlang.exec.OutputTextExecutable(LOC, "t"),
                new io.nop.xlang.exec.OutputValueExecutable(LOC, literal(7)));
        RecordingEvalOutput out = new RecordingEvalOutput();
        XLangTruffleEval.TranslatedEval result = eval.eval("b-output.xpl", tree,
                new EvalScopeImpl(), out);
        assertEquals(null, result.getReturnValue());
        assertEquals(2, out.getCalls().size());
        assertEquals("t", out.getCalls().get(0).getValue());
        assertEquals(7, out.getCalls().get(1).getValue());
    }

    // ------------------------------------------------------------------
    // 静默跳过反证：不可解析载荷显式 fail-fast（LazyCompiled 由矩阵反证覆盖，此处补翻译级）
    // ------------------------------------------------------------------

    @Test
    public void testUnresolvableFunctionPayloadFailsFast() {
        IExecutableExpression tree = BuildFuncRefExecutable.build(LOC,
                new ExecutableFunction(LOC, LOC, "f", 0, 0, new String[0],
                        new IExecutableExpression[0], null),
                new int[0], new int[0]);
        try {
            new ExecToTruffleTranslator().translate("b-null-fn.xpl",
                    TreeFingerprints.fingerprint(tree), tree, null);
            fail("function payload without body must fail-fast");
        } catch (NopEvalException e) {
            assertTrue(String.valueOf(e.getParam("detail")).contains("function payload without body"));
        }
    }
}
