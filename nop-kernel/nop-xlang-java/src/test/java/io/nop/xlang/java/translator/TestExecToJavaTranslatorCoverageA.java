package io.nop.xlang.java.translator;

import io.nop.api.core.convert.SysConverterRegistry;
import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.EvalScopeImpl;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.model.query.FilterOp;
import io.nop.core.reflect.IPropertyGetter;
import io.nop.core.reflect.IPropertySetter;
import io.nop.core.reflect.ReflectionManager;
import io.nop.javac.jdk.JavaCompileResult;
import io.nop.javac.jdk.JdkJavaCompiler;
import io.nop.xlang.ast.XLangOperator;
import io.nop.xlang.exec.ArrayBindingAssignExecutable;
import io.nop.xlang.exec.AssignIdentifier;
import io.nop.xlang.exec.AssertOpExecutable;
import io.nop.xlang.exec.BetweenOpExecutable;
import io.nop.xlang.exec.BinaryExecutable;
import io.nop.xlang.exec.BindVarExecutable;
import io.nop.xlang.exec.BitNotExecutable;
import io.nop.xlang.exec.CallFuncExecutable;
import io.nop.xlang.exec.CastExecutable;
import io.nop.xlang.exec.CloneLiteralExecutable;
import io.nop.xlang.exec.ConcatExecutable;
import io.nop.xlang.exec.ConvertExecutable;
import io.nop.xlang.exec.ConvertWithDefaultExecutable;
import io.nop.xlang.exec.DebugExecutable;
import io.nop.xlang.exec.DebugIdentifierExecutable;
import io.nop.xlang.exec.EqNullExecutable;
import io.nop.xlang.exec.GetAttrExecutable;
import io.nop.xlang.exec.GetPropertyExecutable;
import io.nop.xlang.exec.GetterGetPropertyExecutable;
import io.nop.xlang.exec.GlobalVarExecutable;
import io.nop.xlang.exec.GuardNotEmptyExecutable;
import io.nop.xlang.exec.InitRefSlotExecutable;
import io.nop.xlang.exec.InstanceOfExecutable;
import io.nop.xlang.exec.ListItemExecutable;
import io.nop.xlang.exec.LiteralExecutable;
import io.nop.xlang.exec.MapItemExecutable;
import io.nop.xlang.exec.MakePropertyExecutable;
import io.nop.xlang.exec.NeNullExecutable;
import io.nop.xlang.exec.NegExecutable;
import io.nop.xlang.exec.NewListExecutable;
import io.nop.xlang.exec.NewMapExecutable;
import io.nop.xlang.exec.NullCoalesceExecutable;
import io.nop.xlang.exec.NullExecutable;
import io.nop.xlang.exec.PropBinding;
import io.nop.xlang.exec.PropInExecutable;
import io.nop.xlang.exec.RangeExecutable;
import io.nop.xlang.exec.ReferenceAssignExecutable;
import io.nop.xlang.exec.ReferenceIdentifierExecutable;
import io.nop.xlang.exec.ReferenceSelfAssignExecutable;
import io.nop.xlang.exec.ReferenceSelfIncExecutable;
import io.nop.xlang.exec.RenewReferenceExecutable;
import io.nop.xlang.exec.ResolvedObjFunctionExecutable;
import io.nop.xlang.exec.ScopeAssignExecutable;
import io.nop.xlang.exec.ScopeIdentifierExecutable;
import io.nop.xlang.exec.ScopeSelfAssignExecutable;
import io.nop.xlang.exec.ScopeSelfDecExecutable;
import io.nop.xlang.exec.ScopeSelfIncExecutable;
import io.nop.xlang.exec.SelfAssignExecutable;
import io.nop.xlang.exec.SelfDecExecutable;
import io.nop.xlang.exec.SelfIncExecutable;
import io.nop.xlang.exec.SeqExecutable;
import io.nop.xlang.exec.SetAttrExecutable;
import io.nop.xlang.exec.SetPropertyExecutable;
import io.nop.xlang.exec.SetterSetPropertyExecutable;
import io.nop.xlang.exec.SlotIdentifierExecutable;
import io.nop.xlang.exec.TypeOfExecutable;
import io.nop.xlang.exec.VarStatusExecutable;
import io.nop.xlang.java.gen.GeneratedEvalBinding;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 覆盖 A（+并入残余）转译级单测（I3 Phase 2）：每族 ≥1 真实转译断言（源码级形态断言），
 * 无法经标准前端产生的节点类以合成树覆盖（产生路径盘点见 plan Phase 1 note）；
 * 代表性族附带行为级执行（树→转译→测试域编译→执行→断言）。
 */
