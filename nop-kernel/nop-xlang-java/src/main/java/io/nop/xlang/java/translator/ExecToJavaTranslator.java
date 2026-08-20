/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.java.translator;

import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.xlang.exec.AbstractBinaryExecutable;
import io.nop.xlang.exec.AbstractObjFunctionExecutable;
import io.nop.xlang.exec.AndExecutable;
import io.nop.xlang.exec.ArrayBindingAssignExecutable;
import io.nop.xlang.exec.AssignIdentifier;
import io.nop.xlang.exec.AssertOpExecutable;
import io.nop.xlang.exec.BetweenOpExecutable;
import io.nop.xlang.exec.BinaryExecutable;
import io.nop.xlang.exec.BindVarExecutable;
import io.nop.xlang.exec.BitNotExecutable;
import io.nop.xlang.exec.BlockExecutable;
import io.nop.xlang.exec.CallFuncExecutable;
import io.nop.xlang.exec.CastExecutable;
import io.nop.xlang.exec.CloneLiteralExecutable;
import io.nop.xlang.exec.CompareOpExecutable;
import io.nop.xlang.exec.ConcatExecutable;
import io.nop.xlang.exec.ConvertExecutable;
import io.nop.xlang.exec.ConvertWithDefaultExecutable;
import io.nop.xlang.exec.DebugExecutable;
import io.nop.xlang.exec.DebugIdentifierExecutable;
import io.nop.xlang.exec.DivideExecutable;
import io.nop.xlang.exec.EnhanceRefSlotExecutable;
import io.nop.xlang.exec.EqExecutable;
import io.nop.xlang.exec.EqNullExecutable;
import io.nop.xlang.exec.FunctionExecutable;
import io.nop.xlang.exec.GeExecutable;
import io.nop.xlang.exec.GetAttrExecutable;
import io.nop.xlang.exec.GetPropertyExecutable;
import io.nop.xlang.exec.GetterGetPropertyExecutable;
import io.nop.xlang.exec.GlobalVarExecutable;
import io.nop.xlang.exec.GtExecutable;
import io.nop.xlang.exec.GuardNotEmptyExecutable;
import io.nop.xlang.exec.GuardNotNullExecutable;
import io.nop.xlang.exec.ISeqExecutable;
import io.nop.xlang.exec.InitRefSlotExecutable;
import io.nop.xlang.exec.InstanceOfExecutable;
import io.nop.xlang.exec.LeExecutable;
import io.nop.xlang.exec.ListItemExecutable;
import io.nop.xlang.exec.LiteralExecutable;
import io.nop.xlang.exec.LtExecutable;
import io.nop.xlang.exec.MakePropertyExecutable;
import io.nop.xlang.exec.MapItemExecutable;
import io.nop.xlang.exec.MinusExecutable;
import io.nop.xlang.exec.MultiplyExecutable;
import io.nop.xlang.exec.NeExecutable;
import io.nop.xlang.exec.NeNullExecutable;
import io.nop.xlang.exec.NegExecutable;
import io.nop.xlang.exec.NewListExecutable;
import io.nop.xlang.exec.NewMapExecutable;
import io.nop.xlang.exec.NewObjectExecutable;
import io.nop.xlang.exec.NotExecutable;
import io.nop.xlang.exec.NullCoalesceExecutable;
import io.nop.xlang.exec.NullExecutable;
import io.nop.xlang.exec.ObjFunctionExecutable;
import io.nop.xlang.exec.ObjectBindingAssignExecutable;
import io.nop.xlang.exec.OrExecutable;
import io.nop.xlang.exec.PlusExecutable;
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
import io.nop.xlang.exec.ReturnNullExecutable;
import io.nop.xlang.exec.ScopeAssignExecutable;
import io.nop.xlang.exec.ScopeIdentifierExecutable;
import io.nop.xlang.exec.ScopeSelfAssignExecutable;
import io.nop.xlang.exec.ScopeSelfDecExecutable;
import io.nop.xlang.exec.ScopeSelfIncExecutable;
import io.nop.xlang.exec.SeqExecutable;
import io.nop.xlang.exec.SelfAssignAttrExecutable;
import io.nop.xlang.exec.SelfAssignExecutable;
import io.nop.xlang.exec.SelfAssignPropertyExecutable;
import io.nop.xlang.exec.SelfDecExecutable;
import io.nop.xlang.exec.SelfIncExecutable;
import io.nop.xlang.exec.SetAttrExecutable;
import io.nop.xlang.exec.SetPropertyExecutable;
import io.nop.xlang.exec.SetterSetPropertyExecutable;
import io.nop.xlang.exec.SlotAssignExecutable;
import io.nop.xlang.exec.SlotIdentifierExecutable;
import io.nop.xlang.exec.StaticFunctionExecutable;
import io.nop.xlang.exec.StaticGetterGetPropertyExecutable;
import io.nop.xlang.exec.StrictEqExecutable;
import io.nop.xlang.exec.StrictEqNullExecutable;
import io.nop.xlang.exec.StrictNeExecutable;
import io.nop.xlang.exec.StrictNeNullExecutable;
import io.nop.xlang.exec.TypeOfExecutable;
import io.nop.xlang.exec.VarStatusExecutable;
import io.nop.xlang.exec.XLangSemantics;
import io.nop.xlang.java.gen.EvalMethodConvention;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.xlang.XLangErrors.ARG_CLASS_NAME;
import static io.nop.xlang.XLangErrors.ARG_LOCATION;
import static io.nop.xlang.XLangErrors.ERR_EXEC_TRANSLATE_UNSUPPORTED_NODE;

/**
 * Executable 树到 Java 源码的纯函数转译器（设计 xlang-java 01 §二/§三/§四/§七）。
 *
 * <p>子集（与 I1 corpus v1 对齐）：字面量 / slot 标识符 / 算术 / 逻辑 / 比较 / 简单方法调用
 * （= 宿主方法反射分派族：ObjFunctionExecutable / StaticFunctionExecutable /
 * FunctionExecutable 系，不含函数字面量、闭包捕获、CallFunc 族局部函数调用）+ 结构性载体
 * （CallFunc 程序入口 / Block / Seq / SlotAssign / ReturnNull / GuardNotNull / Null）。
 *
 * <p>硬保证：
 * <ul>
 * <li>fail-fast——树中出现子集外节点即转译失败（报节点类名 + SourceLocation），
 * 禁止部分生成与"剩余解释"混合产物；</li>
 * <li>SourceLocation 保真——可抛错点内嵌静态 SourceLocation 常量（转译期从节点固化），
 * 抛 {@code NopException} 时携带对应常量；</li>
 * <li>语义一致——语义敏感操作统一调用 {@link XLangSemantics} 共享 helper，
 * 禁止为生成代码重写语义等价实现；</li>
 * <li>EvalMethod 约定——生成入口方法 static + 首参 {@code IEvalScope $scope}。</li>
 * </ul>
 */
public final class ExecToJavaTranslator {

    public GeneratedJavaSource translate(String resourcePath, IExecutableExpression tree) {
        Guard.notEmpty(resourcePath, "resourcePath");
        Guard.notNull(tree, "tree");
        GenContext ctx = new GenContext(resourcePath);
        if (tree instanceof CallFuncExecutable) {
            translateProgramEntry(ctx, (CallFuncExecutable) tree);
        } else {
            translatePureExpression(ctx, tree);
        }
        String className = EvalMethodConvention.GENERATED_PACKAGE + '.'
                + EvalMethodConvention.generatedClassName(resourcePath);
        return new GeneratedJavaSource(resourcePath, className,
                ctx.buildClass(EvalMethodConvention.generatedClassName(resourcePath)));
    }

    // ------------------------------------------------------------------
    // 支持集可编程枚举（矩阵注册证据之一；分派为 instanceof 链，本集合与分派分支一一对应，
    // 一致性由矩阵测试逐类真实转译验证——非清单自证）
    // ------------------------------------------------------------------

    /**
     * 转译器已注册的具体节点类（I2 子集 + 覆盖 A 五族 + 并入残余 = {@code ExecNodeBaseline.registeredTarget()}）。
     */
    static final Set<Class<?>> SUPPORTED_NODE_CLASSES = buildSupportedNodeClasses();

