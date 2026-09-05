package io.nop.xlang.java.translator;

import io.nop.api.core.convert.SysConverterRegistry;
import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.core.lang.eval.global.EvalGlobalRegistry;
import io.nop.core.model.query.FilterOp;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.reflect.IPropertyGetter;
import io.nop.core.reflect.IPropertySetter;
import io.nop.core.reflect.ReflectionManager;
import io.nop.xlang.ast.XLangEscapeMode;
import io.nop.xlang.ast.XLangOperator;
import io.nop.xlang.compare.ExecNodeBaseline;
import io.nop.xlang.exec.AbstractExecutable;
import io.nop.xlang.exec.ArrayBindingAssignExecutable;
import io.nop.xlang.exec.AssertOpExecutable;
import io.nop.xlang.exec.AssignIdentifier;
import io.nop.xlang.exec.BetweenOpExecutable;
import io.nop.xlang.exec.BinaryExecutable;
import io.nop.xlang.exec.BindVarExecutable;
import io.nop.xlang.exec.BitNotExecutable;
import io.nop.xlang.exec.BlockExecutable;
import io.nop.xlang.exec.BreakExecutable;
import io.nop.xlang.exec.BuildClosureBodyExecutable;
import io.nop.xlang.exec.BuildFuncRefExecutable;
import io.nop.xlang.exec.CallFuncExecutable;
import io.nop.xlang.exec.CallFuncWithClosureExecutable;
import io.nop.xlang.exec.CastExecutable;
import io.nop.xlang.exec.CloneLiteralExecutable;
import io.nop.xlang.exec.CollectJsonExecutable;
import io.nop.xlang.exec.CollectNodeExecutable;
import io.nop.xlang.exec.CollectSqlExecutable;
import io.nop.xlang.exec.CollectTextExecutable;
import io.nop.xlang.exec.ConcatExecutable;
import io.nop.xlang.exec.ContinueExecutable;
import io.nop.xlang.exec.ConvertExecutable;
import io.nop.xlang.exec.ConvertWithDefaultExecutable;
import io.nop.xlang.exec.DebugExecutable;
import io.nop.xlang.exec.DebugIdentifierExecutable;
import io.nop.xlang.exec.DoWhileExecutable;
import io.nop.xlang.exec.EnhanceRefSlotExecutable;
import io.nop.xlang.exec.EqNullExecutable;
import io.nop.xlang.exec.EscapeOutputExecutable;
import io.nop.xlang.exec.ExecutableFunction;
import io.nop.xlang.exec.ExecutableFunctionEvalAction;
import io.nop.xlang.exec.ForExecutable;
import io.nop.xlang.exec.ForInExecutable;
import io.nop.xlang.exec.ForOfExecutable;
import io.nop.xlang.exec.FunctionalAdapterExecutable;
import io.nop.xlang.exec.GenNodeExecutable;
import io.nop.xlang.exec.GenXJsonExecutable;
import io.nop.xlang.exec.GetAttrExecutable;
import io.nop.xlang.exec.GetPropertyExecutable;
import io.nop.xlang.exec.GetterGetPropertyExecutable;
import io.nop.xlang.exec.GlobalVarExecutable;
import io.nop.xlang.exec.GuardNotEmptyExecutable;
import io.nop.xlang.exec.GuardNotNullExecutable;
import io.nop.xlang.exec.IfExecutable;
import io.nop.xlang.exec.InitRefSlotExecutable;
import io.nop.xlang.exec.InstanceOfExecutable;
import io.nop.xlang.exec.LazyCompiledExecutableFunction;
import io.nop.xlang.exec.LocationFunction;
import io.nop.xlang.exec.NeNullExecutable;
import io.nop.xlang.exec.NegExecutable;
import io.nop.xlang.exec.NewListExecutable;
import io.nop.xlang.exec.NewMapExecutable;
import io.nop.xlang.exec.NewObjectExecutable;
import io.nop.xlang.exec.NullCoalesceExecutable;
import io.nop.xlang.exec.NullExecutable;
import io.nop.xlang.exec.ObjectBindingAssignExecutable;
import io.nop.xlang.exec.OutputTextExecutable;
import io.nop.xlang.exec.OutputValueExecutable;
import io.nop.xlang.exec.OutputXmlAttrExecutable;
import io.nop.xlang.exec.OutputXmlExtAttrsExecutable;
import io.nop.xlang.exec.PropBinding;
import io.nop.xlang.exec.PropInExecutable;
import io.nop.xlang.exec.RangeExecutable;
import io.nop.xlang.exec.ReferenceAssignExecutable;
import io.nop.xlang.exec.ReferenceIdentifierExecutable;
import io.nop.xlang.exec.ReferenceSelfAssignExecutable;
import io.nop.xlang.exec.ReferenceSelfDecExecutable;
import io.nop.xlang.exec.ReferenceSelfIncExecutable;
import io.nop.xlang.exec.RenewReferenceExecutable;
import io.nop.xlang.exec.ResolvedObjFunctionExecutable;
import io.nop.xlang.exec.ReturnExecutable;
import io.nop.xlang.exec.ReturnNullExecutable;
import io.nop.xlang.exec.ReturnScopeValuesExecutable;
import io.nop.xlang.exec.ScopeAssignExecutable;
import io.nop.xlang.exec.ScopeIdentifierExecutable;
import io.nop.xlang.exec.ScopeSelfAssignExecutable;
import io.nop.xlang.exec.ScopeSelfDecExecutable;
import io.nop.xlang.exec.ScopeSelfIncExecutable;
import io.nop.xlang.exec.SelfAssignExecutable;
import io.nop.xlang.exec.SelfAssignAttrExecutable;
import io.nop.xlang.exec.SelfAssignPropertyExecutable;
import io.nop.xlang.exec.SelfDecExecutable;
import io.nop.xlang.exec.SelfIncExecutable;
import io.nop.xlang.exec.SeqExecutable;
import io.nop.xlang.exec.SetAttrExecutable;
import io.nop.xlang.exec.SetPropertyExecutable;
import io.nop.xlang.exec.SetterSetPropertyExecutable;
import io.nop.xlang.exec.SlotAssignExecutable;
import io.nop.xlang.exec.SlotIdentifierExecutable;
import io.nop.xlang.exec.StaticFunctionExecutable;
import io.nop.xlang.exec.StaticGetterGetPropertyExecutable;
import io.nop.xlang.exec.StrictEqNullExecutable;
import io.nop.xlang.exec.StrictNeNullExecutable;
import io.nop.xlang.exec.SwitchExecutable;
import io.nop.xlang.exec.ThrowErrorCodeExecutable;
import io.nop.xlang.exec.ThrowExceptionExecutable;
import io.nop.xlang.exec.TryExecutable;
import io.nop.xlang.exec.TypeOfExecutable;
import io.nop.xlang.exec.VarExecutableFunction;
import io.nop.xlang.exec.VarFunctionExecutable;
import io.nop.xlang.exec.VarStatusExecutable;
import io.nop.xlang.exec.WhileExecutable;
import io.nop.xlang.java.gen.EvalMethodConvention;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static io.nop.xlang.XLangErrors.ERR_EXEC_TRANSLATE_UNSUPPORTED_NODE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 覆盖矩阵断言测试（I3 Phase 3 落地，I4 Phase 2/3 锚点切换到 java 侧目标集后闭环）。
 *
 * <p>矩阵口径（I4 per-backend 定稿）：
 * <ul>
 * <li>基线 = {@link ExecNodeBaseline}（live 包扫描单一事实源，新鲜度红灯在 nop-xlang 侧
 *   {@code TestExecNodeBaselineFreshness}；本测试经 test-jar 同基线消费）；</li>
 * <li>注册证据 = 转译器支持集可编程枚举（{@link ExecToJavaTranslator#getSupportedNodeClasses()}）
 *   与基线 <b>java 侧目标集</b> {@code ExecNodeBaseline.javaTargetSet()}（I2 子集 + A 族 +
 *   并入残余 + B 族三族 = 120 类）双向一致；</li>
 * <li>真实转译验证（非清单自证）：目标集逐类构造最小实例经转译器真实转译成功
 *   （slot 依赖节点以程序入口帧包装，嵌套同族变体经其顶层类的工厂产生——文件级粒度裁定见
 *   {@link ExecNodeBaseline} javadoc）。证据形态裁定（I4 Phase 2）：
 *   {@code GenNodeAttrExecutable} 为属性描述符（非树节点），证据 = 支持集成员 + 宿主
 *   {@code GenNodeExecutable} 携带属性的最小实例真实转译（宿主节点载体）；
 *   {@code LazyCompiledExecutableFunction}（载荷不可解析时）/ {@code FunctionalAdapterExecutable}
 *   （运行期 IEvalFunction 载荷不可内嵌）证据 = 支持集成员 + 分派 fail-fast 反证；</li>
 * <li>pending 清零：java 侧 B 族 pending 集不存在（目标集 = 支持集，三边缘类裁定无第三态）；</li>
 * <li>红灯注入：未注册具体节点类（测试域合成 {@code FutureExecutable}）→ 矩阵判不支持 +
 *   转译 fail-fast + 基线判未归属（新鲜度红灯路径），已注册类绿对照。</li>
 * </ul>
 */
public class TestExecTranslationCoverageMatrix {

    private static final ExecToJavaTranslator TRANSLATOR = new ExecToJavaTranslator();

    private static final SourceLocation LOC = SourceLocation.fromLine("matrix-min.xpl", 1);

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    // ------------------------------------------------------------------
    // 注册证据：支持集 ↔ 基线 registeredTarget 双向一致
    // ------------------------------------------------------------------

    @org.junit.jupiter.api.Test
    public void testSupportSetMatchesJavaTargetSet() {
        Set<String> supported = new TreeSet<>();
        for (Class<?> cls : ExecToJavaTranslator.getSupportedNodeClasses())
            supported.add(cls.getSimpleName());
        Set<String> baseline = new TreeSet<>(ExecNodeBaseline.javaTargetSet());
        assertEquals(baseline, supported,
                "translator support set must equal baseline javaTargetSet (missing="
                        + diff(baseline, supported) + ", extra=" + diff(supported, baseline) + ")");
        assertEquals(123, baseline.size(), "javaTargetSet = registeredTarget(87) + bFamily(33) + delete(3)");
        // pending 清零可断言：java 目标集与支持集双向相等即 B 族全部落地（无 pending 残留、无第三态）
        assertTrue(javaTargetSetFullySupported(), "java-side pending set is empty after I4 closure");
    }

    private static boolean javaTargetSetFullySupported() {
        Set<String> supported = new TreeSet<>();
        for (Class<?> cls : ExecToJavaTranslator.getSupportedNodeClasses())
            supported.add(cls.getSimpleName());
        return supported.containsAll(ExecNodeBaseline.bFamily());
    }

    private static Set<String> diff(Set<String> a, Set<String> b) {
        TreeSet<String> ret = new TreeSet<>(a);
        ret.removeAll(b);
        return ret;
    }

    // ------------------------------------------------------------------
    // 真实转译验证：java 目标集逐类最小实例转译成功（非清单自证；
    // 无树形态/载荷不可内嵌的证据形态裁定类走专门断言，不出现在本流）
    // ------------------------------------------------------------------

    /** 证据形态裁定类（I4 Phase 2）：非树节点（宿主载体）与载荷不可内嵌（分派 fail-fast 反证）。 */
    private static final Set<String> SPECIAL_EVIDENCE_FORM = Set.of("GenNodeAttrExecutable",
            "LazyCompiledExecutableFunction", "FunctionalAdapterExecutable");

    static Stream<String> registeredTargetClasses() {
        return new TreeSet<>(ExecNodeBaseline.javaTargetSet()).stream()
                .filter(name -> !SPECIAL_EVIDENCE_FORM.contains(name));
    }

    @ParameterizedTest(name = "translate:{0}")
    @MethodSource("registeredTargetClasses")
    public void testRegisteredClassTranslates(String className) {
        IExecutableExpression tree = MinimalNodeFactory.minimalTree(className);
        GeneratedJavaSource src;
        try {
            src = TRANSLATOR.translate("matrix/" + className + ".xpl", tree);
        } catch (RuntimeException e) {
            throw new IllegalStateException("matrix translate failed for " + className, e);
        }
        assertNotNull(src);
        assertNotNull(src.getCode());
        // EvalMethod 约定：首参 $scope；输出族触达的单元按 I2 契约追加第二隐参 IEvalOutput $out
        assertTrue(src.getCode().contains("public static Object execute(IEvalScope $scope)")
                        || src.getCode().contains("public static Object execute(IEvalScope $scope, IEvalOutput $out)"),
                "generated source must follow EvalMethod convention: " + className + "\n" + src.getCode());
        assertTrue(src.getClassName().startsWith(EvalMethodConvention.GENERATED_PACKAGE + ".Gen_"));
    }

    // ------------------------------------------------------------------
    // 证据形态裁定（I4 Phase 2）：无树形态/载荷不可内嵌类的专门断言 + 排除类反证
    // ------------------------------------------------------------------

    @org.junit.jupiter.api.Test
    public void testSpecialEvidenceForms() throws Exception {
        // GenNodeAttrExecutable：属性描述符（非 IExecutableExpression）——证据 = 支持集成员 +
        // 宿主 GenNodeExecutable 携带属性的最小实例真实转译（宿主节点载体，translate 流内覆盖）
        Class<?> attrClass = Class.forName("io.nop.xlang.exec.GenNodeAttrExecutable");
        assertTrue(ExecToJavaTranslator.isNodeClassSupported(attrClass),
                "GenNodeAttrExecutable must be a support-set member (host-carrier evidence)");
        assertFalse(IExecutableExpression.class.isAssignableFrom(attrClass),
                "GenNodeAttrExecutable is a descriptor, not a tree node");

        // LazyCompiledExecutableFunction：支持集成员 + 空载荷分派 fail-fast 反证
        // （转译期 force-compile 裁定：载荷 null/不可解析 → 显式 fail-fast）
        Class<?> lazyClass = Class.forName("io.nop.xlang.exec.LazyCompiledExecutableFunction");
        assertTrue(ExecToJavaTranslator.isNodeClassSupported(lazyClass),
                "LazyCompiledExecutableFunction must be a support-set member");
        NopEvalException lazyErr = assertThrows(NopEvalException.class, () -> TRANSLATOR.translate(
                "matrix/b-LazyCompiledExecutableFunction.xpl",
                MinimalNodeFactory.minimalTree("LazyCompiledExecutableFunction")));
        assertEquals(ERR_EXEC_TRANSLATE_UNSUPPORTED_NODE.getErrorCode(), lazyErr.getErrorCode());
        assertTrue(String.valueOf(lazyErr.getParam("detail")).contains("lazy function payload"),
                lazyErr.toString());

        // FunctionalAdapterExecutable：支持集成员 + 运行期 IEvalFunction 载荷分派 fail-fast 反证
        Class<?> adapterClass = Class.forName("io.nop.xlang.exec.FunctionalAdapterExecutable");
        assertTrue(ExecToJavaTranslator.isNodeClassSupported(adapterClass),
                "FunctionalAdapterExecutable must be a support-set member");
        NopEvalException adapterErr = assertThrows(NopEvalException.class, () -> TRANSLATOR.translate(
                "matrix/b-FunctionalAdapterExecutable.xpl",
                MinimalNodeFactory.minimalTree("FunctionalAdapterExecutable")));
        assertEquals(ERR_EXEC_TRANSLATE_UNSUPPORTED_NODE.getErrorCode(), adapterErr.getErrorCode());
        assertTrue(String.valueOf(adapterErr.getParam("detail")).contains("IEvalFunction payload"),
                adapterErr.toString());
    }

    /**
     * 排除类反证（边界收缩后的新 fail-fast 边界，I4 边缘裁定改判排除的两类保持不可转译）：
     * 树节点类同时断言转译报 {@code ERR_EXEC_TRANSLATE_UNSUPPORTED_NODE}。
     */
    @ParameterizedTest(name = "excluded:{0}")
    @MethodSource("i4ExcludedTreeClasses")
    public void testI4ExcludedClassesFailFast(String className) throws Exception {
        Class<?> cls = Class.forName("io.nop.xlang.exec." + className);
        assertFalse(ExecToJavaTranslator.isNodeClassSupported(cls),
                "I4-excluded class must not be in translator support set: " + className);
        if (!IExecutableExpression.class.isAssignableFrom(cls))
            return;
        IExecutableExpression tree = MinimalNodeFactory.minimalTree(className);
        NopEvalException err = assertThrows(NopEvalException.class,
                () -> TRANSLATOR.translate("matrix/excluded-" + className + ".xpl", tree));
        assertEquals(ERR_EXEC_TRANSLATE_UNSUPPORTED_NODE.getErrorCode(), err.getErrorCode(),
                "I4-excluded node must fail-fast as unsupported: " + className);
    }

    static Stream<String> i4ExcludedTreeClasses() {
        return Stream.of("ReturnScopeValuesExecutable", "ExecutableFunctionEvalAction");
    }

    // ------------------------------------------------------------------
    // 红灯注入：未注册具体节点类 → 矩阵红灯（红/绿对照）
    // ------------------------------------------------------------------

    @org.junit.jupiter.api.Test
    public void testRedLightInjectionUnregisteredNodeClass() {
        IExecutableExpression future = new FutureExecutable(LOC);
        // 红：未注册具体节点类 → 支持集不含 + 转译 fail-fast + 基线判未归属（新鲜度红灯路径）
        assertFalse(ExecToJavaTranslator.isNodeClassSupported(FutureExecutable.class),
                "unregistered concrete node class must not resolve as supported (matrix red)");
        NopEvalException err = assertThrows(NopEvalException.class,
                () -> TRANSLATOR.translate("matrix/red-light.xpl", future));
        assertEquals(ERR_EXEC_TRANSLATE_UNSUPPORTED_NODE.getErrorCode(), err.getErrorCode());
        assertTrue(String.valueOf(err.getParam("className")).contains("FutureExecutable"),
                "unsupported error must report the injected class name");
        assertFalse(ExecNodeBaseline.isClassified("FutureExecutable"),
                "baseline must classify the injected class as unassigned (freshness red)");
        // 绿对照：已注册类判绿（I2/A/B 族全部已注册转译）
        assertTrue(ExecToJavaTranslator.isNodeClassSupported(
                io.nop.xlang.exec.LiteralExecutable.class), "registered class must resolve (green)");
        assertTrue(ExecNodeBaseline.isClassified("LiteralExecutable"));
        assertTrue(ExecToJavaTranslator.isNodeClassSupported(
                io.nop.xlang.exec.IfExecutable.class), "B-family class resolves (translated, green)");
    }

    /** 红灯注入用的测试域合成节点类（未注册具体节点类，模拟前端演进新增节点）。 */
    static final class FutureExecutable extends AbstractExecutable {
        FutureExecutable(SourceLocation loc) {
            super(loc);
        }

        @Override
        public void display(StringBuilder sb) {
            sb.append("future");
        }

        @Override
        public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // 最小实例工厂：registeredTarget 87 类 + B 族 35 类
    // ------------------------------------------------------------------

    static final class MinimalNodeFactory {

        private MinimalNodeFactory() {
        }

        static IExecutableExpression minimalTree(String name) {
            switch (name) {
                // ---- I2 子集（28）----
                case "LiteralExecutable":
                    return io.nop.xlang.exec.LiteralExecutable.build(LOC, 1);
                case "NullExecutable":
                    return NullExecutable.NULL;
                case "SlotIdentifierExecutable":
                    return programEntry("a", new SlotIdentifierExecutable(LOC, "a", 0));
                case "SlotAssignExecutable":
                    return programEntry("a", new SlotAssignExecutable(LOC, "a", 0,
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 1)));
                case "PlusExecutable":
                    return new io.nop.xlang.exec.PlusExecutable(LOC,
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 1),
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 2));
                case "MinusExecutable":
                    return new io.nop.xlang.exec.MinusExecutable(LOC,
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 3),
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 2));
                case "MultiplyExecutable":
                    return new io.nop.xlang.exec.MultiplyExecutable(LOC,
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 3),
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 4));
                case "DivideExecutable":
                    return new io.nop.xlang.exec.DivideExecutable(LOC,
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 8),
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 2));
                case "AndExecutable":
                    return new io.nop.xlang.exec.AndExecutable(LOC,
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, true),
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, false));
                case "OrExecutable":
                    return new io.nop.xlang.exec.OrExecutable(LOC,
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, true),
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, false));
                case "NotExecutable":
                    return new io.nop.xlang.exec.NotExecutable(LOC,
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, true));
                case "EqExecutable":
                    return new io.nop.xlang.exec.EqExecutable(LOC,
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 1),
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 1));
                case "NeExecutable":
                    return new io.nop.xlang.exec.NeExecutable(LOC,
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 1),
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 2));
                case "GtExecutable":
                    return new io.nop.xlang.exec.GtExecutable(LOC,
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 2),
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 1));
                case "GeExecutable":
                    return new io.nop.xlang.exec.GeExecutable(LOC,
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 2),
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 1));
                case "LtExecutable":
                    return new io.nop.xlang.exec.LtExecutable(LOC,
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 1),
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 2));
                case "LeExecutable":
                    return new io.nop.xlang.exec.LeExecutable(LOC,
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 1),
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 2));
                case "StrictEqExecutable":
                    return new io.nop.xlang.exec.StrictEqExecutable(LOC,
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 1),
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 1));
                case "StrictNeExecutable":
                    return new io.nop.xlang.exec.StrictNeExecutable(LOC,
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 1),
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 2));
                case "CompareOpExecutable":
                    return new io.nop.xlang.exec.CompareOpExecutable(LOC, FilterOp.GT,
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 2),
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 1));
                case "ObjFunctionExecutable":
                    return io.nop.xlang.exec.ObjFunctionExecutable.build(LOC,
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, "ab"), "length", false,
                            literals(4));
                case "FunctionExecutable":
                    return io.nop.xlang.exec.FunctionExecutable.build(LOC, "print",
                            (IEvalFunction) (thisObj, args, scope) -> null, literals(4));
                case "StaticFunctionExecutable":
                    return new StaticFunctionExecutable(LOC, "java.lang.Math", "max", false,
                            ReflectionManager.instance().getClassModel(Math.class).getStaticMethodsByName("max"),
                            new IExecutableExpression[]{io.nop.xlang.exec.LiteralExecutable.build(LOC, 3)});
                case "GuardNotNullExecutable":
                    return new GuardNotNullExecutable(LOC,
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, "ab"));
                case "SeqExecutable":
                    // 嵌套同族变体经顶层类工厂产生（ISeqExecutable 接口分派）
                    return SeqExecutable.valueOf(LOC, new IExecutableExpression[]{
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 1),
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 2)});
                case "BlockExecutable":
                    return BlockExecutable.valueOf(LOC, new IExecutableExpression[]{
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 1),
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 2)});
                case "ReturnNullExecutable":
                    return new ReturnNullExecutable(io.nop.xlang.exec.LiteralExecutable.build(LOC, 1));
                case "CallFuncExecutable":
                    return programEntry(null, io.nop.xlang.exec.LiteralExecutable.build(LOC, 1));

                // ---- 覆盖 A：作用域链访问族（12）----
                case "ScopeIdentifierExecutable":
                    return new ScopeIdentifierExecutable(LOC, "x");
                case "GlobalVarExecutable":
                    return new GlobalVarExecutable(LOC, "$Math",
                            EvalGlobalRegistry.instance().getRegisteredVariable("$Math"));
                case "ScopeAssignExecutable":
                    return new ScopeAssignExecutable(LOC, "x",
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 7));
                case "ScopeSelfAssignExecutable":
                    return ScopeSelfAssignExecutable.build(LOC, "x", XLangOperator.SELF_ASSIGN_ADD,
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 3));
                case "ScopeSelfIncExecutable":
                    return new ScopeSelfIncExecutable(LOC, "x");
                case "ScopeSelfDecExecutable":
                    return new ScopeSelfDecExecutable(LOC, "x");
                case "ReferenceIdentifierExecutable":
                    return programEntry("x", new ReferenceIdentifierExecutable(LOC, "x", 0));
                case "ReferenceAssignExecutable":
                    return programEntry("x", new ReferenceAssignExecutable(LOC, "x", 0,
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 10)));
                case "ReferenceSelfAssignExecutable":
                    return programEntry("x", ReferenceSelfAssignExecutable.build(LOC, "x", 0,
                            XLangOperator.SELF_ASSIGN_ADD, io.nop.xlang.exec.LiteralExecutable.build(LOC, 5)));
                case "ReferenceSelfIncExecutable":
                    return programEntry("x", new ReferenceSelfIncExecutable(LOC, "x", 0));
                case "ReferenceSelfDecExecutable":
                    return programEntry("x", new ReferenceSelfDecExecutable(LOC, "x", 0));
                case "RenewReferenceExecutable":
                    return programEntry("x", new RenewReferenceExecutable(LOC, "x", 0));

                // ---- 覆盖 A：类型操作族（5）----
                case "CastExecutable":
                    return new CastExecutable(LOC, io.nop.xlang.exec.LiteralExecutable.build(LOC, "5"), int.class);
                case "ConvertExecutable":
                    return new ConvertExecutable(LOC, io.nop.xlang.exec.LiteralExecutable.build(LOC, "12"),
                            "$toInt", SysConverterRegistry.instance().getConverterByName("toInt"));
                case "ConvertWithDefaultExecutable":
                    return new ConvertWithDefaultExecutable(LOC,
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, "abc"), "$toInt",
                            SysConverterRegistry.instance().getConverterByName("toInt"),
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 0));
                case "InstanceOfExecutable":
                    return new InstanceOfExecutable(LOC, io.nop.xlang.exec.LiteralExecutable.build(LOC, "s"),
                            ReflectionManager.instance().buildRawType(String.class));
                case "TypeOfExecutable":
                    return new TypeOfExecutable(LOC, io.nop.xlang.exec.LiteralExecutable.build(LOC, 3));

                // ---- 覆盖 A：对象/集合构造与访问族（13）----
                case "NewObjectExecutable":
                    return new NewObjectExecutable(LOC,
                            ReflectionManager.instance().getClassModel(StringBuilder.class),
                            new IExecutableExpression[]{io.nop.xlang.exec.LiteralExecutable.build(LOC, 16)});
                case "NewListExecutable":
                    return new NewListExecutable(LOC, new io.nop.xlang.exec.ListItemExecutable[]{
                            new io.nop.xlang.exec.ListItemExecutable(false,
                                    io.nop.xlang.exec.LiteralExecutable.build(LOC, 1))});
                case "NewMapExecutable":
                    return new NewMapExecutable(LOC, new io.nop.xlang.exec.MapItemExecutable[]{
                            new io.nop.xlang.exec.MapItemExecutable(
                                    io.nop.xlang.exec.LiteralExecutable.build(LOC, "a"),
                                    io.nop.xlang.exec.LiteralExecutable.build(LOC, 1), false)});
                case "GetPropertyExecutable":
                    return new GetPropertyExecutable(LOC,
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, "ab"), false, "length");
                case "GetterGetPropertyExecutable":
                    return new GetterGetPropertyExecutable(LOC,
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, "ab"), "length",
                            (IPropertyGetter) (obj, propName, scope) -> 2);
                case "StaticGetterGetPropertyExecutable":
                    return new StaticGetterGetPropertyExecutable(LOC, "java.lang.Math", "PI",
                            ReflectionManager.instance().getClassModel(Math.class)
                                    .getStaticField("PI").getGetter());
                case "SetPropertyExecutable":
                    return new SetPropertyExecutable(LOC,
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, "ab"), "length",
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 1));
                case "SetterSetPropertyExecutable":
                    return new SetterSetPropertyExecutable(LOC,
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, "ab"), "length",
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 1),
                            (IPropertySetter) (obj, propName, value, scope) -> {
                            });
                case "GetAttrExecutable":
                    return new GetAttrExecutable(LOC, io.nop.xlang.exec.LiteralExecutable.build(LOC, "ab"),
                            false, io.nop.xlang.exec.LiteralExecutable.build(LOC, 0));
                case "SetAttrExecutable":
                    return new SetAttrExecutable(LOC, io.nop.xlang.exec.LiteralExecutable.build(LOC, "ab"),
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 0),
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 1));
                case "DeletePropertyExecutable":
                    return new io.nop.xlang.exec.DeletePropertyExecutable(LOC,
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, "ab"), "length");
                case "DeleteAttrExecutable":
                    return new io.nop.xlang.exec.DeleteAttrExecutable(LOC,
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, "ab"),
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 0));
                case "DeleteScopeVarExecutable":
                    return new io.nop.xlang.exec.DeleteScopeVarExecutable(LOC, "covVar",
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 0));
                case "ListItemExecutable":
                    return new NewListExecutable(LOC, new io.nop.xlang.exec.ListItemExecutable[]{
                            new io.nop.xlang.exec.ListItemExecutable(true,
                                    io.nop.xlang.exec.LiteralExecutable.build(LOC, 2))});
                case "MapItemExecutable":
                    return new NewMapExecutable(LOC, new io.nop.xlang.exec.MapItemExecutable[]{
                            new io.nop.xlang.exec.MapItemExecutable(
                                    io.nop.xlang.exec.LiteralExecutable.build(LOC, "a"),
                                    io.nop.xlang.exec.LiteralExecutable.build(LOC, 1), true)});
                case "MakePropertyExecutable":
                    return new io.nop.xlang.exec.MakePropertyExecutable(LOC,
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, "ab"), "length");

                // ---- 覆盖 A：绑定/守卫/调试族（9）----
                case "BindVarExecutable":
                    return programEntry("b", new BindVarExecutable(LOC, new int[]{0}, new Object[]{5},
                            new SlotIdentifierExecutable(LOC, "b", 0)));
                case "ArrayBindingAssignExecutable":
                    return programEntry("a", new ArrayBindingAssignExecutable(LOC,
                            new AssignIdentifier[]{new AssignIdentifier(LOC, 0, "a", false, null)},
                            null, io.nop.xlang.exec.LiteralExecutable.build(LOC, java.util.List.of(1))));
                case "ObjectBindingAssignExecutable":
                    return programEntry("x", new ObjectBindingAssignExecutable(LOC,
                            new PropBinding[]{new PropBinding(LOC, 0, "x", false, null, "x")},
                            null, io.nop.xlang.exec.LiteralExecutable.build(LOC, java.util.Map.of("x", 1))));
                case "InitRefSlotExecutable":
                    return programEntry("x", new InitRefSlotExecutable(LOC, "x", 0));
                case "EnhanceRefSlotExecutable":
                    return programEntry("x", new EnhanceRefSlotExecutable(LOC, "x", 0));
                case "GuardNotEmptyExecutable":
                    return new GuardNotEmptyExecutable(LOC,
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, "v"), "attr");
                case "DebugExecutable":
                    return new DebugExecutable(LOC, io.nop.xlang.exec.LiteralExecutable.build(LOC, 3),
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, "dbg"));
                case "DebugIdentifierExecutable":
                    return new DebugIdentifierExecutable(LOC, "y");
                case "VarStatusExecutable":
                    return programEntry("vs", new VarStatusExecutable(LOC, "vs", 0,
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 3)));

                // ---- 覆盖 A：slot 写族（5）----
                case "SelfAssignExecutable":
                    return programEntry("a", SelfAssignExecutable.build(LOC, "a", 0,
                            XLangOperator.SELF_ASSIGN_MULTI, io.nop.xlang.exec.LiteralExecutable.build(LOC, 3)));
                case "SelfAssignAttrExecutable":
                    return new SelfAssignAttrExecutable(LOC,
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, "ab"),
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 0),
                            XLangOperator.SELF_ASSIGN_ADD, io.nop.xlang.exec.LiteralExecutable.build(LOC, 1));
                case "SelfAssignPropertyExecutable":
                    return new SelfAssignPropertyExecutable(LOC,
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, "ab"), "length",
                            XLangOperator.SELF_ASSIGN_ADD, io.nop.xlang.exec.LiteralExecutable.build(LOC, 1));
                case "SelfIncExecutable":
                    return programEntry("a", new SelfIncExecutable(LOC, "a", 0));
                case "SelfDecExecutable":
                    return programEntry("a", new SelfDecExecutable(LOC, "a", 0));

                // ---- 并入残余算子族（15）----
                case "CloneLiteralExecutable":
                    return CloneLiteralExecutable.build(LOC, java.util.Arrays.asList(1, 2));
                case "NegExecutable":
                    return new NegExecutable(LOC, io.nop.xlang.exec.LiteralExecutable.build(LOC, 3));
                case "BitNotExecutable":
                    return new BitNotExecutable(LOC, io.nop.xlang.exec.LiteralExecutable.build(LOC, 3));
                case "NullCoalesceExecutable":
                    return new NullCoalesceExecutable(LOC, NullExecutable.NULL,
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 5));
                case "BetweenOpExecutable":
                    return new BetweenOpExecutable(LOC, FilterOp.GE,
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 5),
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 1),
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 9), false, false);
                case "AssertOpExecutable":
                    return new AssertOpExecutable(LOC, FilterOp.GT,
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 5));
                case "ConcatExecutable":
                    return new ConcatExecutable(LOC, new IExecutableExpression[]{
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, "a"),
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, "b")});
                case "RangeExecutable":
                    return new RangeExecutable(LOC, io.nop.xlang.exec.LiteralExecutable.build(LOC, 1),
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 3),
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 1));
                case "PropInExecutable":
                    return new PropInExecutable(LOC, io.nop.xlang.exec.LiteralExecutable.build(LOC, "a"),
                            new NewMapExecutable(LOC, new io.nop.xlang.exec.MapItemExecutable[]{
                                    new io.nop.xlang.exec.MapItemExecutable(
                                            io.nop.xlang.exec.LiteralExecutable.build(LOC, "a"),
                                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 1), false)}));
                case "EqNullExecutable":
                    return new EqNullExecutable(LOC, io.nop.xlang.exec.LiteralExecutable.build(LOC, "a"));
                case "NeNullExecutable":
                    return new NeNullExecutable(LOC, io.nop.xlang.exec.LiteralExecutable.build(LOC, "a"));
                case "StrictEqNullExecutable":
                    return new StrictEqNullExecutable(LOC, io.nop.xlang.exec.LiteralExecutable.build(LOC, "a"));
                case "StrictNeNullExecutable":
                    return new StrictNeNullExecutable(LOC, io.nop.xlang.exec.LiteralExecutable.build(LOC, "a"));
                case "BinaryExecutable":
                    return BinaryExecutable.valueOf(LOC, XLangOperator.MOD,
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 7),
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 3));
                case "ResolvedObjFunctionExecutable":
                    return ResolvedObjFunctionExecutable.build(LOC,
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, "ab"), "length", false,
                            new IExecutableExpression[0], (thisObj, args, scope) -> 2);

                // ---- B 族：控制流（13）----
                case "IfExecutable":
                    return new IfExecutable(LOC, io.nop.xlang.exec.LiteralExecutable.build(LOC, true),
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 1),
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 2));
                case "SwitchExecutable":
                    return new SwitchExecutable(LOC, true,
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 1),
                            new IExecutableExpression[]{io.nop.xlang.exec.LiteralExecutable.build(LOC, 1)},
                            new IExecutableExpression[]{io.nop.xlang.exec.LiteralExecutable.build(LOC, 2)},
                            new boolean[]{false}, null);
                case "ForExecutable":
                    return ForExecutable.valueOf(LOC, null,
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, true), null,
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 1));
                case "ForInExecutable":
                    // varSlot 依赖入口帧（矩阵证据形态：程序入口帧包装，I3 先例）
                    return programEntry("v", ForInExecutable.valueOf(LOC, 0,
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, java.util.List.of()),
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 1)));
                case "ForOfExecutable":
                    return programEntry("v", ForOfExecutable.valueOf(LOC, 0, false, -1,
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, java.util.List.of()),
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 1)));
                case "WhileExecutable":
                    return WhileExecutable.valueOf(LOC,
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, true),
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 1));
                case "DoWhileExecutable":
                    return DoWhileExecutable.valueOf(LOC,
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, true),
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 1));
                case "BreakExecutable":
                    // break/continue 需循环语境（前端不变式）——矩阵证据形态：入口帧 + 循环体包装
                    return programEntry(null, WhileExecutable.valueOf(LOC,
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, true),
                            new BreakExecutable(LOC)));
                case "ContinueExecutable":
                    return programEntry(null, WhileExecutable.valueOf(LOC,
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, true),
                            new ContinueExecutable(LOC)));
                case "ReturnExecutable":
                    return new ReturnExecutable(LOC, io.nop.xlang.exec.LiteralExecutable.build(LOC, 1));
                case "TryExecutable":
                    return new TryExecutable(LOC, io.nop.xlang.exec.LiteralExecutable.build(LOC, 1),
                            -1, null, null);
                case "ThrowErrorCodeExecutable":
                    return new ThrowErrorCodeExecutable(LOC,
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, "test.err"), null);
                case "ThrowExceptionExecutable":
                    return new ThrowExceptionExecutable(LOC,
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, "boom"));

                // ---- B 族：输出/节点生成（12）----
                case "OutputTextExecutable":
                    return new OutputTextExecutable(LOC, "text");
                case "OutputValueExecutable":
                    return new OutputValueExecutable(LOC, io.nop.xlang.exec.LiteralExecutable.build(LOC, 1));
                case "OutputXmlAttrExecutable":
                    return new OutputXmlAttrExecutable(LOC, "name",
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, "v"));
                case "OutputXmlExtAttrsExecutable":
                    return new OutputXmlExtAttrsExecutable(LOC, null,
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, java.util.Map.of()));
                case "GenNodeExecutable":
                    // 携带一个显式属性：宿主载体形态同时覆盖 GenNodeAttrExecutable 的真实转译证据
                    return new GenNodeExecutable(LOC, "div", null,
                            new io.nop.xlang.exec.GenNodeAttrExecutable[]{
                                    new io.nop.xlang.exec.GenNodeAttrExecutable("a",
                                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 1))},
                            null, null);
                case "GenXJsonExecutable":
                    return new GenXJsonExecutable(io.nop.xlang.exec.LiteralExecutable.build(LOC, 1));
                case "CollectJsonExecutable":
                    return new CollectJsonExecutable(LOC, io.nop.xlang.exec.LiteralExecutable.build(LOC, 1));
                case "CollectNodeExecutable":
                    return new CollectNodeExecutable(LOC, io.nop.xlang.exec.LiteralExecutable.build(LOC, 1), false);
                case "CollectSqlExecutable":
                    return new CollectSqlExecutable(io.nop.xlang.exec.LiteralExecutable.build(LOC, 1));
                case "CollectTextExecutable":
                    return new CollectTextExecutable(LOC, io.nop.xlang.exec.LiteralExecutable.build(LOC, 1));
                case "EscapeOutputExecutable":
                    return new EscapeOutputExecutable(LOC, XLangEscapeMode.none,
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, "v"));

                // ---- B 族：函数/闭包/邻接（9）+ 边缘归 B（3）----
                case "VarFunctionExecutable":
                    return VarFunctionExecutable.build(LOC,
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, "f"), false,
                            literals(4));
                case "VarExecutableFunction":
                    return new VarExecutableFunction(LOC,
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, "f"), false,
                            new IExecutableExpression[0]);
                case "LazyCompiledExecutableFunction":
                    return new LazyCompiledExecutableFunction(LOC, "f", new IExecutableExpression[0], null);
                case "FunctionalAdapterExecutable":
                    return new FunctionalAdapterExecutable(LOC,
                            (IEvalFunction) (thisObj, args, scope) -> null);
                case "CallFuncWithClosureExecutable":
                    return new CallFuncWithClosureExecutable(LOC, "__fn_m", new String[0],
                            IExecutableExpression.EMPTY_EXPRS,
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 1), new int[0], new int[0]);
                case "BuildFuncRefExecutable":
                    return BuildFuncRefExecutable.build(LOC, executableFunction(), new int[0], new int[0]);
                case "BuildClosureBodyExecutable":
                    return programEntry("c", new BuildClosureBodyExecutable(LOC, 0, new int[0], new int[0],
                            io.nop.xlang.exec.LiteralExecutable.build(LOC, 1)));
                case "ExecutableFunctionEvalAction":
                    return new ExecutableFunctionEvalAction(functionModelOf(executableFunction()));
                case "ReturnScopeValuesExecutable":
                    return new ReturnScopeValuesExecutable(LOC,
                            Collections.emptyList(),
                            List.of(io.nop.xlang.exec.LiteralExecutable.build(LOC, 1)));
                case "LocationFunction":
                    return new LocationFunction(LOC);

                default:
                    throw new IllegalArgumentException("no minimal instance factory for class: " + name);
            }
        }

        private static IExecutableExpression programEntry(String slotName, IExecutableExpression body) {
            String[] slotNames = slotName == null ? new String[0] : new String[]{slotName};
            return new CallFuncExecutable(LOC, "__fn_1", slotNames, IExecutableExpression.EMPTY_EXPRS, body);
        }

        /** n 个字面量子表达式（build 工厂默认分支需要非 null 参数数组）。 */
        private static IExecutableExpression[] literals(int n) {
            IExecutableExpression[] exprs = new IExecutableExpression[n];
            for (int i = 0; i < n; i++)
                exprs[i] = io.nop.xlang.exec.LiteralExecutable.build(LOC, i);
            return exprs;
        }

        private static ExecutableFunction executableFunction() {
            return new ExecutableFunction(LOC, LOC, "__fn_m", 0, 0, new String[0],
                    new IExecutableExpression[0], io.nop.xlang.exec.LiteralExecutable.build(LOC, 1));
        }

        /** ExecutableFunctionEvalAction 构造需要 IFunctionModel（getInvoker 返回 ExecutableFunction）。 */
        private static IFunctionModel functionModelOf(ExecutableFunction fn) {
            return (IFunctionModel) Proxy.newProxyInstance(IFunctionModel.class.getClassLoader(),
                    new Class[]{IFunctionModel.class}, (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "getInvoker":
                                return fn;
                            case "getName":
                                return fn.getFuncName();
                            case "toString":
                                return "matrix-function-model";
                            case "hashCode":
                                return System.identityHashCode(proxy);
                            case "equals":
                                return proxy == args[0];
                            default:
                                return null;
                        }
                    });
        }
    }
}