public class TestExecToJavaTranslatorCoverageA {

    private static final ExecToJavaTranslator TRANSLATOR = new ExecToJavaTranslator();

    private static final SourceLocation LOC = SourceLocation.fromLine("synthetic-a.xpl", 3);

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    // ------------------------------------------------------------------
    // 族 1：作用域链访问（含引用族）——corpus 可产生形态的源码级断言 + 引用族合成树
    // ------------------------------------------------------------------

    @Test
    public void testScopeChainFamilySourceShape() {
        String code = translate(new ScopeIdentifierExecutable(LOC, "x"));
        assertTrue(code.contains("XLangSemantics.getScopeValue(LOC_0, \"x\", $scope, \"x\")"), code);

        code = translate(new ScopeAssignExecutable(LOC, "x", LiteralExecutable.build(LOC, 7)));
        assertTrue(code.contains("XLangSemantics.setScopeValue(LOC_0, $scope, \"x\", Integer.valueOf(7));"), code);

        code = translate(ScopeSelfAssignExecutable.build(LOC, "x", XLangOperator.SELF_ASSIGN_ADD,
                LiteralExecutable.build(LOC, 3)));
        assertTrue(code.contains("Object $t0 = $scope.getValue(\"x\");"), code);
        assertTrue(code.contains("XLangSemantics.selfAssignValue(LOC_0, \"x+=3\", "
                + "io.nop.xlang.ast.XLangOperator.SELF_ASSIGN_ADD, $t0, Integer.valueOf(3));"), code);
        assertTrue(code.contains("XLangSemantics.setScopeValue(LOC_0, $scope, \"x\", $t1);"), code);

        code = translate(new ScopeSelfIncExecutable(LOC, "x"));
        assertTrue(code.contains("XLangSemantics.scopeSelfInc(LOC_0, $scope, \"x\", 1)"), code);

        code = translate(new ScopeSelfDecExecutable(LOC, "x"));
        assertTrue(code.contains("XLangSemantics.scopeSelfInc(LOC_0, $scope, \"x\", -1)"), code);

        code = translate(new GlobalVarExecutable(LOC, "$Math",
                io.nop.core.lang.eval.global.EvalGlobalRegistry.instance().getRegisteredVariable("$Math")));
        assertTrue(code.contains("XLangSemantics.getGlobalVarValue(LOC_0, \"$Math\", $scope, \"$Math\")"), code);
    }

    @Test
    public void testReferenceFamilySynthetic() {
        // 引用族不可经表达式出口产生（需闭包捕获）→ 合成树：程序入口帧 slot 0 = 引用 cell
        CallFuncExecutable tree = programEntry(new String[]{"x"},
                SeqExecutable.valueOf(LOC, new IExecutableExpression[]{
                        new ReferenceAssignExecutable(LOC, "x", 0, LiteralExecutable.build(LOC, 10)),
                        new ReferenceIdentifierExecutable(LOC, "x", 0)}));
        String code = translate(tree);
        assertTrue(code.contains("$v0 = XLangSemantics.setRefValue($v0, Integer.valueOf(10));"), code);
        assertTrue(code.contains("XLangSemantics.getRefValue(LOC_0, \"x\", \"x\", $v0)"), code);
        assertEquals(10, execute(tree, newScope()));
    }

    @Test
    public void testReferenceSelfOpsSynthetic() {
        IExecutableExpression selfInc = programEntry(new String[]{"x"}, SeqExecutable.valueOf(LOC,
                new IExecutableExpression[]{
                        new ReferenceAssignExecutable(LOC, "x", 0, LiteralExecutable.build(LOC, 10)),
                        new ReferenceSelfIncExecutable(LOC, "x", 0),
                        new ReferenceIdentifierExecutable(LOC, "x", 0)}));
        assertEquals(11, execute(selfInc, newScope()));

        IExecutableExpression selfAssign = programEntry(new String[]{"x"}, SeqExecutable.valueOf(LOC,
                new IExecutableExpression[]{
                        new ReferenceAssignExecutable(LOC, "x", 0, LiteralExecutable.build(LOC, 10)),
                        ReferenceSelfAssignExecutable.build(LOC, "x", 0, XLangOperator.SELF_ASSIGN_ADD,
                                LiteralExecutable.build(LOC, 5)),
                        new ReferenceIdentifierExecutable(LOC, "x", 0)}));
        assertEquals(15, execute(selfAssign, newScope()));

        IExecutableExpression renew = programEntry(new String[]{"x"}, SeqExecutable.valueOf(LOC,
                new IExecutableExpression[]{
                        new ReferenceAssignExecutable(LOC, "x", 0, LiteralExecutable.build(LOC, 10)),
                        new RenewReferenceExecutable(LOC, "x", 0),
                        ReferenceSelfAssignExecutable.build(LOC, "x", 0, XLangOperator.SELF_ASSIGN_MINUS,
                                LiteralExecutable.build(LOC, 3)),
                        new ReferenceIdentifierExecutable(LOC, "x", 0)}));
        String code = translate(renew);
        assertTrue(code.contains("XLangSemantics.renewReference($v0)"), code);
        assertEquals(7, execute(renew, newScope()));
    }