    private static Set<Class<?>> buildSupportedNodeClasses() {
        Set<Class<?>> set = new LinkedHashSet<>();
        // I2 子集
        Collections.addAll(set, LiteralExecutable.class, NullExecutable.class,
                SlotIdentifierExecutable.class, SlotAssignExecutable.class,
                PlusExecutable.class, MinusExecutable.class, MultiplyExecutable.class, DivideExecutable.class,
                AndExecutable.class, OrExecutable.class, NotExecutable.class,
                EqExecutable.class, NeExecutable.class, GtExecutable.class, GeExecutable.class,
                LtExecutable.class, LeExecutable.class, StrictEqExecutable.class, StrictNeExecutable.class,
                CompareOpExecutable.class,
                ObjFunctionExecutable.class, ResolvedObjFunctionExecutable.class,
                FunctionExecutable.class, StaticFunctionExecutable.class,
                GuardNotNullExecutable.class,
                SeqExecutable.class, BlockExecutable.class, ReturnNullExecutable.class,
                CallFuncExecutable.class);
        // 覆盖 A：作用域链访问族（含引用族）
        Collections.addAll(set, ScopeIdentifierExecutable.class, GlobalVarExecutable.class,
                ScopeAssignExecutable.class, ScopeSelfAssignExecutable.class,
                ScopeSelfIncExecutable.class, ScopeSelfDecExecutable.class,
                ReferenceIdentifierExecutable.class, ReferenceAssignExecutable.class,
                ReferenceSelfAssignExecutable.class, ReferenceSelfIncExecutable.class,
                ReferenceSelfDecExecutable.class, RenewReferenceExecutable.class);
        // 覆盖 A：类型操作族
        Collections.addAll(set, CastExecutable.class, ConvertExecutable.class, ConvertWithDefaultExecutable.class,
                InstanceOfExecutable.class, TypeOfExecutable.class);
        // 覆盖 A：对象/集合构造与访问族
        Collections.addAll(set, NewObjectExecutable.class, NewListExecutable.class, NewMapExecutable.class,
                GetPropertyExecutable.class, GetterGetPropertyExecutable.class,
                StaticGetterGetPropertyExecutable.class,
                SetPropertyExecutable.class, SetterSetPropertyExecutable.class,
                GetAttrExecutable.class, SetAttrExecutable.class,
                ListItemExecutable.class, MapItemExecutable.class, MakePropertyExecutable.class);
        // 覆盖 A：绑定/守卫/调试族
        Collections.addAll(set, BindVarExecutable.class, ArrayBindingAssignExecutable.class,
                ObjectBindingAssignExecutable.class, InitRefSlotExecutable.class, EnhanceRefSlotExecutable.class,
                GuardNotEmptyExecutable.class, DebugExecutable.class, DebugIdentifierExecutable.class,
                VarStatusExecutable.class);
        // 覆盖 A：slot 写族
        Collections.addAll(set, SelfAssignExecutable.class, SelfAssignAttrExecutable.class,
                SelfAssignPropertyExecutable.class, SelfIncExecutable.class, SelfDecExecutable.class);
        // 并入残余算子族
        Collections.addAll(set, CloneLiteralExecutable.class, NegExecutable.class, BitNotExecutable.class,
                NullCoalesceExecutable.class, BetweenOpExecutable.class, AssertOpExecutable.class,
                ConcatExecutable.class, RangeExecutable.class, PropInExecutable.class,
                EqNullExecutable.class, NeNullExecutable.class, StrictEqNullExecutable.class,
                StrictNeNullExecutable.class, BinaryExecutable.class);
        return Collections.unmodifiableSet(set);
    }

    /**
     * 转译器支持集（矩阵消费；文件级具体类 + 嵌套同族特化变体经继承链解析）。
     */
    public static Set<Class<?>> getSupportedNodeClasses() {
        return SUPPORTED_NODE_CLASSES;
    }

