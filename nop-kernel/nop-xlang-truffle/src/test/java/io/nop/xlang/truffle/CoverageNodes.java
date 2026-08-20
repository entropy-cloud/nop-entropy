package io.nop.xlang.truffle;

import io.nop.api.core.convert.SysConverterRegistry;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.global.EvalGlobalRegistry;
import io.nop.core.lang.eval.global.IGlobalVariableDefinition;
import io.nop.core.model.query.FilterOp;
import io.nop.core.reflect.IPropertyGetter;
import io.nop.core.reflect.IPropertySetter;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.type.IGenericType;
import io.nop.xlang.ast.XLangOperator;
import io.nop.xlang.exec.ArrayBindingAssignExecutable;
import io.nop.xlang.exec.AssertOpExecutable;
import io.nop.xlang.exec.AssignIdentifier;
import io.nop.xlang.exec.BetweenOpExecutable;
import io.nop.xlang.exec.BinaryExecutable;
import io.nop.xlang.exec.BindVarExecutable;
import io.nop.xlang.exec.BitNotExecutable;
import io.nop.xlang.exec.BlockExecutable;
import io.nop.xlang.exec.CallFuncExecutable;
import io.nop.xlang.exec.CastExecutable;
import io.nop.xlang.exec.CloneLiteralExecutable;
import io.nop.xlang.exec.ConcatExecutable;
import io.nop.xlang.exec.ConvertExecutable;
import io.nop.xlang.exec.ConvertWithDefaultExecutable;
import io.nop.xlang.exec.DebugExecutable;
import io.nop.xlang.exec.DebugIdentifierExecutable;
import io.nop.xlang.exec.EnhanceRefSlotExecutable;
import io.nop.xlang.exec.GetAttrExecutable;
import io.nop.xlang.exec.GetPropertyExecutable;
import io.nop.xlang.exec.GetterGetPropertyExecutable;
import io.nop.xlang.exec.GlobalVarExecutable;
import io.nop.xlang.exec.GuardNotEmptyExecutable;
import io.nop.xlang.exec.InitRefSlotExecutable;
import io.nop.xlang.exec.InstanceOfExecutable;
import io.nop.xlang.exec.ListItemExecutable;
import io.nop.xlang.exec.LiteralExecutable;
import io.nop.xlang.exec.MakePropertyExecutable;
import io.nop.xlang.exec.MapItemExecutable;
import io.nop.xlang.exec.NegExecutable;
import io.nop.xlang.exec.NewListExecutable;
import io.nop.xlang.exec.NewMapExecutable;
import io.nop.xlang.exec.NewObjectExecutable;
import io.nop.xlang.exec.NullCoalesceExecutable;
import io.nop.xlang.exec.NullExecutable;
import io.nop.xlang.exec.ObjectBindingAssignExecutable;
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
import io.nop.xlang.exec.ScopeAssignExecutable;
import io.nop.xlang.exec.ScopeIdentifierExecutable;
import io.nop.xlang.exec.ScopeSelfAssignExecutable;
import io.nop.xlang.exec.ScopeSelfDecExecutable;
import io.nop.xlang.exec.ScopeSelfIncExecutable;
import io.nop.xlang.exec.SelfAssignAttrExecutable;
import io.nop.xlang.exec.SelfAssignExecutable;
import io.nop.xlang.exec.SelfAssignPropertyExecutable;
import io.nop.xlang.exec.SelfDecExecutable;
import io.nop.xlang.exec.SelfIncExecutable;
import io.nop.xlang.exec.SeqExecutable;
import io.nop.xlang.exec.SetAttrExecutable;
import io.nop.xlang.exec.SetPropertyExecutable;
import io.nop.xlang.exec.SetterSetPropertyExecutable;
import io.nop.xlang.exec.SlotIdentifierExecutable;
import io.nop.xlang.exec.StaticFunctionExecutable;
import io.nop.xlang.exec.StaticGetterGetPropertyExecutable;
import io.nop.xlang.exec.TypeOfExecutable;
import io.nop.xlang.exec.VarStatusExecutable;
import io.nop.xlang.exec.BreakExecutable;
import io.nop.xlang.exec.BuildClosureBodyExecutable;
import io.nop.xlang.exec.BuildFuncRefExecutable;
import io.nop.xlang.exec.CallFuncWithClosureExecutable;
import io.nop.xlang.exec.CollectJsonExecutable;
import io.nop.xlang.exec.CollectNodeExecutable;
import io.nop.xlang.exec.CollectSqlExecutable;
import io.nop.xlang.exec.CollectTextExecutable;
import io.nop.xlang.exec.ContinueExecutable;
import io.nop.xlang.exec.DoWhileExecutable;
import io.nop.xlang.exec.EscapeOutputExecutable;
import io.nop.xlang.exec.ExecutableFunction;
import io.nop.xlang.exec.ForExecutable;
import io.nop.xlang.exec.ForInExecutable;
import io.nop.xlang.exec.ForOfExecutable;
import io.nop.xlang.exec.FunctionalAdapterExecutable;
import io.nop.xlang.exec.GenNodeExecutable;
import io.nop.xlang.exec.GenXJsonExecutable;
import io.nop.xlang.exec.IfExecutable;
import io.nop.xlang.exec.LocationFunction;
import io.nop.xlang.exec.OutputTextExecutable;
import io.nop.xlang.exec.OutputValueExecutable;
import io.nop.xlang.exec.OutputXmlAttrExecutable;
import io.nop.xlang.exec.OutputXmlExtAttrsExecutable;
import io.nop.xlang.exec.ReturnExecutable;
import io.nop.xlang.exec.SwitchExecutable;
import io.nop.xlang.exec.ThrowErrorCodeExecutable;
import io.nop.xlang.exec.ThrowExceptionExecutable;
import io.nop.xlang.exec.TryExecutable;
import io.nop.xlang.exec.VarExecutableFunction;
import io.nop.xlang.exec.VarFunctionExecutable;
import io.nop.xlang.exec.WhileExecutable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 覆盖 A（+并入残余）59 类测试域节点工厂（与 I3 java 侧矩阵工厂同构、独立实体——基线口径
 * 仍以 {@code ExecNodeBaseline} 单一事实源对齐）。
 *
 * <p>三形态构造：
 * <ul>
 * <li>{@link #minimalTree(String)}——逐类最小实例（矩阵真实翻译验证的输入）；</li>
 * <li>{@link #payloadVariant(String, int)}——同位置/同结构，仅标量语义载荷不同
 * （属性名/变量名/类型名/算子/标志/常量值等，指纹载荷测试）；</li>
 * <li>{@link #subtreeVariant(String, int)}——同标量载荷，仅子树不同（复合节点类的子表达式
 * 结构载荷，指纹载荷测试）。</li>
 * </ul>
 */
final class CoverageNodes {

    static final SourceLocation LOC = SourceLocation.fromLine("coverage-a.xpl", 1);

    private static final IEvalFunction VALUE_FN = (thisObj, args, scope) -> 2;

    /** 载荷变体（不同 lambda 站点 → 不同合成类，指纹标量载荷差异载体）。 */
    private static final IEvalFunction VALUE_FN2 = (thisObj, args, scope) -> 3;

    private static final IPropertyGetter PROP_GETTER = (obj, propName, scope) -> 2;

    private static final IPropertySetter PROP_SETTER = (obj, propName, value, scope) -> {
    };

    static {
        // 指纹载荷对照用的第二全局变量（$Math 之外的已注册名）
        EvalGlobalRegistry.instance().registerVariable("$Math2", new IGlobalVariableDefinition() {
            @Override
            public IGenericType getResolvedType() {
                return ReflectionManager.instance().buildRawType(Integer.class);
            }

            @Override
            public Object getValue(EvalRuntime rt) {
                return 2;
            }
        });
    }

    private CoverageNodes() {
    }

    static IExecutableExpression minimalTree(String name) {
        return build(name, 0, 1);
    }

    static IExecutableExpression payloadVariant(String name, int variant) {
        return build(name, variant, 1);
    }

    static IExecutableExpression subtreeVariant(String name, int childValue) {
        return build(name, 0, childValue);
    }

    /**
     * @param variant 标量载荷旋钮（0/1：属性名/变量名/类型/算子/标志/常量取两组值）
     * @param childValue 子树旋钮（子表达式字面量取值，1/2）
     */
    /**
     * @param variant 标量载荷旋钮（0/1：属性名/变量名/类型/算子/标志/常量取两组值）
     * @param childValue 子树旋钮（子表达式字面量取值，1/2）
     */
    static IExecutableExpression build(String name, int variant, int childValue) {
        switch (name) {
            // ---- I2 表达式子集（28；矩阵最小实例 + 指纹载荷共用）----
            case "LiteralExecutable":
                return LiteralExecutable.build(LOC, childValue + (variant == 0 ? 0 : 100));
            case "NullExecutable":
                return NullExecutable.NULL;
            case "SlotIdentifierExecutable":
                // slot 下标为语义载荷（id 仅为显示名，帧布局按 slot 绑名）
                return new CallFuncExecutable(LOC, "__fn_2", new String[]{"a", "b"},
                        IExecutableExpression.EMPTY_EXPRS,
                        new SlotIdentifierExecutable(LOC, "v" + variant, variant));
            case "SlotAssignExecutable":
                return new CallFuncExecutable(LOC, "__fn_2", new String[]{"a", "b"},
                        IExecutableExpression.EMPTY_EXPRS,
                        new io.nop.xlang.exec.SlotAssignExecutable(LOC, "v" + variant, variant,
                                LiteralExecutable.build(LOC, childValue)));
            case "PlusExecutable":
                return new io.nop.xlang.exec.PlusExecutable(LOC, literal(childValue), literal(childValue + 1));
            case "MinusExecutable":
                return new io.nop.xlang.exec.MinusExecutable(LOC, literal(childValue + 5), literal(childValue));
            case "MultiplyExecutable":
                return new io.nop.xlang.exec.MultiplyExecutable(LOC, literal(childValue), literal(childValue + 2));
            case "DivideExecutable":
                return new io.nop.xlang.exec.DivideExecutable(LOC, literal(childValue + 8), literal(childValue));
            case "AndExecutable":
                return new io.nop.xlang.exec.AndExecutable(LOC, literal(true), literal(childValue > 1));
            case "OrExecutable":
                return new io.nop.xlang.exec.OrExecutable(LOC, literal(false), literal(childValue > 1));
            case "NotExecutable":
                return new io.nop.xlang.exec.NotExecutable(LOC, literal(childValue > 1));
            case "EqExecutable":
                return new io.nop.xlang.exec.EqExecutable(LOC, literal(childValue), literal(childValue));
            case "NeExecutable":
                return new io.nop.xlang.exec.NeExecutable(LOC, literal(childValue), literal(childValue + 1));
            case "GtExecutable":
                return new io.nop.xlang.exec.GtExecutable(LOC, literal(childValue + 1), literal(childValue));
            case "GeExecutable":
                return new io.nop.xlang.exec.GeExecutable(LOC, literal(childValue + 1), literal(childValue));
            case "LtExecutable":
                return new io.nop.xlang.exec.LtExecutable(LOC, literal(childValue), literal(childValue + 1));
            case "LeExecutable":
                return new io.nop.xlang.exec.LeExecutable(LOC, literal(childValue), literal(childValue + 1));
            case "StrictEqExecutable":
                return new io.nop.xlang.exec.StrictEqExecutable(LOC, literal(childValue), literal(childValue));
            case "StrictNeExecutable":
                return new io.nop.xlang.exec.StrictNeExecutable(LOC, literal(childValue), literal(childValue + 1));
            case "CompareOpExecutable":
                return new io.nop.xlang.exec.CompareOpExecutable(LOC,
                        variant == 0 ? FilterOp.GT : FilterOp.GE, literal(childValue + 1), literal(childValue));
            case "ObjFunctionExecutable":
                return io.nop.xlang.exec.ObjFunctionExecutable.build(LOC, literal("ab" + childValue),
                        variant == 0 ? "length" : "size", false, literals(4));
            case "FunctionExecutable":
                return io.nop.xlang.exec.FunctionExecutable.build(LOC,
                        variant == 0 ? "print" : "println", VALUE_FN, childLiterals(childValue));
            case "StaticFunctionExecutable":
                return new StaticFunctionExecutable(LOC, "java.lang.Math",
                        variant == 0 ? "max" : "min", false,
                        ReflectionManager.instance().getClassModel(Math.class).getStaticMethodsByName("max"),
                        new IExecutableExpression[]{LiteralExecutable.build(LOC, childValue)});
            case "GuardNotNullExecutable":
                return new io.nop.xlang.exec.GuardNotNullExecutable(LOC, literal("v" + childValue));
            case "SeqExecutable":
                return seq(literal(childValue), literal(childValue + 1));
            case "BlockExecutable":
                return block(literal(childValue), literal(childValue + 1));
            case "ReturnNullExecutable":
                return new io.nop.xlang.exec.ReturnNullExecutable(literal(childValue));
            case "CallFuncExecutable":
                return new CallFuncExecutable(LOC, variant == 0 ? "__fn_1" : "__fn_2", new String[0],
                        IExecutableExpression.EMPTY_EXPRS, LiteralExecutable.build(LOC, childValue));

            // ---- 作用域链访问族（12）----
            case "ScopeIdentifierExecutable":
                return new ScopeIdentifierExecutable(LOC, variant == 0 ? "x" : "y");
            case "GlobalVarExecutable":
                return new GlobalVarExecutable(LOC, variant == 0 ? "$Math" : "$Math2",
                        EvalGlobalRegistry.instance().getRegisteredVariable(variant == 0 ? "$Math" : "$Math2"));
            case "ScopeAssignExecutable":
                return new ScopeAssignExecutable(LOC, variant == 0 ? "x" : "y",
                        LiteralExecutable.build(LOC, childValue));
            case "ScopeSelfAssignExecutable":
                return ScopeSelfAssignExecutable.build(LOC, variant == 0 ? "x" : "y",
                        XLangOperator.SELF_ASSIGN_ADD, LiteralExecutable.build(LOC, childValue));
            case "ScopeSelfIncExecutable":
                return new ScopeSelfIncExecutable(LOC, variant == 0 ? "x" : "y");
            case "ScopeSelfDecExecutable":
                return new ScopeSelfDecExecutable(LOC, variant == 0 ? "x" : "y");
            case "ReferenceIdentifierExecutable":
                return programEntry("x", new ReferenceIdentifierExecutable(LOC, variant == 0 ? "x" : "y", 0));
            case "ReferenceAssignExecutable":
                return programEntry("x", new ReferenceAssignExecutable(LOC, variant == 0 ? "x" : "y", 0,
                        LiteralExecutable.build(LOC, childValue)));
            case "ReferenceSelfAssignExecutable":
                return programEntry("x", ReferenceSelfAssignExecutable.build(LOC, variant == 0 ? "x" : "y", 0,
                        XLangOperator.SELF_ASSIGN_ADD, LiteralExecutable.build(LOC, childValue)));
            case "ReferenceSelfIncExecutable":
                return programEntry("x", new ReferenceSelfIncExecutable(LOC, variant == 0 ? "x" : "y", 0));
            case "ReferenceSelfDecExecutable":
                return programEntry("x", new ReferenceSelfDecExecutable(LOC, variant == 0 ? "x" : "y", 0));
            case "RenewReferenceExecutable":
                return programEntry("x", new RenewReferenceExecutable(LOC, variant == 0 ? "x" : "y", 0));

            // ---- 类型操作族（5）----
            case "CastExecutable":
                return new CastExecutable(LOC, LiteralExecutable.build(LOC, String.valueOf(childValue + 4)),
                        variant == 0 ? int.class : long.class);
            case "ConvertExecutable":
                return new ConvertExecutable(LOC, LiteralExecutable.build(LOC, String.valueOf(childValue + 11)),
                        variant == 0 ? "$toInt" : "$toLong",
                        SysConverterRegistry.instance().getConverterByName("toInt"));
            case "ConvertWithDefaultExecutable":
                return new ConvertWithDefaultExecutable(LOC, NullExecutable.NULL,
                        variant == 0 ? "$toInt" : "$toLong",
                        SysConverterRegistry.instance().getConverterByName("toInt"),
                        LiteralExecutable.build(LOC, childValue));
            case "InstanceOfExecutable":
                return new InstanceOfExecutable(LOC, LiteralExecutable.build(LOC, "s" + childValue),
                        ReflectionManager.instance().buildRawType(variant == 0 ? String.class : Integer.class));
            case "TypeOfExecutable":
                return new TypeOfExecutable(LOC, LiteralExecutable.build(LOC, childValue));

            // ---- 对象/集合构造与访问族（13）----
            case "NewObjectExecutable":
                return new NewObjectExecutable(LOC,
                        ReflectionManager.instance().getClassModel(variant == 0 ? StringBuilder.class : ArrayList.class),
                        new IExecutableExpression[]{LiteralExecutable.build(LOC, childValue)});
            case "NewListExecutable":
                return new NewListExecutable(LOC, new ListItemExecutable[]{
                        new ListItemExecutable(false, LiteralExecutable.build(LOC, childValue))});
            case "NewMapExecutable":
                return new NewMapExecutable(LOC, new MapItemExecutable[]{
                        new MapItemExecutable(LiteralExecutable.build(LOC, "a"),
                                LiteralExecutable.build(LOC, childValue), false)});
            case "GetPropertyExecutable":
                return new GetPropertyExecutable(LOC,
                        LiteralExecutable.build(LOC, "ab" + childValue), false,
                        variant == 0 ? "length" : "size");
            case "GetterGetPropertyExecutable":
                return new GetterGetPropertyExecutable(LOC,
                        LiteralExecutable.build(LOC, "ab" + childValue),
                        variant == 0 ? "length" : "size", PROP_GETTER);
            case "StaticGetterGetPropertyExecutable":
                return new StaticGetterGetPropertyExecutable(LOC, "java.lang.Math",
                        variant == 0 ? "PI" : "E",
                        ReflectionManager.instance().getClassModel(Math.class)
                                .getStaticField("PI").getGetter());
            case "SetPropertyExecutable":
                return new SetPropertyExecutable(LOC,
                        LiteralExecutable.build(LOC, "ab" + childValue),
                        variant == 0 ? "length" : "size",
                        LiteralExecutable.build(LOC, childValue + 1));
            case "SetterSetPropertyExecutable":
                return new SetterSetPropertyExecutable(LOC,
                        LiteralExecutable.build(LOC, "ab" + childValue),
                        variant == 0 ? "length" : "size",
                        LiteralExecutable.build(LOC, childValue + 1), PROP_SETTER);
            case "GetAttrExecutable":
                return new GetAttrExecutable(LOC, LiteralExecutable.build(LOC, "ab" + childValue),
                        variant == 0, LiteralExecutable.build(LOC, childValue - 1));
            case "SetAttrExecutable":
                return new SetAttrExecutable(LOC, LiteralExecutable.build(LOC, "ab" + childValue),
                        LiteralExecutable.build(LOC, childValue - 1),
                        LiteralExecutable.build(LOC, childValue + 1));
            case "ListItemExecutable":
                return new NewListExecutable(LOC, new ListItemExecutable[]{
                        new ListItemExecutable(variant == 1, LiteralExecutable.build(LOC, childValue))});
            case "MapItemExecutable":
                return new NewMapExecutable(LOC, new MapItemExecutable[]{
                        new MapItemExecutable(LiteralExecutable.build(LOC, "a"),
                                LiteralExecutable.build(LOC, childValue), variant == 1)});
            case "MakePropertyExecutable":
                return new MakePropertyExecutable(LOC,
                        LiteralExecutable.build(LOC, "ab" + childValue),
                        variant == 0 ? "length" : "size");

            // ---- 绑定/守卫/调试族（9）----
            case "BindVarExecutable":
                return programEntry("b", new BindVarExecutable(LOC, new int[]{0},
                        new Object[]{variant == 0 ? 5 : 6},
                        new io.nop.xlang.exec.PlusExecutable(LOC, slotRead(0), literal(childValue))));
            case "ArrayBindingAssignExecutable":
                return programEntry("a", new ArrayBindingAssignExecutable(LOC,
                        new AssignIdentifier[]{new AssignIdentifier(LOC, 0, variant == 0 ? "a" : "b", false, null)},
                        null, LiteralExecutable.build(LOC, List.of(childValue))));
            case "ObjectBindingAssignExecutable":
                return programEntry("x", new ObjectBindingAssignExecutable(LOC,
                        new PropBinding[]{new PropBinding(LOC, 0, variant == 0 ? "x" : "y", false, null,
                                variant == 0 ? "x" : "y")},
                        null, LiteralExecutable.build(LOC, Map.of(variant == 0 ? "x" : "y", childValue))));
            case "InitRefSlotExecutable":
                return programEntry("x", new InitRefSlotExecutable(LOC, variant == 0 ? "x" : "y", 0));
            case "EnhanceRefSlotExecutable":
                return programEntry("x", new EnhanceRefSlotExecutable(LOC, variant == 0 ? "x" : "y", 0));
            case "GuardNotEmptyExecutable":
                return new GuardNotEmptyExecutable(LOC,
                        LiteralExecutable.build(LOC, "v" + childValue), variant == 0 ? "attr" : "attr2");
            case "DebugExecutable":
                return new DebugExecutable(LOC, LiteralExecutable.build(LOC, childValue),
                        LiteralExecutable.build(LOC, "dbg"));
            case "DebugIdentifierExecutable":
                return new DebugIdentifierExecutable(LOC, variant == 0 ? "y" : "z");
            case "VarStatusExecutable":
                return programEntry("vs", new VarStatusExecutable(LOC, variant == 0 ? "vs" : "vs2", 0,
                        LiteralExecutable.build(LOC, List.of(childValue, childValue + 1))));

            // ---- slot 写族（5）----
            case "SelfAssignExecutable":
                return programEntry("a", SelfAssignExecutable.build(LOC, variant == 0 ? "a" : "b", 0,
                        XLangOperator.SELF_ASSIGN_MULTI, LiteralExecutable.build(LOC, childValue)));
            case "SelfAssignAttrExecutable":
                return new SelfAssignAttrExecutable(LOC,
                        LiteralExecutable.build(LOC, "ab" + childValue),
                        LiteralExecutable.build(LOC, childValue - 1),
                        variant == 0 ? XLangOperator.SELF_ASSIGN_ADD : XLangOperator.SELF_ASSIGN_MULTI,
                        LiteralExecutable.build(LOC, childValue + 1));
            case "SelfAssignPropertyExecutable":
                return new SelfAssignPropertyExecutable(LOC,
                        LiteralExecutable.build(LOC, "ab" + childValue),
                        variant == 0 ? "length" : "size",
                        XLangOperator.SELF_ASSIGN_ADD, LiteralExecutable.build(LOC, childValue + 1));
            case "SelfIncExecutable":
                return programEntry("a", new SelfIncExecutable(LOC, variant == 0 ? "a" : "b", 0));
            case "SelfDecExecutable":
                return programEntry("a", new SelfDecExecutable(LOC, variant == 0 ? "a" : "b", 0));

            // ---- 并入残余算子族（15）----
            case "CloneLiteralExecutable":
                return CloneLiteralExecutable.build(LOC, variant == 0 ? List.of(1, 2) : List.of(1, 3));
            case "NegExecutable":
                return new NegExecutable(LOC, LiteralExecutable.build(LOC, childValue));
            case "BitNotExecutable":
                return new BitNotExecutable(LOC, LiteralExecutable.build(LOC, childValue));
            case "NullCoalesceExecutable":
                return new NullCoalesceExecutable(LOC, NullExecutable.NULL,
                        LiteralExecutable.build(LOC, childValue + 3));
            case "BetweenOpExecutable":
                // BETWEEN 为真实 between 谓词 FilterOp（GE 等比较算子无 betweenOperator，仅编译产物不执行）
                return new BetweenOpExecutable(LOC, FilterOp.BETWEEN,
                        LiteralExecutable.build(LOC, childValue + 3),
                        LiteralExecutable.build(LOC, 1),
                        LiteralExecutable.build(LOC, 9), variant == 1, false);
            case "AssertOpExecutable":
                // NOT_BLANK 为真实断言谓词 FilterOp（GT 等比较算子无 predicate，仅编译产物不执行）
                return new AssertOpExecutable(LOC,
                        variant == 0 ? FilterOp.NOT_BLANK : FilterOp.NOT_NULL,
                        LiteralExecutable.build(LOC, childValue + 3));
            case "ConcatExecutable":
                return new ConcatExecutable(LOC, new IExecutableExpression[]{
                        LiteralExecutable.build(LOC, "a"),
                        LiteralExecutable.build(LOC, "b" + childValue)});
            case "RangeExecutable":
                return new RangeExecutable(LOC, LiteralExecutable.build(LOC, childValue),
                        LiteralExecutable.build(LOC, childValue + 2),
                        LiteralExecutable.build(LOC, 1));
            case "PropInExecutable":
                return new PropInExecutable(LOC, LiteralExecutable.build(LOC, "a"),
                        new NewMapExecutable(LOC, new MapItemExecutable[]{
                                new MapItemExecutable(LiteralExecutable.build(LOC, "a"),
                                        LiteralExecutable.build(LOC, childValue), false)}));
            case "EqNullExecutable":
                return new io.nop.xlang.exec.EqNullExecutable(LOC,
                        LiteralExecutable.build(LOC, "a" + childValue));
            case "NeNullExecutable":
                return new io.nop.xlang.exec.NeNullExecutable(LOC,
                        LiteralExecutable.build(LOC, "a" + childValue));
            case "StrictEqNullExecutable":
                return new io.nop.xlang.exec.StrictEqNullExecutable(LOC,
                        LiteralExecutable.build(LOC, "a" + childValue));
            case "StrictNeNullExecutable":
                return new io.nop.xlang.exec.StrictNeNullExecutable(LOC,
                        LiteralExecutable.build(LOC, "a" + childValue));
            case "BinaryExecutable":
                return BinaryExecutable.valueOf(LOC,
                        variant == 0 ? XLangOperator.MOD : XLangOperator.BIT_AND,
                        LiteralExecutable.build(LOC, 7),
                        LiteralExecutable.build(LOC, childValue + 2));
            case "ResolvedObjFunctionExecutable":
                return ResolvedObjFunctionExecutable.build(LOC,
                        LiteralExecutable.build(LOC, "ab" + childValue),
                        variant == 0 ? "length" : "size", false,
                        new IExecutableExpression[0], VALUE_FN);

            // ---- 覆盖 B：函数/闭包族（plan I7；7 有树形态 + LocationFunction）----
            case "VarFunctionExecutable":
                return VarFunctionExecutable.build(LOC, literal("f" + childValue), variant == 0,
                        childLiterals(childValue));
            case "VarExecutableFunction":
                return new VarExecutableFunction(LOC, literal("f" + childValue), variant == 0,
                        new IExecutableExpression[]{literal(childValue)});
            case "FunctionalAdapterExecutable":
                return new FunctionalAdapterExecutable(LOC, variant == 0 ? VALUE_FN : VALUE_FN2);
            case "CallFuncWithClosureExecutable":
                // 调用时闭包拷贝载体：sourceSlots 指向调用者帧 slot 0，被调帧 slotNames 自携带
                return programEntry(new String[]{"c"},
                        new CallFuncWithClosureExecutable(LOC, variant == 0 ? "__fn_m" : "__fn_n",
                                new String[0], IExecutableExpression.EMPTY_EXPRS, literal(childValue),
                                new int[]{0}, new int[0]));
            case "BuildFuncRefExecutable":
                // 捕获时闭包拷贝载体：sourceSlots 快照 + 函数体独立 RootNode（函数体 = 子树旋钮）
                return programEntry(new String[]{"c"},
                        BuildFuncRefExecutable.build(LOC,
                                new ExecutableFunction(LOC, LOC, variant == 0 ? "__fn_m" : "__fn_n",
                                        0, 0, new String[0], new IExecutableExpression[0],
                                        literal(childValue)),
                                new int[]{0}, new int[0]));
            case "BuildClosureBodyExecutable":
                return programEntry(new String[]{"c", "d"},
                        new BuildClosureBodyExecutable(LOC, variant == 0 ? 0 : 1,
                                new int[]{0}, new int[0], literal(childValue)));
            case "LocationFunction":
                return new LocationFunction(LOC);

            // ---- 覆盖 B：控制流族（13）----
            case "IfExecutable":
                return new IfExecutable(LOC, literal(variant == 0), literal(childValue), literal(2));
            case "SwitchExecutable":
                return new SwitchExecutable(LOC, variant == 0, literal(1),
                        new IExecutableExpression[]{literal(1)}, new IExecutableExpression[]{literal(childValue)},
                        new boolean[]{false}, null);
            case "ForExecutable":
                return ForExecutable.valueOf(LOC, null, literal(true), null, literal(childValue));
            case "ForInExecutable":
                return programEntry(new String[]{"k", "k2"},
                        ForInExecutable.valueOf(LOC, variant == 0 ? 0 : 1, literal(childValue),
                                literal(childValue + 1)));
            case "ForOfExecutable":
                return programEntry(new String[]{"v", "i"},
                        ForOfExecutable.valueOf(LOC, 0, variant == 0, variant == 0 ? -1 : 1,
                                literal(List.of(childValue)), literal(childValue + 1)));
            case "WhileExecutable":
                return WhileExecutable.valueOf(LOC, literal(false), literal(childValue));
            case "DoWhileExecutable":
                return DoWhileExecutable.valueOf(LOC, literal(false), literal(childValue));
            case "BreakExecutable":
                return new BreakExecutable(LOC);
            case "ContinueExecutable":
                return new ContinueExecutable(LOC);
            case "ReturnExecutable":
                return new ReturnExecutable(LOC, literal(childValue));
            case "TryExecutable":
                return programEntry(new String[]{"e"},
                        new TryExecutable(LOC, literal(childValue), variant == 0 ? -1 : 0,
                                null, literal(childValue + 1)));
            case "ThrowErrorCodeExecutable":
                return new ThrowErrorCodeExecutable(LOC, literal("test.err" + childValue), null);
            case "ThrowExceptionExecutable":
                return new ThrowExceptionExecutable(LOC, literal("boom" + childValue));

            // ---- 覆盖 B：输出/节点生成族（12；GenNodeAttrExecutable 为 GenNode 宿主载体）----
            case "OutputTextExecutable":
                return new OutputTextExecutable(LOC, variant == 0 ? "text" : "text2");
            case "OutputValueExecutable":
                return new OutputValueExecutable(LOC, literal(childValue));
            case "OutputXmlAttrExecutable":
                return new OutputXmlAttrExecutable(LOC, variant == 0 ? "name" : "name2",
                        literal("v" + childValue));
            case "OutputXmlExtAttrsExecutable":
                return new OutputXmlExtAttrsExecutable(LOC,
                        variant == 0 ? null : java.util.Set.of("ex"),
                        literal(Map.of("a", childValue)));
            case "EscapeOutputExecutable":
                return new EscapeOutputExecutable(LOC,
                        variant == 0 ? io.nop.xlang.ast.XLangEscapeMode.none : io.nop.xlang.ast.XLangEscapeMode.xml,
                        literal("v" + childValue));
            case "GenNodeExecutable":
                // 真实树恒有 tagNameExpr（visit 无条件访问；常量 tagName 的空 expr 为退化形态）
                return new GenNodeExecutable(LOC, null, literal("div" + childValue),
                        new io.nop.xlang.exec.GenNodeAttrExecutable[0], null, literal(childValue));
            case "GenXJsonExecutable":
                return new GenXJsonExecutable(literal(childValue));
            case "CollectJsonExecutable":
                return new CollectJsonExecutable(LOC, literal(childValue));
            case "CollectNodeExecutable":
                return new CollectNodeExecutable(LOC, literal(childValue), variant == 0);
            case "CollectSqlExecutable":
                return new CollectSqlExecutable(literal(childValue));
            case "CollectTextExecutable":
                return new CollectTextExecutable(LOC, literal(childValue));
            case "GenNodeAttrExecutable":
                // 属性描述符（非树节点）：宿主 GenNode 载体形态覆盖（与 java 侧矩阵同口径）
                return new GenNodeExecutable(LOC, null, literal("div"),
                        new io.nop.xlang.exec.GenNodeAttrExecutable[]{
                                new io.nop.xlang.exec.GenNodeAttrExecutable(
                                        variant == 0 ? "a" : "b", literal(childValue))},
                        null, literal(childValue));

            default:
                throw new IllegalArgumentException("no coverage-a factory for class: " + name);
        }
    }

    static IExecutableExpression programEntry(String slotName, IExecutableExpression body) {
        String[] slotNames = slotName == null ? new String[0] : new String[]{slotName};
        return new CallFuncExecutable(LOC, "__fn_1", slotNames, IExecutableExpression.EMPTY_EXPRS, body);
    }

    static IExecutableExpression programEntry(String[] slotNames, IExecutableExpression body) {
        return new CallFuncExecutable(LOC, "__fn_1", slotNames, IExecutableExpression.EMPTY_EXPRS, body);
    }

    /**
     * 改判排除类（I4 边缘裁定）的最小实例（矩阵 fail-fast 反证输入：排除类不在支持集，
     * 遇即 fail-fast）。B 族 33 类的翻译最小实例已并入 {@link #build(String, int, int)}。
     */
    static IExecutableExpression excludedMinimalTree(String name) {
        switch (name) {
            case "ReturnScopeValuesExecutable":
                return new io.nop.xlang.exec.ReturnScopeValuesExecutable(LOC, List.of(), List.of(literal(1)));
            case "ExecutableFunctionEvalAction":
                return new io.nop.xlang.exec.ExecutableFunctionEvalAction(
                        functionModelOf(executableFunction()));
            default:
                throw new IllegalArgumentException("no excluded factory for class: " + name);
        }
    }

    private static IExecutableExpression[] literals(int n) {
        IExecutableExpression[] exprs = new IExecutableExpression[n];
        for (int i = 0; i < n; i++)
            exprs[i] = LiteralExecutable.build(LOC, i);
        return exprs;
    }

    /** 子树旋钮驱动的实参数组（指纹子树变体测试用）。 */
    private static IExecutableExpression[] childLiterals(int childValue) {
        return new IExecutableExpression[]{LiteralExecutable.build(LOC, childValue),
                LiteralExecutable.build(LOC, childValue + 1)};
    }

    private static io.nop.xlang.exec.ExecutableFunction executableFunction() {
        return executableFunction(0);
    }

    /** variant 旋钮：函数名载荷差异（指纹变体测试）。 */
    static ExecutableFunction executableFunction(int variant) {
        return new ExecutableFunction(LOC, LOC, variant == 0 ? "__fn_m" : "__fn_n", 0, 0, new String[0],
                new IExecutableExpression[0], LiteralExecutable.build(LOC, 1));
    }

    /** ExecutableFunctionEvalAction 构造需要 IFunctionModel（getInvoker 返回 ExecutableFunction）。 */
    private static io.nop.core.reflect.IFunctionModel functionModelOf(
            io.nop.xlang.exec.ExecutableFunction fn) {
        return (io.nop.core.reflect.IFunctionModel) java.lang.reflect.Proxy.newProxyInstance(
                io.nop.core.reflect.IFunctionModel.class.getClassLoader(),
                new Class[]{io.nop.core.reflect.IFunctionModel.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getInvoker":
                            return fn;
                        case "getName":
                            return fn.getFuncName();
                        case "toString":
                            return "truffle-matrix-function-model";
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        case "equals":
                            return proxy == args[0];
                        default:
                            return null;
                    }
                });
    }

    static IExecutableExpression seq(IExecutableExpression... exprs) {
        return SeqExecutable.valueOf(LOC, exprs);
    }

    static IExecutableExpression block(IExecutableExpression... exprs) {
        return BlockExecutable.valueOf(LOC, exprs);
    }

    static IExecutableExpression literal(Object value) {
        return LiteralExecutable.build(LOC, value);
    }

    static IExecutableExpression slotRead(int slot) {
        return new SlotIdentifierExecutable(LOC, "v" + slot, slot);
    }

    /**
     * 红灯注入用测试域合成节点类（未注册具体节点类，模拟前端演进新增节点）。
     */
    static final class FutureExecutable extends io.nop.xlang.exec.AbstractExecutable {
        FutureExecutable(SourceLocation loc) {
            super(loc);
        }

        @Override
        public void display(StringBuilder sb) {
            sb.append("future");
        }

        @Override
        public Object execute(io.nop.core.lang.eval.IExpressionExecutor executor,
                              io.nop.core.lang.eval.EvalRuntime rt) {
            return null;
        }
    }

    static IExecutableExpression staticMax() {
        return new StaticFunctionExecutable(LOC, "java.lang.Math", "max", false,
                ReflectionManager.instance().getClassModel(Math.class).getStaticMethodsByName("max"),
                new IExecutableExpression[]{LiteralExecutable.build(LOC, 3)});
    }
}