    @Test
    public void testScopeFamilyBehavior() throws Exception {
        // 行为级：$scope 复合赋值 + 自增（返回旧值）+ 按名读取
        IExecutableExpression tree = SeqExecutable.valueOf(LOC, new IExecutableExpression[]{
                new ScopeAssignExecutable(LOC, "n", LiteralExecutable.build(LOC, 5)),
                new ScopeSelfIncExecutable(LOC, "n"),
                new ScopeIdentifierExecutable(LOC, "n")});
        IEvalScope scope = newScope();
        Object ret = executeProgram(tree, scope);
        assertEquals(6, ret, "sequence returns the final scope read");
        assertEquals(6, scope.getLocalValue("n"));

        // ScopeSelfInc 自身为尾表达式时返回旧值（与解释器同语义）
        IExecutableExpression incLast = SeqExecutable.valueOf(LOC, new IExecutableExpression[]{
                new ScopeAssignExecutable(LOC, "n", LiteralExecutable.build(LOC, 5)),
                new ScopeSelfIncExecutable(LOC, "n")});
        assertEquals(5, executeProgram(incLast, newScope()), "ScopeSelfInc returns the old value");
    }

    // ------------------------------------------------------------------
    // 族 2：类型操作——corpus 形态源码级 + Cast 合成树 + typeof null 行为（缺陷修复证明）
    // ------------------------------------------------------------------

    @Test
    public void testTypeOpFamilySourceShape() {
        String code = translate(new ConvertExecutable(LOC, LiteralExecutable.build(LOC, "12"),
                "$toInt", SysConverterRegistry.instance().getConverterByName("toInt")));
        assertTrue(code.contains("XLangSemantics.convertValue(LOC_0, \"\\\"12\\\".$toInt()\", \"$toInt\", $scope, \"12\")"),
                code);

        code = translate(new ConvertWithDefaultExecutable(LOC, LiteralExecutable.build(LOC, "abc"),
                "$toInt", SysConverterRegistry.instance().getConverterByName("toInt"),
                LiteralExecutable.build(LOC, 0)));
        assertTrue(code.contains("Object $t0 = \"abc\";"), code);
        assertTrue(code.contains("if ($t0 == null) {"), code);
        assertTrue(code.contains("XLangSemantics.convertWithDefault(LOC_0,"), code);

        code = translate(new InstanceOfExecutable(LOC, LiteralExecutable.build(LOC, "s"),
                ReflectionManager.instance().buildRawType(String.class)));
        assertTrue(code.contains("XLangSemantics.instanceOf(\"s\", \"java.lang.String\")"), code);

        // Cast 无产生路径 → 合成树
        code = translate(new CastExecutable(LOC, LiteralExecutable.build(LOC, "5"), int.class));
        assertTrue(code.contains("XLangSemantics.castValue(LOC_0, \"\\\"5\\\" as int\", $scope, \"int\", \"5\")"), code);
    }

    @Test
    public void testTypeOfNullReturnsUndefined() {
        // 修复证明：typeof null 意图语义 "undefined"（原实现误引用枚举常量致 NPE）
        IExecutableExpression tree = new TypeOfExecutable(LOC, NullExecutable.NULL);
        assertEquals("undefined", execute(tree, newScope()));

        IExecutableExpression tree2 = new TypeOfExecutable(LOC, LiteralExecutable.build(LOC, 3));
        assertEquals("java.lang.Integer", execute(tree2, newScope()));

        // 解释器侧同语义（共享 helper 单一实现）
        assertEquals("undefined", interpret(new TypeOfExecutable(LOC, NullExecutable.NULL)));
    }