    /**
     * 节点类是否可转译（沿继承/接口链解析到已注册类；未注册 = false，对应分派 fail-fast）。
     */
    public static boolean isNodeClassSupported(Class<?> nodeClass) {
        for (Class<?> c = nodeClass; c != null; c = c.getSuperclass()) {
            if (SUPPORTED_NODE_CLASSES.contains(c))
                return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // 编译单元入口形态
    // ------------------------------------------------------------------

    /**
     * 程序入口模式：树根为 CallFuncExecutable 程序入口包装（编译前端对 script 单元的固有产物，
     * slotNames 承载帧布局），入口包装翻译为生成方法本体（与其 EvalMethod 生成方法约定同构），
     * 与"用户级 CallFunc 族局部函数调用被子集排除"不冲突。
     */
    private void translateProgramEntry(GenContext ctx, CallFuncExecutable entry) {
        IExecutableExpression[] argExprs = entry.getArgExprs();
        if (argExprs != null && argExprs.length > 0)
            throw unsupported(entry, "program entry with declared arguments");
        String[] slotNames = entry.getSlotNames() == null ? new String[0] : entry.getSlotNames();
        ctx.slotNames = slotNames;
        ctx.slotCount = slotNames.length;
        for (int i = 0; i < slotNames.length; i++) {
            ctx.line("Object $v" + i + " = null; // slot " + i + ": " + slotNames[i]);
        }
        emitRootValue(ctx, entry.getBodyExpr());
    }

    /**
     * 纯表达式模式：树根为任意子集表达式（无入口帧），单表达式返回。
     */
    private void translatePureExpression(GenContext ctx, IExecutableExpression tree) {
        emitRootValue(ctx, tree);
    }

    private void emitRootValue(GenContext ctx, IExecutableExpression body) {
        if (body == null) {
            ctx.line("return null;");
            return;
        }
        if (body instanceof ISeqExecutable) {
            IExecutableExpression[] exprs = ((ISeqExecutable) body).getExprs();
            boolean block = ((ISeqExecutable) body).isBlockStatement();
            if (exprs.length == 0) {
                ctx.line("return null;");
                return;
            }
            int last = block ? exprs.length : exprs.length - 1;
            for (int i = 0; i < last; i++) {
                genStatement(ctx, exprs[i]);
            }
            if (block) {
                ctx.line("return null;");
            } else {
                ctx.line("return " + genExpr(ctx, exprs[exprs.length - 1]) + ";");
            }
        } else {
            ctx.line("return " + genExpr(ctx, body) + ";");
        }
    }

    // ------------------------------------------------------------------
    // 语句与表达式发射
    // ------------------------------------------------------------------

    private void genStatement(GenContext ctx, IExecutableExpression node) {
        if (node instanceof ISeqExecutable) {
            for (IExecutableExpression child : ((ISeqExecutable) node).getExprs()) {
                genStatement(ctx, child);
            }
            return;
        }
        if (node instanceof ReturnNullExecutable) {
            genStatement(ctx, ((ReturnNullExecutable) node).getExecutable());
            return;
        }
        if (node instanceof SlotAssignExecutable) {
            genExpr(ctx, node);
            return;
        }
        // 一般表达式在语句位置：求值（含副作用）后弃值
        String ref = genExpr(ctx, node);
        if (isInvocationRef(ref)) {
            ctx.line(ref + ";");
        }
    }

    private boolean isInvocationRef(String ref) {
        return ref.startsWith("XLangSemantics.") || ref.startsWith("io.nop.");
    }

    private String genExpr(GenContext ctx, IExecutableExpression node) {
        // ---- 字面量 / null ----
        if (node instanceof LiteralExecutable) {
            return literal(ctx, (LiteralExecutable) node);
        }
        if (node instanceof NullExecutable) {
            return "null";
        }

        // ---- slot 读写 ----
        if (node instanceof SlotIdentifierExecutable) {
            SlotIdentifierExecutable slot = (SlotIdentifierExecutable) node;
            checkSlot(ctx, node, slot.getSlot());
            return "$v" + slot.getSlot();
        }
        if (node instanceof SlotAssignExecutable) {
            SlotAssignExecutable assign = (SlotAssignExecutable) node;
            checkSlot(ctx, node, assign.getSlot());
            String value = genExpr(ctx, assign.getExpr());
            ctx.line("$v" + assign.getSlot() + " = " + value + ";");
            return "$v" + assign.getSlot();
        }

        // ---- 算术（Plus 完整语义 = String 拼接 + 数值，统一共享 helper） ----
        if (node instanceof PlusExecutable) {
            return invoke2(ctx, "plus", (AbstractBinaryExecutable) node);
        }
        if (node instanceof MinusExecutable) {
            return invoke2(ctx, "minus", (AbstractBinaryExecutable) node);
        }
        if (node instanceof MultiplyExecutable) {
            return invoke2(ctx, "multiply", (AbstractBinaryExecutable) node);
        }
        if (node instanceof DivideExecutable) {
            return invoke2(ctx, "divide", (AbstractBinaryExecutable) node);
        }

        // ---- 比较（宽松与 Strict 变体 live 同实现，均走 xlangEq 族共享 helper） ----
        if (node instanceof EqExecutable || node instanceof StrictEqExecutable) {
            return invoke2(ctx, "eq", (AbstractBinaryExecutable) node);
        }
        if (node instanceof NeExecutable || node instanceof StrictNeExecutable) {
            return invoke2(ctx, "ne", (AbstractBinaryExecutable) node);
        }
        if (node instanceof GtExecutable) {
            return invoke2(ctx, "gt", (AbstractBinaryExecutable) node);
        }
        if (node instanceof GeExecutable) {
            return invoke2(ctx, "ge", (AbstractBinaryExecutable) node);
        }
        if (node instanceof LtExecutable) {
            return invoke2(ctx, "lt", (AbstractBinaryExecutable) node);
        }
        if (node instanceof LeExecutable) {
            return invoke2(ctx, "le", (AbstractBinaryExecutable) node);
        }

        // ---- 逻辑（短路语义以 if 块保真：右侧仅在需要时求值） ----
        if (node instanceof AndExecutable) {
            return genShortCircuit(ctx, (AbstractBinaryExecutable) node, true);
        }
        if (node instanceof OrExecutable) {
            return genShortCircuit(ctx, (AbstractBinaryExecutable) node, false);
        }
        if (node instanceof NotExecutable) {
            String expr = genExpr(ctx, ((NotExecutable) node).getExpr());
            return "!XLangSemantics.truthy(" + expr + ")";
        }

        // ---- 过滤比较谓词（FilterOp 枚举统一谓词源，直引枚举常量） ----
        if (node instanceof CompareOpExecutable) {
            CompareOpExecutable cmp = (CompareOpExecutable) node;
            String left = genExpr(ctx, cmp.getLeft());
            String right = genExpr(ctx, cmp.getRight());
            return "io.nop.core.model.query.FilterOp." + cmp.getFilterOp().name()
                    + ".getBiPredicate().test(" + left + ", " + right + ")";
        }

        // ---- 简单方法调用：宿主方法反射分派族 ----
        if (node instanceof ObjFunctionExecutable) {
            return genObjFunction(ctx, (ObjFunctionExecutable) node);
        }
        if (node instanceof FunctionExecutable) {
            return genGlobalFunction(ctx, (FunctionExecutable) node);
        }
        if (node instanceof StaticFunctionExecutable) {
            return genStaticFunction(ctx, (StaticFunctionExecutable) node);
        }

        // ---- null 守卫 ----
        if (node instanceof GuardNotNullExecutable) {
            GuardNotNullExecutable guard = (GuardNotNullExecutable) node;
            String expr = genExpr(ctx, guard.getExpr());
            return "XLangSemantics.guardNotNull(" + ctx.locRef(guard) + ", " + displayOf(guard)
                    + ", " + expr + ")";
        }

        // ---- 序列（表达式位置） ----
        if (node instanceof ISeqExecutable) {
            return genSeqAsExpr(ctx, (ISeqExecutable) node);
        }
        if (node instanceof ReturnNullExecutable) {
            genStatement(ctx, ((ReturnNullExecutable) node).getExecutable());
            return "null";
        }

        // ---- 覆盖 A：作用域链访问族（经 $scope API，语义敏感操作统一共享 helper） ----
        if (node instanceof ScopeIdentifierExecutable) {
            ScopeIdentifierExecutable scope = (ScopeIdentifierExecutable) node;
            return "XLangSemantics.getScopeValue(" + ctx.locRef(node) + ", " + displayOf(node)
                    + ", $scope, \"" + escape(scope.getVarName()) + "\")";
        }
        if (node instanceof GlobalVarExecutable) {
            GlobalVarExecutable global = (GlobalVarExecutable) node;
            return "XLangSemantics.getGlobalVarValue(" + ctx.locRef(node) + ", " + displayOf(node)
                    + ", $scope, \"" + escape(global.getVarName()) + "\")";
        }
        if (node instanceof ScopeAssignExecutable) {
            ScopeAssignExecutable assign = (ScopeAssignExecutable) node;
            String value = hoist(ctx, genExpr(ctx, assign.getExpr()));
            ctx.line("XLangSemantics.setScopeValue(" + ctx.locRef(node) + ", $scope, \""
                    + escape(assign.getVarName()) + "\", " + value + ");");
            return value;
        }
        if (node instanceof ScopeSelfAssignExecutable) {
            return genScopeSelfAssign(ctx, (ScopeSelfAssignExecutable) node);
        }
        if (node instanceof ScopeSelfIncExecutable) {
            ScopeSelfIncExecutable inc = (ScopeSelfIncExecutable) node;
            return "XLangSemantics.scopeSelfInc(" + ctx.locRef(node) + ", $scope, \""
                    + escape(inc.getVarName()) + "\", 1)";
        }
        if (node instanceof ScopeSelfDecExecutable) {
            ScopeSelfDecExecutable dec = (ScopeSelfDecExecutable) node;
            return "XLangSemantics.scopeSelfInc(" + ctx.locRef(node) + ", $scope, \""
                    + escape(dec.getVarName()) + "\", -1)";
        }

        // ---- 覆盖 A：引用族（帧 slot 的 EvalReference cell，生成代码以 $v 局部变量承载 slot 值） ----
        if (node instanceof ReferenceIdentifierExecutable) {
            ReferenceIdentifierExecutable ref = (ReferenceIdentifierExecutable) node;
            checkSlot(ctx, node, ref.getSlot());
            return "XLangSemantics.getRefValue(" + ctx.locRef(node) + ", " + displayOf(node) + ", \""
                    + escape(ref.getId()) + "\", $v" + ref.getSlot() + ")";
        }
        if (node instanceof ReferenceAssignExecutable) {
            ReferenceAssignExecutable assign = (ReferenceAssignExecutable) node;
            checkSlot(ctx, node, assign.getSlot());
            String value = hoist(ctx, genExpr(ctx, assign.getExpr()));
            ctx.line("$v" + assign.getSlot() + " = XLangSemantics.setRefValue($v" + assign.getSlot()
                    + ", " + value + ");");
            return value;
        }
        if (node instanceof ReferenceSelfAssignExecutable) {
            return genReferenceSelfAssign(ctx, (ReferenceSelfAssignExecutable) node);
        }
        if (node instanceof ReferenceSelfIncExecutable) {
            return genReferenceSelfIncDec(ctx, (ReferenceSelfIncExecutable) node, 1);
        }
        if (node instanceof ReferenceSelfDecExecutable) {
            return genReferenceSelfIncDec(ctx, (ReferenceSelfDecExecutable) node, -1);
        }
        if (node instanceof RenewReferenceExecutable) {
            RenewReferenceExecutable renew = (RenewReferenceExecutable) node;
            checkSlot(ctx, node, renew.getSlot());
            ctx.line("$v" + renew.getSlot() + " = (io.nop.core.lang.eval.EvalReference) "
                    + "XLangSemantics.renewReference($v" + renew.getSlot() + ");");
            return "null";
        }

        // ---- 覆盖 A：类型操作族 ----
        if (node instanceof CastExecutable) {
            CastExecutable cast = (CastExecutable) node;
            return "XLangSemantics.castValue(" + ctx.locRef(node) + ", " + displayOf(node) + ", $scope, \""
                    + escape(cast.getClazz().getName()) + "\", " + genExpr(ctx, cast.getExpr()) + ")";
        }
        if (node instanceof ConvertExecutable) {
            ConvertExecutable convert = (ConvertExecutable) node;
            return "XLangSemantics.convertValue(" + ctx.locRef(node) + ", " + displayOf(node) + ", \""
                    + escape(convert.getFuncName()) + "\", $scope, " + genExpr(ctx, convert.getExpr()) + ")";
        }
        if (node instanceof ConvertWithDefaultExecutable) {
            return genConvertWithDefault(ctx, (ConvertWithDefaultExecutable) node);
        }
        if (node instanceof InstanceOfExecutable) {
            InstanceOfExecutable inst = (InstanceOfExecutable) node;
            return "XLangSemantics.instanceOf(" + genExpr(ctx, inst.getExpr()) + ", \""
                    + escape(inst.getType().getRawClass().getName()) + "\")";
        }
        if (node instanceof TypeOfExecutable) {
            return "XLangSemantics.typeOf(" + genExpr(ctx, ((TypeOfExecutable) node).getExpr()) + ")";
        }

        // ---- 覆盖 A：对象/集合构造与访问族（属性反射统一共享 helper，与解释器同一实现） ----
        if (node instanceof MakePropertyExecutable) {
            MakePropertyExecutable make = (MakePropertyExecutable) node;
            return "XLangSemantics.makeProperty(" + ctx.locRef(node) + ", " + displayOf(node) + ", "
                    + displayOf(make.getObjExpr()) + ", \"" + escape(make.getPropName()) + "\", "
                    + genExpr(ctx, make.getObjExpr()) + ", $scope)";
        }
        if (node instanceof GetPropertyExecutable) {
            GetPropertyExecutable get = (GetPropertyExecutable) node;
            return "XLangSemantics.getProperty(" + ctx.locRef(node) + ", " + displayOf(node) + ", "
                    + displayOf(get.getObjExpr()) + ", " + get.isOptional() + ", \""
                    + escape(get.getPropName()) + "\", " + genExpr(ctx, get.getObjExpr()) + ", $scope)";
        }
        if (node instanceof GetterGetPropertyExecutable) {
            GetterGetPropertyExecutable get = (GetterGetPropertyExecutable) node;
            return "XLangSemantics.getterGetProperty(" + ctx.locRef(node) + ", " + displayOf(node) + ", \""
                    + escape(get.getPropName()) + "\", " + genExpr(ctx, get.getObjExpr()) + ", $scope)";
        }
        if (node instanceof StaticGetterGetPropertyExecutable) {
            StaticGetterGetPropertyExecutable get = (StaticGetterGetPropertyExecutable) node;
            return "XLangSemantics.getStaticProperty(" + ctx.locRef(node) + ", " + displayOf(node) + ", \""
                    + escape(get.getClassName()) + "\", \"" + escape(get.getPropName()) + "\", $scope)";
        }
        if (node instanceof SetPropertyExecutable) {
            SetPropertyExecutable set = (SetPropertyExecutable) node;
            String obj = hoist(ctx, genExpr(ctx, set.getObjExpr()));
            String value = hoist(ctx, genExpr(ctx, set.getValueExpr()));
            ctx.line("XLangSemantics.setProperty(" + ctx.locRef(node) + ", " + displayOf(node) + ", \""
                    + escape(set.getPropName()) + "\", " + obj + ", " + value + ", $scope);");
            return value;
        }
        if (node instanceof SetterSetPropertyExecutable) {
            SetterSetPropertyExecutable set = (SetterSetPropertyExecutable) node;
            String obj = hoist(ctx, genExpr(ctx, set.getObjExpr()));
            String value = hoist(ctx, genExpr(ctx, set.getValueExpr()));
            ctx.line("XLangSemantics.setterSetProperty(" + ctx.locRef(node) + ", " + displayOf(node) + ", \""
                    + escape(set.getPropName()) + "\", " + obj + ", " + value + ", $scope);");
            return value;
        }
        if (node instanceof SelfAssignPropertyExecutable) {
            SelfAssignPropertyExecutable self = (SelfAssignPropertyExecutable) node;
            String obj = hoist(ctx, genExpr(ctx, self.getObjExpr()));
            String value = hoist(ctx, genExpr(ctx, self.getValueExpr()));
            return "XLangSemantics.selfAssignProperty(" + ctx.locRef(node) + ", " + displayOf(node) + ", \""
                    + escape(self.getPropName()) + "\", " + operatorRef(self.getOperator()) + ", "
                    + obj + ", " + value + ", $scope)";
        }
        if (node instanceof GetAttrExecutable) {
            GetAttrExecutable get = (GetAttrExecutable) node;
            return "XLangSemantics.getAttr(" + ctx.locRef(node) + ", " + displayOf(node) + ", "
                    + displayOf(get.getObjExpr()) + ", " + displayOf(get.getAttrExpr()) + ", "
                    + get.isOptional() + ", " + genExpr(ctx, get.getObjExpr()) + ", "
                    + genExpr(ctx, get.getAttrExpr()) + ")";
        }
        if (node instanceof SetAttrExecutable) {
            SetAttrExecutable set = (SetAttrExecutable) node;
            String obj = hoist(ctx, genExpr(ctx, set.getObjExpr()));
            String attr = hoist(ctx, genExpr(ctx, set.getAttrExpr()));
            String value = hoist(ctx, genExpr(ctx, set.getValueExpr()));
            ctx.line("XLangSemantics.setAttr(" + ctx.locRef(node) + ", " + displayOf(node) + ", "
                    + displayOf(set.getAttrExpr()) + ", " + obj + ", " + attr + ", " + value + ");");
            return value;
        }
        if (node instanceof SelfAssignAttrExecutable) {
            SelfAssignAttrExecutable self = (SelfAssignAttrExecutable) node;
            String obj = hoist(ctx, genExpr(ctx, self.getObjExpr()));
            String attr = hoist(ctx, genExpr(ctx, self.getAttrExpr()));
            String value = hoist(ctx, genExpr(ctx, self.getValueExpr()));
            return "XLangSemantics.selfAssignAttr(" + ctx.locRef(node) + ", " + displayOf(node) + ", "
                    + displayOf(self.getAttrExpr()) + ", " + operatorRef(self.getOperator()) + ", "
                    + obj + ", " + attr + ", " + value + ")";
        }
        if (node instanceof NewObjectExecutable) {
            NewObjectExecutable newExpr = (NewObjectExecutable) node;
            String args = genArgs(ctx, newExpr.getArgExprs());
            return "XLangSemantics.newInstance(" + ctx.locRef(node) + ", " + displayOf(node) + ", \""
                    + escape(newExpr.getClassModel().getClassName()) + "\", new Object[]{" + args
                    + "}, $scope)";
        }
        if (node instanceof NewListExecutable) {
            return genNewList(ctx, (NewListExecutable) node);
        }
        if (node instanceof NewMapExecutable) {
            return genNewMap(ctx, (NewMapExecutable) node);
        }

        // ---- 覆盖 A：绑定/守卫/调试族 ----
        if (node instanceof BindVarExecutable) {
            return genBindVar(ctx, (BindVarExecutable) node);
        }
        if (node instanceof ArrayBindingAssignExecutable) {
            return genArrayBinding(ctx, (ArrayBindingAssignExecutable) node);
        }
        if (node instanceof ObjectBindingAssignExecutable) {
            return genObjectBinding(ctx, (ObjectBindingAssignExecutable) node);
        }
        if (node instanceof InitRefSlotExecutable) {
            InitRefSlotExecutable init = (InitRefSlotExecutable) node;
            checkSlot(ctx, node, init.getSlot());
            ctx.line("$v" + init.getSlot() + " = new io.nop.core.lang.eval.EvalReference(null);");
            return "null";
        }
        if (node instanceof EnhanceRefSlotExecutable) {
            EnhanceRefSlotExecutable enhance = (EnhanceRefSlotExecutable) node;
            checkSlot(ctx, node, enhance.getSlot());
            ctx.line("$v" + enhance.getSlot() + " = new io.nop.core.lang.eval.EvalReference($v"
                    + enhance.getSlot() + ");");
            return "null";
        }
        if (node instanceof GuardNotEmptyExecutable) {
            GuardNotEmptyExecutable guard = (GuardNotEmptyExecutable) node;
            return "XLangSemantics.guardNotEmpty(" + ctx.locRef(node) + ", " + displayOf(node) + ", \""
                    + escape(String.valueOf(guard.getTarget())) + "\", "
                    + genExpr(ctx, guard.getExpr()) + ")";
        }
        if (node instanceof DebugExecutable) {
            return genDebug(ctx, (DebugExecutable) node);
        }
        if (node instanceof DebugIdentifierExecutable) {
            return genDebugIdentifier(ctx, (DebugIdentifierExecutable) node);
        }
        if (node instanceof VarStatusExecutable) {
            return genVarStatus(ctx, (VarStatusExecutable) node);
        }

        // ---- 覆盖 A：slot 写族（帧 slot ↔ $v 局部变量直译 + 共享 selfAssignValue） ----
        if (node instanceof SelfAssignExecutable) {
            return genSlotSelfAssign(ctx, (SelfAssignExecutable) node);
        }
        if (node instanceof SelfIncExecutable) {
            return genSlotSelfIncDec(ctx, (SelfIncExecutable) node, 1);
        }
        if (node instanceof SelfDecExecutable) {
            return genSlotSelfIncDec(ctx, (SelfDecExecutable) node, -1);
        }

        // ---- 并入残余算子族 ----
        if (node instanceof CloneLiteralExecutable) {
            return literal(ctx, node, ((CloneLiteralExecutable) node).getValue());
        }
        if (node instanceof NegExecutable) {
            return "io.nop.commons.util.MathHelper.neg(" + genExpr(ctx, ((NegExecutable) node).getExpr()) + ")";
        }
        if (node instanceof BitNotExecutable) {
            return "io.nop.commons.util.MathHelper.bneg(" + genExpr(ctx, ((BitNotExecutable) node).getExpr()) + ")";
        }
        if (node instanceof NullCoalesceExecutable) {
            return genNullCoalesce(ctx, (NullCoalesceExecutable) node);
        }
        if (node instanceof EqNullExecutable) {
            return parenRef(genExpr(ctx, ((EqNullExecutable) node).getExpr())) + " == null";
        }
        if (node instanceof StrictEqNullExecutable) {
            return parenRef(genExpr(ctx, ((StrictEqNullExecutable) node).getExpr())) + " == null";
        }
        if (node instanceof NeNullExecutable) {
            return parenRef(genExpr(ctx, ((NeNullExecutable) node).getExpr())) + " != null";
        }
        if (node instanceof StrictNeNullExecutable) {
            return parenRef(genExpr(ctx, ((StrictNeNullExecutable) node).getExpr())) + " != null";
        }
        if (node instanceof PropInExecutable) {
            PropInExecutable in = (PropInExecutable) node;
            return "XLangSemantics.propIn(" + genExpr(ctx, in.getLeft()) + ", " + genExpr(ctx, in.getRight()) + ")";
        }
        if (node instanceof ConcatExecutable) {
            return genConcat(ctx, (ConcatExecutable) node);
        }
        if (node instanceof BetweenOpExecutable) {
            BetweenOpExecutable between = (BetweenOpExecutable) node;
            return "io.nop.core.model.query.FilterOp." + between.getFilterOp().name()
                    + ".getBetweenOperator().test(" + genExpr(ctx, between.getValueExpr()) + ", "
                    + genExpr(ctx, between.getMinExpr()) + ", " + genExpr(ctx, between.getMaxExpr()) + ", "
                    + between.isExcludeMin() + ", " + between.isExcludeMax() + ")";
        }
        if (node instanceof AssertOpExecutable) {
            AssertOpExecutable assertOp = (AssertOpExecutable) node;
            return "io.nop.core.model.query.FilterOp." + assertOp.getFilterOp().name()
                    + ".getPredicate().test(" + genExpr(ctx, assertOp.getValueExpr()) + ")";
        }
        if (node instanceof RangeExecutable) {
            RangeExecutable range = (RangeExecutable) node;
            return "XLangSemantics.range(" + ctx.locRef(node) + ", " + displayOf(node) + ", "
                    + genExpr(ctx, range.getBeginExpr()) + ", " + genExpr(ctx, range.getEndExpr()) + ", "
                    + genExpr(ctx, range.getStepExpr()) + ")";
        }
        if (node instanceof BinaryExecutable) {
            BinaryExecutable binary = (BinaryExecutable) node;
            return "io.nop.xlang.utils.EvalHelper.binaryOp(" + operatorRef(binary.getOperator()) + ", "
                    + genExpr(ctx, binary.getLeft()) + ", " + genExpr(ctx, binary.getRight()) + ")";
        }
        if (node instanceof ResolvedObjFunctionExecutable) {
            return genObjFunction(ctx, (AbstractObjFunctionExecutable) node);
        }

        // CallFunc 族在非根位置 = 局部函数调用，子集排除（fail-fast）
        throw unsupported(node, null);
    }

    // ------------------------------------------------------------------
    // 覆盖 A（+并入残余）：各族生成方法
    // ------------------------------------------------------------------

    private static String operatorRef(io.nop.xlang.ast.XLangOperator op) {
        return "io.nop.xlang.ast.XLangOperator." + op.name();
    }

    private static String parenRef(String ref) {
        return "(" + ref + ")";
    }

    private String genScopeSelfAssign(GenContext ctx, ScopeSelfAssignExecutable node) {
        // 求值顺序与解释器一致：先读旧值，再求值 change，最后复合与写回
        String old = ctx.temp();
        ctx.line("Object " + old + " = $scope.getValue(\"" + escape(node.getVarName()) + "\");");
        String change = hoist(ctx, genExpr(ctx, node.getExpr()));
        String result = ctx.temp();
        ctx.line("Object " + result + " = XLangSemantics.selfAssignValue(" + ctx.locRef(node) + ", "
                + displayOf(node) + ", " + operatorRef(node.getOperator()) + ", " + old + ", " + change + ");");
        ctx.line("XLangSemantics.setScopeValue(" + ctx.locRef(node) + ", $scope, \""
                + escape(node.getVarName()) + "\", " + result + ");");
        return result;
    }

    private String genReferenceSelfAssign(GenContext ctx, ReferenceSelfAssignExecutable node) {
        checkSlot(ctx, node, node.getSlot());
        String ref = ctx.temp();
        ctx.line("io.nop.core.lang.eval.EvalReference " + ref + " = XLangSemantics.asRef(" + ctx.locRef(node)
                + ", " + displayOf(node) + ", \"" + escape(node.getVarName()) + "\", $v" + node.getSlot() + ");");
        String change = hoist(ctx, genExpr(ctx, node.getExpr()));
        String result = ctx.temp();
        ctx.line("Object " + result + " = XLangSemantics.selfAssignValue(" + ctx.locRef(node) + ", "
                + displayOf(node) + ", " + operatorRef(node.getOperator()) + ", " + ref + ".getValue(), "
                + change + ");");
        ctx.line(ref + ".setValue(" + result + ");");
        return result;
    }

    private String genReferenceSelfIncDec(GenContext ctx, IExecutableExpression ref, int delta) {
        ReferenceVarInfo info = referenceVarInfo(ref);
        checkSlot(ctx, ref, info.slot);
        String refVar = ctx.temp();
        ctx.line("io.nop.core.lang.eval.EvalReference " + refVar + " = XLangSemantics.asRef(" + ctx.locRef(ref)
                + ", " + displayOf(ref) + ", \"" + escape(info.varName) + "\", $v" + info.slot + ");");
        String old = ctx.temp();
        ctx.line("Object " + old + " = " + refVar + ".getValue();");
        ctx.line(refVar + ".setValue(XLangSemantics.selfIncValue(" + old + ", " + delta + "));");
        return old;
    }

    private String genSlotSelfAssign(GenContext ctx, SelfAssignExecutable node) {
        checkSlot(ctx, node, node.getSlot());
        String old = ctx.temp();
        ctx.line("Object " + old + " = $v" + node.getSlot() + ";");
        String change = hoist(ctx, genExpr(ctx, node.getExpr()));
        ctx.line("$v" + node.getSlot() + " = XLangSemantics.selfAssignValue(" + ctx.locRef(node) + ", "
                + displayOf(node) + ", " + operatorRef(node.getOperator()) + ", " + old + ", " + change + ");");
        return "$v" + node.getSlot();
    }

    private String genSlotSelfIncDec(GenContext ctx, IExecutableExpression node, int delta) {
        SelfVarInfo info = selfVarInfo(node);
        checkSlot(ctx, node, info.slot);
        String old = ctx.temp();
        ctx.line("Object " + old + " = $v" + info.slot + ";");
        ctx.line("$v" + info.slot + " = XLangSemantics.selfIncValue(" + old + ", " + delta + ");");
        return old;
    }

    private String genConvertWithDefault(GenContext ctx, ConvertWithDefaultExecutable node) {
        // 短路保真：value 无条件提升只求值一次；default 仅在 value == null 时求值（与解释器一致）
        String value = ctx.temp();
        ctx.line("Object " + value + " = " + genExpr(ctx, node.getExpr()) + ";");
        String def = ctx.temp();
        ctx.line("Object " + def + " = null;");
        ctx.line("if (" + value + " == null) {");
        ctx.indent();
        ctx.line(def + " = " + hoist(ctx, genExpr(ctx, node.getDefaultExpr())) + ";");
        ctx.unindent();
        ctx.line("}");
        return "XLangSemantics.convertWithDefault(" + ctx.locRef(node) + ", " + displayOf(node) + ", \""
                + escape(node.getFuncName()) + "\", $scope, " + value + ", " + def + ")";
    }

    private String genNewList(GenContext ctx, NewListExecutable node) {
        io.nop.xlang.exec.ListItemExecutable[] items = node.getItems();
        String list = ctx.temp();
        ctx.line("java.util.List " + list + " = new java.util.ArrayList(" + items.length + ");");
        for (io.nop.xlang.exec.ListItemExecutable item : items) {
            String value = hoist(ctx, genExpr(ctx, item.getValueExpr()));
            if (item.isSpread()) {
                ctx.line("XLangSemantics.spreadListAdd(" + list + ", " + value + ");");
            } else {
                ctx.line(list + ".add(" + value + ");");
            }
        }
        return list;
    }

    private String genNewMap(GenContext ctx, NewMapExecutable node) {
        io.nop.xlang.exec.MapItemExecutable[] items = node.getItems();
        String map = ctx.temp();
        ctx.line("java.util.Map " + map + " = io.nop.commons.util.CollectionHelper.newLinkedHashMap("
                + (items.length / 2) + ");");
        for (io.nop.xlang.exec.MapItemExecutable item : items) {
            if (item.isSpread()) {
                String value = hoist(ctx, genExpr(ctx, item.getValueExpr()));
                ctx.line("XLangSemantics.spreadMapPut(" + map + ", " + value + ");");
            } else {
                String key = hoist(ctx, genExpr(ctx, item.getKeyExpr()));
                String value = hoist(ctx, genExpr(ctx, item.getValueExpr()));
                ctx.line(map + ".put(io.nop.commons.util.StringHelper.toString(" + key + ", null), " + value + ");");
            }
        }
        return map;
    }

    private String genBindVar(GenContext ctx, BindVarExecutable node) {
        int[] slots = node.getSlots();
        Object[] vars = node.getVars();
        for (int i = 0; i < slots.length; i++) {
            checkSlot(ctx, node, slots[i]);
            ctx.line("$v" + slots[i] + " = " + literal(ctx, node, vars[i]) + ";");
        }
        return genExpr(ctx, node.getExpr());
    }

    private String genArrayBinding(GenContext ctx, ArrayBindingAssignExecutable node) {
        String value = hoist(ctx, genExpr(ctx, node.getExpr()));
        String list = ctx.temp();
        ctx.line("java.util.List " + list + " = XLangSemantics.asListBinding(" + ctx.locRef(node) + ", "
                + displayOf(node) + ", " + value + ");");
        AssignIdentifier[] bindings = node.getElementBindings();
        for (int i = 0; i < bindings.length; i++) {
            // 数组解构元素不应用默认初始化器（与解释器现状一致）
            emitBindingAssignValue(ctx, node, bindings[i], list + ".get(" + i + ")");
        }
        AssignIdentifier rest = node.getRestBinding();
        if (rest != null) {
            String tail = ctx.temp();
            ctx.line("Object " + tail + " = io.nop.commons.util.CollectionHelper.copyTail(" + list + ", "
                    + bindings.length + ");");
            emitBindingAssignValue(ctx, node, rest, tail);
        }
        return "null";
    }

    private String genObjectBinding(GenContext ctx, ObjectBindingAssignExecutable node) {
        String value = hoist(ctx, genExpr(ctx, node.getExpr()));
        String map = ctx.temp();
        ctx.line("java.util.Map " + map + " = XLangSemantics.asMapBinding(" + ctx.locRef(node) + ", "
                + displayOf(node) + ", " + value + ");");
        PropBinding[] bindings = node.getPropBindings();
        for (PropBinding binding : bindings) {
            String propValue = ctx.temp();
            ctx.line("Object " + propValue + " = " + map + ".get(\"" + escape(binding.getKey()) + "\");");
            if (binding.getInitializer() != null) {
                ctx.line("if (" + propValue + " == null) {");
                ctx.indent();
                ctx.line(propValue + " = " + hoist(ctx, genExpr(ctx, binding.getInitializer())) + ";");
                ctx.unindent();
                ctx.line("}");
            }
            emitBindingAssignValue(ctx, node, binding, propValue);
        }
        AssignIdentifier rest = node.getRestBinding();
        if (rest != null) {
            String tail = ctx.temp();
            ctx.line("java.util.Map " + tail + " = new java.util.LinkedHashMap();");
            ctx.line("for (java.util.Iterator it = " + map + ".entrySet().iterator(); it.hasNext(); ) {");
            ctx.indent();
            ctx.line("java.util.Map.Entry entry = (java.util.Map.Entry) it.next();");
            ctx.line("if (!" + propKeySetRef(ctx, node, bindings) + ".contains(entry.getKey())) {");
            ctx.indent();
            ctx.line(tail + ".put(entry.getKey(), entry.getValue());");
            ctx.unindent();
            ctx.line("}");
            ctx.unindent();
            ctx.line("}");
            emitBindingAssignValue(ctx, node, rest, tail);
        }
        return "null";
    }

    private String propKeySetRef(GenContext ctx, ObjectBindingAssignExecutable node, PropBinding[] bindings) {
        StringBuilder sb = new StringBuilder("java.util.Collections.unmodifiableSet(new java.util.HashSet(");
        sb.append("java.util.Arrays.asList(");
        for (int i = 0; i < bindings.length; i++) {
            if (i > 0)
                sb.append(", ");
            sb.append('\"').append(escape(bindings[i].getKey())).append('\"');
        }
        sb.append(")))");
        return sb.toString();
    }

    /**
     * AssignIdentifier.assign 语义：slot>=0 时 useRef 走引用 cell 写、否则直写 slot；slot<0 写 scope。
     */
    private void emitBindingAssignValue(GenContext ctx, IExecutableExpression node, AssignIdentifier binding,
                                        String valueRef) {
        int slot = binding.getVarSlot();
        if (slot >= 0) {
            checkSlot(ctx, node, slot);
            if (binding.isUseRef()) {
                ctx.line("$v" + slot + " = XLangSemantics.setRefValue($v" + slot + ", " + valueRef + ");");
            } else {
                ctx.line("$v" + slot + " = " + valueRef + ";");
            }
        } else {
            ctx.line("XLangSemantics.setScopeValue(" + bindingLocRef(ctx, binding) + ", $scope, \""
                    + escape(binding.getVarName()) + "\", " + valueRef + ");");
        }
    }

    private String bindingLocRef(GenContext ctx, AssignIdentifier binding) {
        SourceLocation loc = binding.getLocation();
        return loc == null ? "null" : ctx.locRefOf(loc);
    }

    private String genDebug(GenContext ctx, DebugExecutable node) {
        // 调试语义承载：与解释器同一 DebugHelper.v 调用（loc 常量内嵌），日志副作用不在对拍断言域
        String value = hoist(ctx, genExpr(ctx, node.getValueExpr()));
        String prefix = hoist(ctx, genExpr(ctx, node.getPrefixExpr()));
        String prefixStr = ctx.temp();
        ctx.line("String " + prefixStr + " = io.nop.api.core.convert.ConvertHelper.toString(" + prefix + ");");
        ctx.line("io.nop.xlang.utils.DebugHelper.v(" + ctx.locRef(node) + ", " + prefixStr + ", "
                + displayOf(node.getValueExpr()) + ", " + value + ");");
        return value;
    }

    private String genDebugIdentifier(GenContext ctx, DebugIdentifierExecutable node) {
        // ExprExecHelper.getVar 同一查找序：入口帧按名定位（deRef）→ 回落 scope 按名读取
        String varName = node.getVarName();
        int slot = ctx.indexOfSlotName(varName);
        if (slot >= 0) {
            checkSlot(ctx, node, slot);
            return "io.nop.core.lang.eval.EvalReference.deRef($v" + slot + ")";
        }
        return "$scope.getValue(\"" + escape(varName) + "\")";
    }

    private String genVarStatus(GenContext ctx, VarStatusExecutable node) {
        checkSlot(ctx, node, node.getVarStatusSlot());
        String vs = ctx.temp();
        ctx.line("Object " + vs + " = XLangSemantics.varStatus(" + ctx.locRef(node) + ", " + displayOf(node)
                + ", " + displayOf(node.getItemsExpr()) + ", " + genExpr(ctx, node.getItemsExpr()) + ");");
        ctx.line("if (" + vs + " != null) {");
        ctx.indent();
        ctx.line("$v" + node.getVarStatusSlot() + " = " + vs + ";");
        ctx.unindent();
        ctx.line("}");
        return vs;
    }

    private String genNullCoalesce(GenContext ctx, NullCoalesceExecutable node) {
        // 左值无条件提升为临时变量（字面量/局部变量引用不可赋值），短路保真：右侧仅在左侧为 null 时求值
        String temp = ctx.temp();
        ctx.line("Object " + temp + " = " + genExpr(ctx, node.getLeft()) + ";");
        ctx.line("if (" + temp + " == null) {");
        ctx.indent();
        ctx.line(temp + " = " + hoist(ctx, genExpr(ctx, node.getRight())) + ";");
        ctx.unindent();
        ctx.line("}");
        return temp;
    }

    private String genConcat(GenContext ctx, ConcatExecutable node) {
        // 逐元素求值后非 null 追加（求值顺序与解释器一致）
        String sb = ctx.temp();
        ctx.line("StringBuilder " + sb + " = new StringBuilder();");
        for (IExecutableExpression expr : node.getExprs()) {
            String value = hoist(ctx, genExpr(ctx, expr));
            ctx.line("if (" + value + " != null) {");
            ctx.indent();
            ctx.line(sb + ".append(" + value + ");");
            ctx.unindent();
            ctx.line("}");
        }
        return sb + ".toString()";
    }

    private static final class ReferenceVarInfo {
        final String varName;
        final int slot;

        ReferenceVarInfo(String varName, int slot) {
            this.varName = varName;
            this.slot = slot;
        }
    }

    private static ReferenceVarInfo referenceVarInfo(IExecutableExpression node) {
        if (node instanceof ReferenceSelfIncExecutable)
            return new ReferenceVarInfo(((ReferenceSelfIncExecutable) node).getVarName(),
                    ((ReferenceSelfIncExecutable) node).getSlot());
        if (node instanceof ReferenceSelfDecExecutable)
            return new ReferenceVarInfo(((ReferenceSelfDecExecutable) node).getVarName(),
                    ((ReferenceSelfDecExecutable) node).getSlot());
        throw new IllegalStateException("unexpected node: " + node);
    }

    private static final class SelfVarInfo {
        final String varName;
        final int slot;

        SelfVarInfo(String varName, int slot) {
            this.varName = varName;
            this.slot = slot;
        }
    }

    private static SelfVarInfo selfVarInfo(IExecutableExpression node) {
        if (node instanceof SelfIncExecutable)
            return new SelfVarInfo(((SelfIncExecutable) node).getVarName(),
                    ((SelfIncExecutable) node).getSlot());
        if (node instanceof SelfDecExecutable)
            return new SelfVarInfo(((SelfDecExecutable) node).getVarName(),
                    ((SelfDecExecutable) node).getSlot());
        throw new IllegalStateException("unexpected node: " + node);
    }

    private String genSeqAsExpr(GenContext ctx, ISeqExecutable seq) {
        IExecutableExpression[] exprs = seq.getExprs();
        if (seq.isBlockStatement()) {
            for (IExecutableExpression child : exprs) {
                genStatement(ctx, child);
            }
            return "null";
        }
        if (exprs.length == 0)
            return "null";
        for (int i = 0, n = exprs.length - 1; i < n; i++) {
            genStatement(ctx, exprs[i]);
        }
        String last = genExpr(ctx, exprs[exprs.length - 1]);
        if (isInvocationRef(last)) {
            String temp = ctx.temp();
            ctx.line("Object " + temp + " = " + last + ";");
            return temp;
        }
        return last;
    }

    private String invoke2(GenContext ctx, String helper, AbstractBinaryExecutable node) {
        String left = genExpr(ctx, node.getLeft());
        String right = genExpr(ctx, node.getRight());
        return "XLangSemantics." + helper + "(" + left + ", " + right + ")";
    }

    /**
     * And/Or 短路：左侧求值后按真值决定右侧是否求值（右侧的副作用语句收在 if 分支内）。
     * And: truthy(left) ? right : left；Or: !truthy(left) ? right : left。
     */
    private String genShortCircuit(GenContext ctx, AbstractBinaryExecutable node, boolean and) {
        String left = hoist(ctx, genExpr(ctx, node.getLeft()));
        String temp = ctx.temp();
        ctx.line("Object " + temp + ";");
        ctx.line("if (" + (and ? "" : "!") + "XLangSemantics.truthy(" + left + ")) {");
        ctx.indent();
        String right = genExpr(ctx, node.getRight());
        ctx.line(temp + " = " + right + ";");
        ctx.unindent();
        ctx.line("} else {");
        ctx.indent();
        ctx.line(temp + " = " + left + ";");
        ctx.unindent();
        ctx.line("}");
        return temp;
    }

    /**
     * 调用形态的引用在会被多次使用或进入条件分支前提升为临时变量，保证只求值一次。
     */
    private String hoist(GenContext ctx, String ref) {
        if (!isInvocationRef(ref))
            return ref;
        String temp = ctx.temp();
        ctx.line("Object " + temp + " = " + ref + ";");
        return temp;
    }

    /**
     * 实例方法分派（ObjFunctionExecutable 族）：接收者先求值；null 时短路返回 null
     * （右侧实参不求值，与解释器逐分支求值顺序一致）；否则实参求值后经共享 helper 分派调用。
     */
    private String genObjFunction(GenContext ctx, AbstractObjFunctionExecutable fn) {
        String obj = hoist(ctx, genExpr(ctx, fn.getObjExpr()));
        String temp = ctx.temp();
        ctx.line("Object " + temp + ";");
        ctx.line("if (" + obj + " == null) {");
        ctx.indent();
        ctx.line(temp + " = null;");
        ctx.unindent();
        ctx.line("} else {");
        ctx.indent();
        String args = genArgs(ctx, fn.getArgs());
        ctx.line(temp + " = XLangSemantics.invokeObjMethod(" + ctx.locRef(fn) + ", "
                + displayOf(fn) + ", " + obj + ", \"" + escape(fn.getFuncName())
                + "\", new Object[]{" + args + "}, $scope);");
        ctx.unindent();
        ctx.line("}");
        return temp;
    }

    /**
     * 注册全局函数分派（FunctionExecutable 族）：运行时经同一全局注册表解析后统一调用。
     */
    private String genGlobalFunction(GenContext ctx, FunctionExecutable node) {
        String args = genArgs(ctx, node.getArgs());
        return "XLangSemantics.invokeGlobalFunction(" + ctx.locRef(node) + ", " + displayOf(node)
                + ", \"" + escape(node.getFuncName()) + "\", new Object[]{" + args + "}, $scope)";
    }

    /**
     * 类静态方法分派（StaticFunctionExecutable 族）：按 className/funcName 运行时解析
     * 方法集合后统一调用。
     */
    private String genStaticFunction(GenContext ctx, StaticFunctionExecutable node) {
        String args = genArgs(ctx, node.getArgExprs());
        return "XLangSemantics.invokeStaticMethodResolved(" + ctx.locRef(node) + ", " + displayOf(node)
                + ", \"" + escape(node.getClassName()) + "\", \"" + escape(node.getFuncName())
                + "\", " + node.isOptional() + ", new Object[]{" + args + "}, $scope)";
    }

    private String genArgs(GenContext ctx, IExecutableExpression[] argExprs) {
        if (argExprs == null || argExprs.length == 0)
            return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < argExprs.length; i++) {
            if (i > 0)
                sb.append(", ");
            sb.append(genExpr(ctx, argExprs[i]));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // 字面量 / 位置常量 / fail-fast
    // ------------------------------------------------------------------

    private String literal(GenContext ctx, LiteralExecutable node) {
        return literal(ctx, node, node.getValue());
    }

    private String literal(GenContext ctx, IExecutableExpression node, Object value) {
        if (value == null)
            return "null";
        if (value instanceof String)
            return "\"" + escape((String) value) + "\"";
        if (value instanceof Boolean)
            return ((Boolean) value) ? "Boolean.TRUE" : "Boolean.FALSE";
        if (value instanceof Integer)
            return "Integer.valueOf(" + value + ")";
        if (value instanceof Long)
            return "Long.valueOf(" + value + "L)";
        if (value instanceof Double) {
            double d = (Double) value;
            if (Double.isNaN(d))
                return "Double.NaN";
            if (d == Double.POSITIVE_INFINITY)
                return "Double.POSITIVE_INFINITY";
            if (d == Double.NEGATIVE_INFINITY)
                return "Double.NEGATIVE_INFINITY";
            return "Double.valueOf(" + d + ")";
        }
        if (value instanceof Float)
            return "Float.valueOf(" + value + "F)";
        if (value instanceof BigDecimal)
            return "new java.math.BigDecimal(\"" + value + "\")";
        if (value instanceof BigInteger)
            return "new java.math.BigInteger(\"" + value + "\")";
        if (value instanceof Character) {
            return "Character.valueOf('" + escapeChar((Character) value) + "')";
        }
        if (value instanceof Enum) {
            Enum<?> e = (Enum<?>) value;
            return e.getDeclaringClass().getName() + "." + e.name();
        }
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            StringBuilder sb = new StringBuilder();
            sb.append("XLangSemantics.cloneList(new Object[]{");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0)
                    sb.append(", ");
                sb.append(literal(ctx, node, list.get(i)));
            }
            sb.append("})");
            return sb.toString();
        }
        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            StringBuilder sb = new StringBuilder();
            sb.append("XLangSemantics.cloneMap(new Object[]{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first)
                    sb.append(", ");
                first = false;
                sb.append(literal(ctx, node, entry.getKey()));
                sb.append(", ");
                sb.append(literal(ctx, node, entry.getValue()));
            }
            sb.append("})");
            return sb.toString();
        }
        throw unsupported(ctx, node, "unsupported literal type: " + value.getClass().getName());
    }

    private void checkSlot(GenContext ctx, IExecutableExpression node, int slot) {
        if (slot < 0 || slot >= ctx.slotCount)
            throw unsupported(ctx, node, "slot read/write outside program entry frame: slot=" + slot);
    }

    private NopEvalException unsupported(IExecutableExpression node, String detail) {
        return unsupported(null, node, detail);
    }

    private NopEvalException unsupported(GenContext ctx, IExecutableExpression node, String detail) {
        SourceLocation loc = node.getLocation();
        String className = node.getClass().getName();
        NopEvalException err = new NopEvalException(ERR_EXEC_TRANSLATE_UNSUPPORTED_NODE, null);
        err.param(ARG_CLASS_NAME, className).param(ARG_LOCATION, String.valueOf(loc));
        if (detail != null)
            err.param("detail", detail);
        if (loc != null)
            err.loc(loc);
        return err;
    }

    private static String displayOf(IExecutableExpression node) {
        StringBuilder sb = new StringBuilder();
        node.display(sb);
        return "\"" + escape(sb.toString()) + "\"";
    }

    static String escape(String str) {
        StringBuilder sb = new StringBuilder(str.length() + 8);
        for (int i = 0, n = str.length(); i < n; i++) {
            escapeChar(str.charAt(i), sb);
        }
        return sb.toString();
    }

    private static String escapeChar(char c) {
        StringBuilder sb = new StringBuilder();
        escapeChar(c, sb);
        return sb.toString();
    }

    private static void escapeChar(char c, StringBuilder sb) {
        switch (c) {
            case '\\':
                sb.append("\\\\");
                break;
            case '"':
                sb.append("\\\"");
                break;
            case '\'':
                sb.append("\\'");
                break;
            case '\n':
                sb.append("\\n");
                break;
            case '\r':
                sb.append("\\r");
                break;
            case '\t':
                sb.append("\\t");
                break;
            case '\b':
                sb.append("\\b");
                break;
            case '\f':
                sb.append("\\f");
                break;
            default:
                if (c < 0x20 || c > 0x7e) {
                    sb.append(String.format("\\u%04x", (int) c));
                } else {
                    sb.append(c);
                }
        }
    }

    // ------------------------------------------------------------------
    // 生成上下文
    // ------------------------------------------------------------------

    private static final class GenContext {
        final String resourcePath;
        final List<String> lines = new ArrayList<>();
        final Map<String, String> locConstants = new LinkedHashMap<>();
        final List<SourceLocation> locDecls = new ArrayList<>();
        String[] slotNames = new String[0];
        int tempCounter;
        int slotCount;
        int indent = 1;

        GenContext(String resourcePath) {
            this.resourcePath = resourcePath;
        }

        int indexOfSlotName(String varName) {
            for (int i = 0; i < slotNames.length; i++) {
                if (varName.equals(slotNames[i]))
                    return i;
            }
            return -1;
        }

        void line(String text) {
            StringBuilder sb = new StringBuilder(text.length() + 8);
            for (int i = 0; i < indent; i++)
                sb.append("    ");
            sb.append(text);
            lines.add(sb.toString());
        }

        void indent() {
            indent++;
        }

        void unindent() {
            indent--;
        }

        String temp() {
            return "$t" + (tempCounter++);
        }

        /**
         * 可抛错点的 SourceLocation 常量引用（按 path:line:col 去重注册）。
         */
        String locRef(IExecutableExpression node) {
            return locRefOf(node.getLocation());
        }

        String locRefOf(SourceLocation loc) {
            if (loc == null)
                return "null";
            String key = loc.getPath() + ":" + loc.getLine() + ":" + loc.getCol();
            String name = locConstants.get(key);
            if (name == null) {
                name = "LOC_" + locConstants.size();
                locConstants.put(key, name);
                locDecls.add(loc);
            }
            return name;
        }

        String buildClass(String className) {
            StringBuilder sb = new StringBuilder(1024);
            sb.append("// source: ").append(resourcePath).append('\n');
            sb.append("package ").append(EvalMethodConvention.GENERATED_PACKAGE).append(";\n\n");
            sb.append("import ").append(SourceLocation.class.getName()).append(";\n");
            sb.append("import ").append(IEvalScope.class.getName()).append(";\n");
            sb.append("import ").append(XLangSemantics.class.getName()).append(";\n\n");
            sb.append("public final class ").append(className).append(" {\n");
            for (int i = 0, n = locDecls.size(); i < n; i++) {
                SourceLocation loc = locDecls.get(i);
                String name = "LOC_" + i;
                sb.append("    private static final SourceLocation ").append(name)
                        .append(" = SourceLocation.fromLine(\"").append(escape(loc.getPath()))
                        .append("\", ").append(loc.getLine()).append(", ").append(loc.getCol())
                        .append(");\n");
            }
            if (!locDecls.isEmpty())
                sb.append('\n');
            sb.append("    public static Object ").append(EvalMethodConvention.ENTRY_METHOD_NAME)
                    .append("(").append(IEvalScope.class.getSimpleName()).append(' ')
                    .append(EvalMethodConvention.SCOPE_PARAM).append(") {\n");
            for (String text : lines)
                sb.append("    ").append(text).append('\n');
            sb.append("    }\n");
            sb.append("}\n");
            return sb.toString();
        }
    }
}