    // ------------------------------------------------------------------
    // 族 3：对象/集合构造与访问——corpus 形态源码级 + 无产生路径变体合成树 + 行为级
    // ------------------------------------------------------------------

    @Test
    public void testObjCollectionFamilySourceShape() {
        String code = translate(new GetPropertyExecutable(LOC, LiteralExecutable.build(LOC, "ab"),
                false, "length"));
        assertTrue(code.contains("XLangSemantics.getProperty(LOC_0, \"\\\"ab\\\".length\", \"\\\"ab\\\"\", "
                + "false, \"length\", \"ab\", $scope)"), code);

        code = translate(new SetPropertyExecutable(LOC, LiteralExecutable.build(LOC, "ab"),
                "length", LiteralExecutable.build(LOC, 1)));
        assertTrue(code.contains("XLangSemantics.setProperty(LOC_0, \"\\\"ab\\\".length = 1\", \"length\", "
                + "\"ab\", Integer.valueOf(1), $scope);"), code);

        code = translate(new GetAttrExecutable(LOC, LiteralExecutable.build(LOC, "ab"), false,
                LiteralExecutable.build(LOC, 1)));
        assertTrue(code.contains("XLangSemantics.getAttr(LOC_0,"), code);

        code = translate(new NewListExecutable(LOC, new ListItemExecutable[]{
                new ListItemExecutable(false, LiteralExecutable.build(LOC, 1)),
                new ListItemExecutable(true, LiteralExecutable.build(LOC, 2))}));
        assertTrue(code.contains("new java.util.ArrayList(2)"), code);
        assertTrue(code.contains("XLangSemantics.spreadListAdd($t0, Integer.valueOf(2));"), code);
        assertTrue(code.contains("$t0.add(Integer.valueOf(1));"), code);

        code = translate(new NewMapExecutable(LOC, new MapItemExecutable[]{
                new MapItemExecutable(LiteralExecutable.build(LOC, "a"), LiteralExecutable.build(LOC, 1), false)}));
        assertTrue(code.contains("io.nop.commons.util.CollectionHelper.newLinkedHashMap(0)"), code);
        assertTrue(code.contains("$t0.put(io.nop.commons.util.StringHelper.toString(\"a\", null), "
                + "Integer.valueOf(1));"), code);

        // StaticGetterGetProperty（corpus 可产生：Math.PI）
        code = translate(new io.nop.xlang.exec.StaticGetterGetPropertyExecutable(LOC,
                "java.lang.Math", "PI",
                ReflectionManager.instance().getClassModel(Math.class).getStaticField("PI").getGetter()));
        assertTrue(code.contains("XLangSemantics.getStaticProperty(LOC_0, \"java.lang.Math.PI\", "
                + "\"java.lang.Math\", \"PI\", $scope)"), code);

        // 无产生路径变体（合成树）：Getter/Setter/MakeProperty 按 propName 走同一解析
        code = translate(new GetterGetPropertyExecutable(LOC, LiteralExecutable.build(LOC, "ab"),
                "length", (IPropertyGetter) (obj, propName, scope) -> 2));
        assertTrue(code.contains("XLangSemantics.getterGetProperty(LOC_0, \"\\\"ab\\\".length\", \"length\", "
                + "\"ab\", $scope)"), code);

        code = translate(new SetterSetPropertyExecutable(LOC, LiteralExecutable.build(LOC, "ab"),
                "length", LiteralExecutable.build(LOC, 1),
                (IPropertySetter) (obj, propName, value, scope) -> { }));
        assertTrue(code.contains("XLangSemantics.setterSetProperty(LOC_0,"), code);

        code = translate(new MakePropertyExecutable(LOC, LiteralExecutable.build(LOC, "ab"), "length"));
        assertTrue(code.contains("XLangSemantics.makeProperty(LOC_0, \"\\\"ab\\\".length\", \"\\\"ab\\\"\", "
                + "\"length\", \"ab\", $scope)"), code);

        code = translate(new io.nop.xlang.exec.SelfAssignPropertyExecutable(LOC,
                LiteralExecutable.build(LOC, "ab"), "length", XLangOperator.SELF_ASSIGN_ADD,
                LiteralExecutable.build(LOC, 1)));
        assertTrue(code.contains("XLangSemantics.selfAssignProperty(LOC_0,"), code);

        code = translate(new io.nop.xlang.exec.SelfAssignAttrExecutable(LOC,
                LiteralExecutable.build(LOC, "ab"), LiteralExecutable.build(LOC, 0),
                XLangOperator.SELF_ASSIGN_ADD, LiteralExecutable.build(LOC, 1)));
        assertTrue(code.contains("XLangSemantics.selfAssignAttr(LOC_0,"), code);
    }

    @Test
    public void testNewMapGetPropertyBehavior() {
        NewMapExecutable map = new NewMapExecutable(LOC, new MapItemExecutable[]{
                new MapItemExecutable(LiteralExecutable.build(LOC, "a"), LiteralExecutable.build(LOC, 1), false)});
        IExecutableExpression tree = new GetPropertyExecutable(LOC, map, false, "a");
        assertEquals(1, execute(tree, newScope()));
    }

    @Test
    public void testNewObjectBehavior() {
        IExecutableExpression tree = new io.nop.xlang.exec.NewObjectExecutable(LOC,
                ReflectionManager.instance().getClassModel(StringBuilder.class),
                new IExecutableExpression[]{LiteralExecutable.build(LOC, 16)});
        Object ret = execute(tree, newScope());
        assertInstanceOf(StringBuilder.class, ret);
    }

    // ------------------------------------------------------------------
    // 族 4：绑定/守卫/调试——corpus 形态源码级 + 无产生路径节点合成树 + 行为级
    // ------------------------------------------------------------------

    @Test
    public void testBindingGuardDebugFamilySourceShape() {
        // GuardNotEmpty 仅 tag-attr 路径产生（非表达式出口）→ 合成树
        String code = translate(new GuardNotEmptyExecutable(LOC, LiteralExecutable.build(LOC, "v"), "attr"));
        assertTrue(code.contains("XLangSemantics.guardNotEmpty(LOC_0, \"\\\"v\\\"!\", \"attr\", \"v\")"), code);

        // DebugExecutable（corpus 可产生：x.$('p')）
        code = translate(new DebugExecutable(LOC, LiteralExecutable.build(LOC, 3),
                LiteralExecutable.build(LOC, "dbg")));
        assertTrue(code.contains("io.nop.xlang.utils.DebugHelper.v(LOC_0, $t0, \"3\", Integer.valueOf(3));"), code);

        // DebugIdentifier 仅调试器处理器产生 → 合成树（入口帧按名定位 deRef）
        code = translate(programEntry(new String[]{"x"},
                new DebugIdentifierExecutable(LOC, "x")));
        assertTrue(code.contains("io.nop.core.lang.eval.EvalReference.deRef($v0)"), code);

        code = translate(new DebugIdentifierExecutable(LOC, "y"));
        assertTrue(code.contains("$scope.getValue(\"y\")"), code);

        // InitRefSlot/EnhanceRefSlot/BindVar/VarStatus 全部无产生路径 → 合成树
        code = translate(programEntry(new String[]{"x"}, new InitRefSlotExecutable(LOC, "x", 0)));
        assertTrue(code.contains("$v0 = new io.nop.core.lang.eval.EvalReference(null);"), code);

        code = translate(programEntry(new String[]{"x"}, new io.nop.xlang.exec.EnhanceRefSlotExecutable(LOC, "x", 0)));
        assertTrue(code.contains("$v0 = new io.nop.core.lang.eval.EvalReference($v0);"), code);

        code = translate(programEntry(new String[]{"a", "b"},
                new BindVarExecutable(LOC, new int[]{1}, new Object[]{5},
                        new SlotIdentifierExecutable(LOC, "b", 1))));
        assertTrue(code.contains("$v1 = Integer.valueOf(5);"), code);

        code = translate(programEntry(new String[]{"vs"},
                new VarStatusExecutable(LOC, "vs", 0, LiteralExecutable.build(LOC, 3))));
        assertTrue(code.contains("XLangSemantics.varStatus(LOC_0, \"vs\", \"3\", Integer.valueOf(3));"), code);
        assertTrue(code.contains("$v0 = $t0;"), code);
    }

    @Test
    public void testArrayBindingBehavior() {
        NewListExecutable list = new NewListExecutable(LOC, new ListItemExecutable[]{
                new ListItemExecutable(false, LiteralExecutable.build(LOC, 1)),
                new ListItemExecutable(false, LiteralExecutable.build(LOC, 2))});
        IExecutableExpression tree = programEntry(new String[]{"a", "b"},
                SeqExecutable.valueOf(LOC, new IExecutableExpression[]{
                        new ArrayBindingAssignExecutable(LOC,
                                new AssignIdentifier[]{new AssignIdentifier(LOC, 0, "a", false, null),
                                        new AssignIdentifier(LOC, 1, "b", false, null)},
                                null, list),
                        new SlotIdentifierExecutable(LOC, "b", 1)}));
        assertEquals(2, execute(tree, newScope()));
    }

    @Test
    public void testObjectBindingSourceShape() {
        NewMapExecutable map = new NewMapExecutable(LOC, new MapItemExecutable[]{
                new MapItemExecutable(LiteralExecutable.build(LOC, "x"), LiteralExecutable.build(LOC, 1), false),
                new MapItemExecutable(LiteralExecutable.build(LOC, "y"), LiteralExecutable.build(LOC, 2), false)});
        String code = translate(programEntry(new String[]{"x", "y"},
                new io.nop.xlang.exec.ObjectBindingAssignExecutable(LOC,
                        new PropBinding[]{new PropBinding(LOC, 0, "x", false, null, "x"),
                                new PropBinding(LOC, 1, "y", false, null, "y")},
                        null, map)));
        assertTrue(code.contains("XLangSemantics.asMapBinding(LOC_0,"), code);
        assertTrue(code.contains("$t1.get(\"x\")"), code);
        assertTrue(code.contains("$v0 = $t2;"), code);
        assertTrue(code.contains("$v1 = $t3;"), code);
    }

    // ------------------------------------------------------------------
    // 族 5：slot 写族——源码级 + 行为级（自增返回旧值、复合赋值语义）
    // ------------------------------------------------------------------

    @Test
    public void testSlotWriteFamily() {
        String code = translate(programEntry(new String[]{"a"},
                SelfAssignExecutable.build(LOC, "a", 0, XLangOperator.SELF_ASSIGN_MULTI,
                        LiteralExecutable.build(LOC, 3))));
        assertTrue(code.contains("Object $t0 = $v0;"), code);
        assertTrue(code.contains("$v0 = XLangSemantics.selfAssignValue(LOC_0, \"a*=3\", "
                + "io.nop.xlang.ast.XLangOperator.SELF_ASSIGN_MULTI, $t0, Integer.valueOf(3));"), code);

        code = translate(programEntry(new String[]{"a"}, new SelfIncExecutable(LOC, "a", 0)));
        assertTrue(code.contains("$v0 = XLangSemantics.selfIncValue($t0, 1);"), code);

        code = translate(programEntry(new String[]{"a"}, new SelfDecExecutable(LOC, "a", 0)));
        assertTrue(code.contains("$v0 = XLangSemantics.selfIncValue($t0, -1);"), code);
    }

    @Test
    public void testSlotWriteBehavior() {
        IExecutableExpression tree = programEntry(new String[]{"a"}, SeqExecutable.valueOf(LOC,
                new IExecutableExpression[]{
                        new io.nop.xlang.exec.SlotAssignExecutable(LOC, "a", 0,
                                LiteralExecutable.build(LOC, 6)),
                        new SelfIncExecutable(LOC, "a", 0),
                        new SelfIncExecutable(LOC, "a", 0),
                        SelfAssignExecutable.build(LOC, "a", 0, XLangOperator.SELF_ASSIGN_MULTI,
                                LiteralExecutable.build(LOC, 4)),
                        new SlotIdentifierExecutable(LOC, "a", 0)}));
        assertEquals(32, execute(tree, newScope()));
    }

    // ------------------------------------------------------------------
    // 并入残余算子族——源码级 + 行为级（NullCoalesce 短路 / CloneLiteral 深拷贝新鲜性）
    // ------------------------------------------------------------------

    @Test
    public void testResidualOpsSourceShape() {
        String code = translate(new NegExecutable(LOC, LiteralExecutable.build(LOC, 3)));
        assertTrue(code.contains("io.nop.commons.util.MathHelper.neg(Integer.valueOf(3))"), code);

        code = translate(new BitNotExecutable(LOC, LiteralExecutable.build(LOC, 3)));
        assertTrue(code.contains("io.nop.commons.util.MathHelper.bneg(Integer.valueOf(3))"), code);

        code = translate(new NullCoalesceExecutable(LOC, NullExecutable.NULL,
                LiteralExecutable.build(LOC, 5)));
        assertTrue(code.contains("if ($t0 == null) {"), code);

        code = translate(new EqNullExecutable(LOC, LiteralExecutable.build(LOC, "a")));
        assertTrue(code.contains("return (\"a\") == null;"), code);

        code = translate(new NeNullExecutable(LOC, LiteralExecutable.build(LOC, "a")));
        assertTrue(code.contains("return (\"a\") != null;"), code);

        code = translate(new PropInExecutable(LOC, LiteralExecutable.build(LOC, "a"),
                new NewMapExecutable(LOC, new MapItemExecutable[]{
                        new MapItemExecutable(LiteralExecutable.build(LOC, "a"),
                                LiteralExecutable.build(LOC, 1), false)})));
        assertTrue(code.contains("XLangSemantics.propIn("), code);

        code = translate(new ConcatExecutable(LOC, new IExecutableExpression[]{
                LiteralExecutable.build(LOC, "a"), NullExecutable.NULL, LiteralExecutable.build(LOC, "c")}));
        assertTrue(code.contains("StringBuilder $t0 = new StringBuilder();"), code);
        assertTrue(code.contains("if (\"a\" != null) {"), code);
        assertTrue(code.contains("if (null != null) {"), code);
        assertTrue(code.contains("$t0.append(\"a\");"), code);
        assertTrue(code.contains("return $t0.toString();"), code);

        code = translate(new BetweenOpExecutable(LOC, FilterOp.GE, LiteralExecutable.build(LOC, 5),
                LiteralExecutable.build(LOC, 1), LiteralExecutable.build(LOC, 9), false, false));
        assertTrue(code.contains("io.nop.core.model.query.FilterOp.ge.getBetweenOperator().test("
                + "Integer.valueOf(5), Integer.valueOf(1), Integer.valueOf(9), false, false)"), code);

        code = translate(new AssertOpExecutable(LOC, FilterOp.GT, LiteralExecutable.build(LOC, 5)));
        assertTrue(code.contains("io.nop.core.model.query.FilterOp.gt.getPredicate().test(Integer.valueOf(5))"),
                code);

        code = translate(new RangeExecutable(LOC, LiteralExecutable.build(LOC, 1),
                LiteralExecutable.build(LOC, 3), LiteralExecutable.build(LOC, 1)));
        assertTrue(code.contains("XLangSemantics.range(LOC_0,"), code);

        code = translate(BinaryExecutable.valueOf(LOC, XLangOperator.MOD, LiteralExecutable.build(LOC, 7),
                LiteralExecutable.build(LOC, 3)));
        assertTrue(code.contains("io.nop.xlang.utils.EvalHelper.binaryOp(io.nop.xlang.ast.XLangOperator.MOD, "
                + "Integer.valueOf(7), Integer.valueOf(3))"), code);

        code = translate(CloneLiteralExecutable.build(LOC, java.util.Arrays.asList(1, 2)));
        assertTrue(code.contains("XLangSemantics.cloneList(new Object[]{Integer.valueOf(1), Integer.valueOf(2)})"),
                code);

        // ResolvedObjFunction 无产生路径 → 合成树：与 ObjFunctionExecutable 同一分派实现
        code = translate(ResolvedObjFunctionExecutable.build(LOC, LiteralExecutable.build(LOC, "ab"),
                "length", false, new IExecutableExpression[0],
                (thisObj, args, scope) -> 2));
        assertTrue(code.contains("XLangSemantics.invokeObjMethod(LOC_0, \"\\\"ab\\\".length()\", \"ab\", \"length\", "
                + "new Object[]{}, $scope);"), code);
    }

    @Test
    public void testResidualOpsBehavior() {
        assertEquals(5, execute(new NullCoalesceExecutable(LOC, NullExecutable.NULL,
                LiteralExecutable.build(LOC, 5)), newScope()));
        assertEquals("a", execute(new NullCoalesceExecutable(LOC, LiteralExecutable.build(LOC, "a"),
                LiteralExecutable.build(LOC, 5)), newScope()));
        assertEquals(1, execute(BinaryExecutable.valueOf(LOC, XLangOperator.MOD,
                LiteralExecutable.build(LOC, 7), LiteralExecutable.build(LOC, 3)), newScope()));

        // CloneLiteral 深拷贝新鲜性：两次执行返回不同实例且值相等
        IExecutableExpression clone = CloneLiteralExecutable.build(LOC,
                new java.util.LinkedHashMap<>(java.util.Map.of("a", 1)));
        Object first = execute(clone, newScope());
        Object second = execute(clone, newScope());
        assertNotSame(first, second);
        assertEquals(first, second);
    }

    @Test
    public void testConvertBehavior() {
        IExecutableExpression convert = new ConvertExecutable(LOC, LiteralExecutable.build(LOC, "12"),
                "$toInt", SysConverterRegistry.instance().getConverterByName("toInt"));
        assertEquals(12, execute(convert, newScope()));

        // default 仅在值为 null 时生效（与解释器一致：转换失败不回落 default）
        IExecutableExpression withDefault = new ConvertWithDefaultExecutable(LOC,
                NullExecutable.NULL, "$toInt",
                SysConverterRegistry.instance().getConverterByName("toInt"),
                LiteralExecutable.build(LOC, 0));
        assertEquals(0, execute(withDefault, newScope()));

        assertThrows(NopEvalException.class, () -> execute(new ConvertWithDefaultExecutable(LOC,
                LiteralExecutable.build(LOC, "abc"), "$toInt",
                SysConverterRegistry.instance().getConverterByName("toInt"),
                LiteralExecutable.build(LOC, 0)), newScope()));

        NopEvalException err = assertThrows(NopEvalException.class,
                () -> execute(new ConvertExecutable(LOC, LiteralExecutable.build(LOC, "abc"), "$toInt",
                        SysConverterRegistry.instance().getConverterByName("toInt")), newScope()));
        assertEquals("nop.err.api.convert-to-type-fail", err.getErrorCode());
    }

    // ------------------------------------------------------------------
    // fail-fast 反证：slot 越界（A 族节点的未处理形态显式失败，不静默通过）
    // ------------------------------------------------------------------

    @Test
    public void testFailFastSlotOutsideEntryFrame() {
        NopEvalException err = assertThrows(NopEvalException.class,
                () -> translate(new SelfIncExecutable(LOC, "a", 0)));
        assertEquals("nop.err.xlang.exec.translate-unsupported-node", err.getErrorCode());
        assertTrue(err.getParam("className").toString().contains("SelfIncExecutable"), err.toString());

        err = assertThrows(NopEvalException.class,
                () -> translate(new BindVarExecutable(LOC, new int[]{3}, new Object[]{1},
                        NullExecutable.NULL)));
        assertEquals("nop.err.xlang.exec.translate-unsupported-node", err.getErrorCode());
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private static String translate(IExecutableExpression tree) {
        return TRANSLATOR.translate("synthetic-a/shape.xpl", tree).getCode();
    }

    private static CallFuncExecutable programEntry(String[] slotNames, IExecutableExpression body) {
        return new CallFuncExecutable(LOC, "__fn_1", slotNames, IExecutableExpression.EMPTY_EXPRS, body);
    }

    private static IEvalScope newScope() {
        return new EvalScopeImpl(new LinkedHashMap<>());
    }

    private static Object execute(IExecutableExpression tree, IEvalScope scope) {
        return executeProgram(tree, scope);
    }

    private static Object executeProgram(IExecutableExpression tree, IEvalScope scope) {
        GeneratedJavaSource src = TRANSLATOR.translate("synthetic-a/exec-" + COUNTER.incrementAndGet() + ".xpl", tree);
        JdkJavaCompiler compiler = new JdkJavaCompiler();
        List<String> classPaths = JdkJavaCompiler.getDefaultClassPaths();
        JavaCompileResult result = compiler.compile(src.getClassName(), src.getCode(), classPaths);
        assertTrue(result.isSuccess(), () -> "generated source compile failed: " + result.getErrorMessage()
                + "\n==== generated code ====\n" + src.getCode());
        Class<?> clazz = result.getGeneratedClass(src.getClassName());
        try {
            Method entry = GeneratedEvalBinding.findEntryMethod(clazz);
            return entry.invoke(null, scope);
        } catch (Exception e) {
            Throwable cause = e instanceof java.lang.reflect.InvocationTargetException ? e.getCause() : e;
            if (cause instanceof RuntimeException)
                throw (RuntimeException) cause;
            throw new IllegalStateException("generated code execution failed", cause);
        }
    }

    private static final java.util.concurrent.atomic.AtomicInteger COUNTER =
            new java.util.concurrent.atomic.AtomicInteger();

    /** 解释器直接执行（共享 helper 行为一致性的对照锚点）。 */
    private static Object interpret(IExecutableExpression tree) {
        return tree.execute(io.nop.xlang.api.XLang.getExecutor(), new EvalRuntime(newScope()));
    }
}
