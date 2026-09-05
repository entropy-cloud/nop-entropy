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
import io.nop.core.lang.eval.IEvalOutput;
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
import io.nop.xlang.exec.CompareOpExecutable;
import io.nop.xlang.exec.ConcatExecutable;
import io.nop.xlang.exec.ContinueExecutable;
import io.nop.xlang.exec.ConvertExecutable;
import io.nop.xlang.exec.ConvertWithDefaultExecutable;
import io.nop.xlang.exec.DebugExecutable;
import io.nop.xlang.exec.DebugIdentifierExecutable;
import io.nop.xlang.exec.DeleteAttrExecutable;
import io.nop.xlang.exec.DeletePropertyExecutable;
import io.nop.xlang.exec.DeleteScopeVarExecutable;
import io.nop.xlang.exec.DivideExecutable;
import io.nop.xlang.exec.DoWhileExecutable;
import io.nop.xlang.exec.EnhanceRefSlotExecutable;
import io.nop.xlang.exec.EqExecutable;
import io.nop.xlang.exec.EqNullExecutable;
import io.nop.xlang.exec.EscapeOutputExecutable;
import io.nop.xlang.exec.ExecutableFunction;
import io.nop.xlang.exec.ForExecutable;
import io.nop.xlang.exec.ForInExecutable;
import io.nop.xlang.exec.ForOfExecutable;
import io.nop.xlang.exec.FunctionExecutable;
import io.nop.xlang.exec.FunctionalAdapterExecutable;
import io.nop.xlang.exec.GeExecutable;
import io.nop.xlang.exec.GenNodeAttrExecutable;
import io.nop.xlang.exec.GenNodeExecutable;
import io.nop.xlang.exec.GenXJsonExecutable;
import io.nop.xlang.exec.GetAttrExecutable;
import io.nop.xlang.exec.GetPropertyExecutable;
import io.nop.xlang.exec.GetterGetPropertyExecutable;
import io.nop.xlang.exec.GlobalVarExecutable;
import io.nop.xlang.exec.GtExecutable;
import io.nop.xlang.exec.GuardNotEmptyExecutable;
import io.nop.xlang.exec.GuardNotNullExecutable;
import io.nop.xlang.exec.ISeqExecutable;
import io.nop.xlang.exec.IfExecutable;
import io.nop.xlang.exec.InitRefSlotExecutable;
import io.nop.xlang.exec.InstanceOfExecutable;
import io.nop.xlang.exec.LazyCompiledExecutableFunction;
import io.nop.xlang.exec.LeExecutable;
import io.nop.xlang.exec.ListItemExecutable;
import io.nop.xlang.exec.LiteralExecutable;
import io.nop.xlang.exec.LocationFunction;
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
import io.nop.xlang.exec.OutputTextExecutable;
import io.nop.xlang.exec.OutputValueExecutable;
import io.nop.xlang.exec.OutputXmlAttrExecutable;
import io.nop.xlang.exec.OutputXmlExtAttrsExecutable;
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
import io.nop.xlang.exec.ReturnExecutable;
import io.nop.xlang.exec.ReturnNullExecutable;
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
import io.nop.xlang.exec.StrictEqExecutable;
import io.nop.xlang.exec.StrictEqNullExecutable;
import io.nop.xlang.exec.StrictNeExecutable;
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
 * <p>支持集（I2 表达式子集 + I3 覆盖 A + I4 覆盖 B = 全量非排除具体类，
 * 见 {@code ExecNodeBaseline.javaTargetSet()}）：字面量 / slot 标识符 / 算术 / 逻辑 / 比较 /
 * 简单方法调用（宿主方法反射分派族）/ 作用域链 / 类型操作 / 对象集合 / 绑定守卫 / slot 写 /
 * 残余算子 + 控制流（Java 控制流直译，ExitMode 以生成方法边界为传播边界）+
 * 函数闭包（载荷下降为生成私有方法 + {@code XLangSemantics.generatedFunction} 适配）+
 * 输出与节点生成（{@code $out} 隐参 API 调用序列 + 换缓冲共享 helper 回调 + ExitMode cell 通道）。
 *
 * <p>硬保证：
 * <ul>
 * <li>fail-fast——树中出现子集外节点/形态即转译失败（报节点类名 + SourceLocation），
 * 禁止部分生成与"剩余解释"混合产物；运行期对象载荷（如 {@code FunctionalAdapterExecutable}
 * 的 IEvalFunction）与不可解析载荷（如 {@code LazyCompiledExecutableFunction} 空载荷）显式
 * fail-fast，不静默降级；</li>
 * <li>SourceLocation 保真——可抛错点内嵌静态 SourceLocation 常量（转译期从节点固化），
 * 抛 {@code NopException} 时携带对应常量；</li>
 * <li>语义一致——语义敏感操作统一调用 {@link XLangSemantics} 共享 helper，
 * 禁止为生成代码重写语义等价实现；</li>
 * <li>EvalMethod 约定——生成入口方法 static + 首参 {@code IEvalScope $scope}；含输出语义
 * 单元按 I2 定稿契约追加第二隐参 {@code IEvalOutput $out}（{@link EvalMethodConvention#OUT_PARAM}）；</li>
 * <li>ExitMode 边界不变式——ExitMode 不跨函数/闭包边界传播：函数体下降为私有方法（方法边界
 * 原生对应）；换缓冲生成体以显式 ExitMode cell 通道承载 pending 跳转（调用点按
 * RETURN/BREAK/CONTINUE 分派，与解释器逐语句停走语义一一对应）。</li>
 * </ul>
 */
public final class ExecToJavaTranslator {

    /** 控制跳转节点发射后的"值引用"标记：控制流已跳转，值不可用（return 或经 ExitMode cell 返回）。 */
    private static final String JUMP_RETURN = "$$jump-return";
    /** 跳转经 ExitMode cell 通道承载（换缓冲生成体内、无原生循环边界可跨）：控制流已从生成体返回。 */
    private static final String JUMP_EXIT_CELL = "$$jump-cell";
    /** 原生循环内 break/continue：跳出本块后控制流在循环外恢复（后续语句仍按停走语义跳过）。 */
    private static final String JUMP_BREAK_NATIVE = "$$jump-break";

    public GeneratedJavaSource translate(String resourcePath, IExecutableExpression tree) {
        Guard.notEmpty(resourcePath, "resourcePath");
        Guard.notNull(tree, "tree");
        GenContext ctx = new GenContext(resourcePath);
        if (tree instanceof CallFuncExecutable) {
            translateProgramEntry(ctx, (CallFuncExecutable) tree);
        } else {
            translatePureExpression(ctx, tree);
        }
        String className = EvalMethodConvention.generatedClassName(resourcePath);
        return new GeneratedJavaSource(resourcePath,
                EvalMethodConvention.GENERATED_PACKAGE + '.' + className, ctx.buildClass(className));
    }

    /**
     * xlib 每标签单元转译（I11，Phase 1 §7 裁定的生产生成形态）：一个标签一个生成类，入口
     * {@code public static Object execute(IEvalScope $scope, Object[] $args, IEvalOutput $out)}
     * （{@link EvalMethodConvention#ARGS_PARAM} 形态——实参组 + 输出缓冲经绑定体注入），函数体下降
     * 为 {@code private static Object $fn_k(IEvalScope, Object[], Object[], IEvalOutput)}（slot 绑定 +
     * 缺省实参 + 函数体，emitFunctionMethod 同款前导；$out 形参使输出族节点在标签体内合法——
     * 解释器标签体经 EvalRuntime.out 输出的直译对应）。纯增量 API：既有 {@link #translate}
     * 与全部既有测试零改动。指纹口径 = {@code ExecutableTreeFingerprints.fingerprint(fn)}
     * （根级函数分支）——任务侧先指纹后转译（转译会 force-compile 惰性载荷）。
     */
    public GeneratedJavaSource translateTagUnit(String entryKey, ExecutableFunction fn) {
        Guard.notEmpty(entryKey, "entryKey");
        Guard.notNull(fn, "fn");
        if (fn.getBody() == null)
            throw unsupported(fn, "tag function without body");
        GenContext ctx = new GenContext(entryKey);
        ctx.entryTagForm = true;
        String methodName = emitFunctionMethod(ctx, fn, fn.getSlotNames(), fn.getBody(),
                fn.getArgCount(), fn.getDemandArgCount(), fn.getDefaultArgValues(), null, true);
        ctx.beginEntryMethod(new String[0]);
        ctx.line("return " + methodName + "($scope, $args, null, $out);");
        ctx.endMethod();
        String className = EvalMethodConvention.generatedClassName(entryKey);
        return new GeneratedJavaSource(entryKey,
                EvalMethodConvention.GENERATED_PACKAGE + '.' + className, ctx.buildClass(className));
    }

    // ------------------------------------------------------------------
    // 支持集可编程枚举（矩阵注册证据之一；分派为 instanceof 链，本集合与分派分支一一对应，
    // 一致性由矩阵测试逐类真实转译验证——非清单自证）
    // ------------------------------------------------------------------

    /**
     * 转译器已注册的具体节点类（I2 子集 + 覆盖 A 五族 + 并入残余 + 覆盖 B 三族
     * = {@code ExecNodeBaseline.javaTargetSet()}，120 类）。
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
                DeleteAttrExecutable.class, DeletePropertyExecutable.class,
                DeleteScopeVarExecutable.class,
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
        // 覆盖 B：函数/闭包族（含裁定转译并入的边缘类 LocationFunction）
        Collections.addAll(set, VarFunctionExecutable.class, VarExecutableFunction.class,
                LazyCompiledExecutableFunction.class, FunctionalAdapterExecutable.class,
                CallFuncWithClosureExecutable.class, BuildFuncRefExecutable.class,
                BuildClosureBodyExecutable.class, LocationFunction.class);
        // 覆盖 B：控制流族
        Collections.addAll(set, IfExecutable.class, SwitchExecutable.class,
                ForExecutable.class, ForInExecutable.class, ForOfExecutable.class,
                WhileExecutable.class, DoWhileExecutable.class,
                BreakExecutable.class, ContinueExecutable.class, ReturnExecutable.class,
                TryExecutable.class, ThrowErrorCodeExecutable.class, ThrowExceptionExecutable.class);
        // 覆盖 B：输出/节点生成族（GenNodeAttrExecutable 为 GenNode 的属性描述符，经宿主节点载体覆盖）
        Collections.addAll(set, OutputTextExecutable.class, OutputValueExecutable.class,
                OutputXmlAttrExecutable.class, OutputXmlExtAttrsExecutable.class,
                GenNodeExecutable.class, GenNodeAttrExecutable.class,
                GenXJsonExecutable.class, CollectJsonExecutable.class, CollectNodeExecutable.class,
                CollectSqlExecutable.class, CollectTextExecutable.class, EscapeOutputExecutable.class);
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
     * 程序入口模式：树根为 CallFuncExecutable 程序入口包装（编译前端对 script/模板单元的固有产物，
     * slotNames 承载帧布局），入口包装翻译为生成方法本体（与其 EvalMethod 生成方法约定同构）。
     * 非根 CallFunc = 局部函数调用，经 {@link #genCallFunc} 转译（I4 裁定并入）。
     */
    private void translateProgramEntry(GenContext ctx, CallFuncExecutable entry) {
        IExecutableExpression[] argExprs = entry.getArgExprs();
        if (argExprs != null && argExprs.length > 0)
            throw unsupported(entry, "program entry with declared arguments");
        String[] slotNames = entry.getSlotNames() == null ? new String[0] : entry.getSlotNames();
        ctx.beginEntryMethod(slotNames);
        for (int i = 0; i < slotNames.length; i++) {
            ctx.line("Object $v" + i + " = null; // slot " + i + ": " + slotNames[i]);
        }
        emitRootValue(ctx, entry.getBodyExpr());
        ctx.endMethod();
    }

    /**
     * 纯表达式模式：树根为任意子集表达式（无入口帧），单表达式返回。
     */
    private void translatePureExpression(GenContext ctx, IExecutableExpression tree) {
        ctx.beginEntryMethod(new String[0]);
        emitRootValue(ctx, tree);
        ctx.endMethod();
    }

    private void emitRootValue(GenContext ctx, IExecutableExpression body) {
        if (body == null) {
            ctx.line("return null;");
            return;
        }
        boolean prev = ctx.jumpCtx;
        ctx.jumpCtx = true;
        try {
            if (body instanceof ISeqExecutable) {
                IExecutableExpression[] exprs = ((ISeqExecutable) body).getExprs();
                boolean block = ((ISeqExecutable) body).isBlockStatement();
                if (exprs.length == 0) {
                    ctx.line("return null;");
                    return;
                }
                int last = block ? exprs.length : exprs.length - 1;
                for (int i = 0; i < last; i++) {
                    if (!genStatementChild(ctx, exprs[i]))
                        return; // 语句无条件跳转（return 已发射）：后续按停走语义跳过
                }
                if (block) {
                    ctx.line("return null;");
                } else {
                    String ref = genBodyValue(ctx, exprs[exprs.length - 1]);
                    if (!isJump(ref))
                        ctx.line("return " + ref + ";");
                }
            } else {
                String ref = genExpr(ctx, body);
                if (!isJump(ref))
                    ctx.line("return " + ref + ";");
            }
        } finally {
            ctx.jumpCtx = prev;
        }
    }

    // ------------------------------------------------------------------
    // 语句与表达式发射
    // ------------------------------------------------------------------

    private void genStatement(GenContext ctx, IExecutableExpression node) {
        if (node instanceof ISeqExecutable) {
            for (IExecutableExpression child : ((ISeqExecutable) node).getExprs()) {
                if (!genStatementChild(ctx, child))
                    break;
            }
            return;
        }
        genStatementChild(ctx, node);
    }

    /**
     * 语句位置子节点发射；返回 false = 控制流已无条件跳转，后续语句按解释器 exitMode 停走语义
     * 跳过（live {@code SeqExecutable.execute} 每 child 后检查 exitMode，一一对应）。
     */
    private boolean genStatementChild(GenContext ctx, IExecutableExpression node) {
        if (node instanceof ReturnNullExecutable) {
            genStatement(ctx, ((ReturnNullExecutable) node).getExecutable());
            return true;
        }
        boolean prev = ctx.jumpCtx;
        ctx.jumpCtx = true;
        try {
            String ref = genExpr(ctx, node);
            if (isJump(ref))
                return false;
            if (isInvocationRef(ref))
                ctx.line(ref + ";");
            return true;
        } finally {
            ctx.jumpCtx = prev;
        }
    }

    /**
     * 体位置（分支体/情形体/函数体根部/换缓冲生成体根部）求值：允许控制跳转节点（jump 语境）。
     */
    private String genBodyValue(GenContext ctx, IExecutableExpression node) {
        boolean prev = ctx.jumpCtx;
        ctx.jumpCtx = true;
        try {
            if (node instanceof ISeqExecutable)
                return genSeqAsExpr(ctx, (ISeqExecutable) node);
            return genExpr(ctx, node);
        } finally {
            ctx.jumpCtx = prev;
        }
    }

    private boolean isInvocationRef(String ref) {
        return ref.startsWith("XLangSemantics.") || ref.startsWith("io.nop.");
    }

    private static boolean isJump(String ref) {
        return ref == JUMP_RETURN || ref == JUMP_EXIT_CELL || ref == JUMP_BREAK_NATIVE;
    }

    /** 终端跳转标记（return / ExitMode cell 返回）：后续语句不可达。 */
    private static boolean isTerminalJumpRef(String ref) {
        return ref == JUMP_RETURN || ref == JUMP_EXIT_CELL;
    }

    private String genExpr(GenContext ctx, IExecutableExpression node) {
        // ---- 控制跳转：仅语句/体位置合法；表达式位置 = 前端不可能形态，显式 fail-fast ----
        if (node instanceof BreakExecutable || node instanceof ContinueExecutable
                || node instanceof ReturnExecutable) {
            if (!ctx.jumpCtx)
                throw unsupported(node, "jump statement in expression position");
            return genJump(ctx, node);
        }
        boolean prev = ctx.jumpCtx;
        ctx.jumpCtx = false;
        try {
            return genValueExpr(ctx, node);
        } finally {
            ctx.jumpCtx = prev;
        }
    }

    private String genValueExpr(GenContext ctx, IExecutableExpression node) {
        // ---- 字面量 / null（ExecutableFunction 载荷 = 函数值，下降为生成私有方法） ----
        if (node instanceof LiteralExecutable) {
            Object value = ((LiteralExecutable) node).getValue();
            if (value instanceof ExecutableFunction)
                return genFunctionValue(ctx, node, (ExecutableFunction) value, null, null);
            return literal(ctx, (LiteralExecutable) node);
        }
        if (node instanceof NullExecutable) {
            return "null";
        }
        if (node instanceof LocationFunction) {
            // 边缘类裁定转译并入：返回调用点 SourceLocation 常量直译
            return ctx.locRef(node);
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
        if (node instanceof DeletePropertyExecutable) {
            DeletePropertyExecutable del = (DeletePropertyExecutable) node;
            return "XLangSemantics.deleteProperty(" + ctx.locRef(node) + ", " + displayOf(node) + ", \""
                    + escape(del.getPropName()) + "\", " + genExpr(ctx, del.getObjExpr()) + ", $scope)";
        }
        if (node instanceof DeleteAttrExecutable) {
            DeleteAttrExecutable del = (DeleteAttrExecutable) node;
            return "XLangSemantics.deleteAttr(" + ctx.locRef(node) + ", " + displayOf(node) + ", "
                    + displayOf(del.getAttrExpr()) + ", " + genExpr(ctx, del.getObjExpr()) + ", "
                    + genExpr(ctx, del.getAttrExpr()) + ", $scope)";
        }
        if (node instanceof DeleteScopeVarExecutable) {
            DeleteScopeVarExecutable del = (DeleteScopeVarExecutable) node;
            String nameRef = del.getVarName() != null ? "\"" + escape(del.getVarName()) + "\"" : "null";
            String attrRef = del.getAttrExpr() != null ? genExpr(ctx, del.getAttrExpr()) : "null";
            return "XLangSemantics.deleteScopeVar(" + ctx.locRef(node) + ", $scope, " + nameRef + ", " + attrRef + ")";
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

        // ---- 覆盖 B：控制流族（Java 控制流直译；ExitMode 边界 = 生成方法边界） ----
        if (node instanceof IfExecutable) {
            return genIf(ctx, (IfExecutable) node);
        }
        if (node instanceof SwitchExecutable) {
            return genSwitch(ctx, (SwitchExecutable) node);
        }
        if (node instanceof ForExecutable) {
            return genFor(ctx, (ForExecutable) node);
        }
        if (node instanceof ForInExecutable) {
            return genForIn(ctx, (ForInExecutable) node);
        }
        if (node instanceof ForOfExecutable) {
            return genForOf(ctx, (ForOfExecutable) node);
        }
        if (node instanceof WhileExecutable) {
            return genWhile(ctx, (WhileExecutable) node);
        }
        if (node instanceof DoWhileExecutable) {
            return genDoWhile(ctx, (DoWhileExecutable) node);
        }
        if (node instanceof TryExecutable) {
            return genTry(ctx, (TryExecutable) node);
        }
        if (node instanceof ThrowErrorCodeExecutable) {
            return genThrowErrorCode(ctx, (ThrowErrorCodeExecutable) node);
        }
        if (node instanceof ThrowExceptionExecutable) {
            ThrowExceptionExecutable thr = (ThrowExceptionExecutable) node;
            String v = hoist(ctx, genExpr(ctx, thr.getExpr()));
            ctx.line("XLangSemantics.throwException(" + ctx.locRef(node) + ", " + displayOf(node)
                    + ", " + v + ");");
            return "null";
        }

        // ---- 覆盖 B：函数/闭包族（载荷下降为生成私有方法 + generatedFunction 适配） ----
        if (node instanceof VarFunctionExecutable || node instanceof VarExecutableFunction) {
            return genVarFunctionCall(ctx, node);
        }
        if (node instanceof CallFuncExecutable) {
            // 非根 CallFunc = 局部函数调用（I4 裁定并入转译；根位置在 translate 入口分派）
            return genCallFunc(ctx, (CallFuncExecutable) node, null);
        }
        if (node instanceof CallFuncWithClosureExecutable) {
            return genCallFunc((CallFuncWithClosureExecutable) node, ctx);
        }
        if (node instanceof BuildFuncRefExecutable) {
            BuildFuncRefExecutable ref = (BuildFuncRefExecutable) node;
            return genFunctionValue(ctx, node, ref.getFunc(), ref.getSourceSlots(), ref.getTargetSlots());
        }
        if (node instanceof BuildClosureBodyExecutable) {
            return genBuildClosureBody(ctx, (BuildClosureBodyExecutable) node);
        }
        if (node instanceof LazyCompiledExecutableFunction) {
            return genLazyCompiledCall(ctx, (LazyCompiledExecutableFunction) node);
        }
        if (node instanceof FunctionalAdapterExecutable) {
            // 运行期 IEvalFunction 载荷：生成源码无自包含表示（I11 构建 JVM ≠ 运行 JVM），显式 fail-fast
            throw unsupported(node, "runtime IEvalFunction payload cannot be embedded in generated source");
        }

        // ---- 覆盖 B：输出/节点生成族（$out 隐参 API 调用序列 + 换缓冲共享 helper 回调） ----
        if (node instanceof OutputTextExecutable) {
            requireOut(ctx, node);
            OutputTextExecutable out = (OutputTextExecutable) node;
            ctx.line("$out.text(" + ctx.locRef(node) + ", \"" + escape(out.getText()) + "\");");
            return "null";
        }
        if (node instanceof OutputValueExecutable) {
            requireOut(ctx, node);
            OutputValueExecutable out = (OutputValueExecutable) node;
            String v = genExpr(ctx, out.getValueExpr());
            ctx.line("$out.value(" + ctx.locRef(node) + ", " + v + ");");
            return "null";
        }
        if (node instanceof OutputXmlAttrExecutable) {
            requireOut(ctx, node);
            OutputXmlAttrExecutable out = (OutputXmlAttrExecutable) node;
            String v = hoist(ctx, genExpr(ctx, out.getValueExpr()));
            ctx.line("XLangSemantics.outputXmlAttr(" + ctx.locRef(node) + ", $out, \""
                    + escape(out.getName()) + "\", " + v + ");");
            return "null";
        }
        if (node instanceof OutputXmlExtAttrsExecutable) {
            requireOut(ctx, node);
            OutputXmlExtAttrsExecutable out = (OutputXmlExtAttrsExecutable) node;
            String v = hoist(ctx, genExpr(ctx, out.getAttrsExpr()));
            ctx.line("XLangSemantics.outputXmlExtAttrs(" + ctx.locRef(node) + ", " + displayOf(node)
                    + ", $out, " + stringSetRef(out.getExcludeNames()) + ", " + v + ");");
            return "null";
        }
        if (node instanceof EscapeOutputExecutable) {
            requireOut(ctx, node);
            EscapeOutputExecutable out = (EscapeOutputExecutable) node;
            String v = hoist(ctx, genExpr(ctx, out.getValueExpr()));
            ctx.line("XLangSemantics.escapeOutput(" + ctx.locRef(node) + ", $out, io.nop.xlang.ast.XLangEscapeMode."
                    + out.getEscapeMode().name() + ", " + v + ");");
            return "null";
        }
        if (node instanceof GenXJsonExecutable) {
            requireOut(ctx, node);
            return genSwapCall(ctx, ((GenXJsonExecutable) node).getExecutable(), "genXjson", null);
        }
        if (node instanceof CollectTextExecutable) {
            requireOut(ctx, node);
            return genSwapCall(ctx, ((CollectTextExecutable) node).getBodyExpr(), "collectText", null);
        }
        if (node instanceof CollectJsonExecutable) {
            requireOut(ctx, node);
            return genSwapCall(ctx, ((CollectJsonExecutable) node).getBodyExpr(), "collectJson", null);
        }
        if (node instanceof CollectNodeExecutable) {
            requireOut(ctx, node);
            CollectNodeExecutable collect = (CollectNodeExecutable) node;
            return genSwapCall(ctx, collect.getBodyExpr(), "collectNode",
                    ctx.locRef(node) + ", " + collect.isSingleNode());
        }
        if (node instanceof CollectSqlExecutable) {
            requireOut(ctx, node);
            return genSwapCall(ctx, ((CollectSqlExecutable) node).getBodyExpr(), "collectSql", null);
        }
        if (node instanceof GenNodeExecutable) {
            requireOut(ctx, node);
            return genGenNode(ctx, (GenNodeExecutable) node);
        }

        // 子集外节点（fail-fast）
        throw unsupported(node, null);
    }

    // ------------------------------------------------------------------
    // 覆盖 B：控制流族生成方法（循环以 "$loop_k: while(true){...}" + 迭代块 "$iter_k:{ body }"
    // 承载——break → break $loop_k（穿透 switch 翻译的顺序块结构，与解释器 switch 不消费
    // exitMode 一致）；continue → break $iter_k（落到 update/test 位置，与解释器 continue
    // 后执行 update 的次序一致）；test 前置 / update 尾部与解释器逐迭代次序一一对应
    // ------------------------------------------------------------------

    private String genIf(GenContext ctx, IfExecutable node) {
        String test = hoist(ctx, genExpr(ctx, node.getTest()));
        String temp = ctx.temp();
        ctx.line("Object " + temp + ";");
        ctx.line("if (XLangSemantics.truthy(" + test + ")) {");
        ctx.indent();
        String cons = genBodyValue(ctx, node.getConsequent());
        boolean consJump = isJump(cons);
        if (!consJump)
            ctx.line(temp + " = " + cons + ";");
        ctx.unindent();
        ctx.line("} else {");
        ctx.indent();
        boolean altJump = false;
        if (node.getAlternate() != null) {
            String alt = genBodyValue(ctx, node.getAlternate());
            altJump = isJump(alt);
            if (!altJump)
                ctx.line(temp + " = " + alt + ";");
        } else {
            ctx.line(temp + " = null;");
        }
        ctx.unindent();
        ctx.line("}");
        if (consJump && altJump)
            return JUMP_RETURN; // 两分支均跳转：if 之后控制流不可达（后续语句按停走语义跳过）
        return temp;
    }

    /**
     * Switch 直译为顺序 case 块 + 标签块（逐 test 求值 + fallthrough 语义与解释器迭代算法
     * 一一对应；非 fallthrough 命中即 break 标签跳出 case 链；consequent 内跳转节点为
     * 原生/标签跳转，与解释器 switch 不消费 exitMode 一致）。
     */
    private String genSwitch(GenContext ctx, SwitchExecutable node) {
        String disc = hoist(ctx, genExpr(ctx, node.getDiscriminant()));
        String label = ctx.newLabel("$sw");
        String ret = ctx.temp();
        ctx.line("Object " + ret + " = null;");
        ctx.line(label + ": {");
        ctx.indent();
        ctx.current().openSwitch(label);
        IExecutableExpression[] tests = node.getTests();
        IExecutableExpression[] consequences = node.getConsequences();
        boolean[] fallthroughs = node.getFallthroughs();
        for (int i = 0; i < tests.length; i++) {
            String testValue = hoist(ctx, genExpr(ctx, tests[i]));
            ctx.line("if (java.util.Objects.equals(" + disc + ", " + testValue + ")) {");
            ctx.indent();
            String value = genBodyValue(ctx, consequences[i]);
            if (!isJump(value)) {
                ctx.line(ret + " = " + value + ";");
                if (!fallthroughs[i])
                    ctx.line("break " + label + ";");
            }
            ctx.unindent();
            ctx.line("}");
        }
        if (node.getDefaultCase() != null) {
            String value = genBodyValue(ctx, node.getDefaultCase());
            if (!isJump(value))
                ctx.line(ret + " = " + value + ";");
        }
        ctx.unindent();
        ctx.line("}");
        ctx.current().closeSwitch();
        return node.isAsExpr() ? ret : "null";
    }

    private String genFor(GenContext ctx, ForExecutable node) {
        if (node.getInitExpr() != null)
            genStatement(ctx, node.getInitExpr());
        ctx.openLoop();
        if (node.getTestExpr() != null)
            emitLoopTest(ctx, node.getTestExpr());
        genStatement(ctx, node.getBodyExpr());
        ctx.closeIterBlock();
        if (node.getUpdateExpr() != null)
            genStatement(ctx, node.getUpdateExpr());
        ctx.closeLoop();
        return "null";
    }

    private String genWhile(GenContext ctx, WhileExecutable node) {
        ctx.openLoop();
        emitLoopTest(ctx, node.getTestExpr());
        genStatement(ctx, node.getBodyExpr());
        ctx.closeIterBlock();
        ctx.closeLoop();
        return "null";
    }

    private String genDoWhile(GenContext ctx, DoWhileExecutable node) {
        ctx.openLoop();
        genStatement(ctx, node.getBodyExpr());
        ctx.closeIterBlock();
        if (node.getTestExpr() != null)
            emitLoopTest(ctx, node.getTestExpr());
        ctx.closeLoop();
        return "null";
    }

    private String genForIn(GenContext ctx, ForInExecutable node) {
        checkSlot(ctx, node, node.getVarSlot());
        String items = hoist(ctx, genExpr(ctx, node.getItemsExpr()));
        String map = ctx.temp();
        ctx.line("java.util.Map " + map + " = XLangSemantics.asForInMap(" + ctx.locRef(node) + ", "
                + displayOf(node) + ", " + items + ");");
        ctx.line("if (" + map + " != null) {");
        ctx.indent();
        // 迭代器在循环外创建一次（循环内每次新建会无限循环）
        String it = ctx.temp();
        ctx.line("java.util.Iterator " + it + " = " + map + ".keySet().iterator();");
        ctx.openLoop();
        String key = ctx.temp();
        ctx.line("if (!" + it + ".hasNext()) {");
        ctx.indent();
        ctx.line("break " + ctx.current().currentLoopLabel() + ";");
        ctx.unindent();
        ctx.line("}");
        ctx.line("Object " + key + " = " + it + ".next();");
        ctx.line("$v" + node.getVarSlot() + " = " + key + ";");
        genStatement(ctx, node.getBodyExpr());
        ctx.closeIterBlock();
        ctx.closeLoop();
        ctx.unindent();
        ctx.line("}");
        return "null";
    }

    private String genForOf(GenContext ctx, ForOfExecutable node) {
        checkSlot(ctx, node, node.getVarSlot());
        String items = hoist(ctx, genExpr(ctx, node.getItemsExpr()));
        String iter = ctx.temp();
        ctx.line("java.util.Iterator " + iter + " = " + items + " == null ? null : XLangSemantics.forOfIterator("
                + ctx.locRef(node) + ", " + displayOf(node) + ", " + displayOf(node.getItemsExpr()) + ", "
                + items + ");");
        ctx.line("if (" + iter + " != null) {");
        ctx.indent();
        String idx = null;
        if (node.getIndexSlot() >= 0) {
            checkSlot(ctx, node, node.getIndexSlot());
            idx = ctx.temp();
            ctx.line("int " + idx + " = 0;");
        }
        ctx.openLoop();
        String var = ctx.temp();
        ctx.line("if (!" + iter + ".hasNext()) {");
        ctx.indent();
        ctx.line("break " + ctx.current().currentLoopLabel() + ";");
        ctx.unindent();
        ctx.line("}");
        ctx.line("Object " + var + " = " + iter + ".next();");
        ctx.line("$v" + node.getVarSlot() + " = "
                + (node.isUseRef() ? "new io.nop.core.lang.eval.EvalReference(" + var + ")" : var) + ";");
        if (node.getIndexSlot() >= 0)
            ctx.line("$v" + node.getIndexSlot() + " = Integer.valueOf(" + idx + ");");
        genStatement(ctx, node.getBodyExpr());
        ctx.closeIterBlock();
        if (node.getIndexSlot() >= 0)
            ctx.line(idx + "++;");
        ctx.closeLoop();
        ctx.unindent();
        ctx.line("}");
        return "null";
    }

    private void emitLoopTest(GenContext ctx, IExecutableExpression testExpr) {
        String test = hoist(ctx, genExpr(ctx, testExpr));
        ctx.line("if (!XLangSemantics.truthy(" + test + ")) {");
        ctx.indent();
        // 命名 break 跳出 $loop_k（裸 break 只能跳出 $iter_k 语句块，会退化成死循环）
        ctx.line("break " + ctx.current().currentLoopLabel() + ";");
        ctx.unindent();
        ctx.line("}");
    }

    /**
     * Try 直译：catch Exception → slot 承载 → 执行 catch 体后吞掉异常继续（JS 语义，与解释器
     * TryExecutable 一致；catch 体内的 return/break/continue 经 jump 协议处置）；finally 原生
     * （unwind 路径执行与解释器一致）。
     */
    private String genTry(GenContext ctx, TryExecutable node) {
        String ret = ctx.temp();
        ctx.line("Object " + ret + " = null;");
        ctx.line("try {");
        ctx.indent();
        String body = genBodyValue(ctx, node.getBodyExpr());
        if (!isJump(body))
            ctx.line(ret + " = " + body + ";");
        ctx.unindent();
        String exVar = null;
        if (node.getCatchExpr() != null) {
            exVar = ctx.temp();
            ctx.line("} catch (java.lang.Exception " + exVar + ") {");
            ctx.indent();
            if (node.getExceptionSlot() >= 0) {
                checkSlot(ctx, node, node.getExceptionSlot());
                ctx.line("$v" + node.getExceptionSlot() + " = " + exVar + ";");
            }
            genStatement(ctx, node.getCatchExpr());
            ctx.unindent();
        }
        if (node.getFinallyExpr() != null) {
            ctx.line("} finally {");
            ctx.indent();
            genStatement(ctx, node.getFinallyExpr());
            ctx.unindent();
        }
        ctx.line("}");
        return ret;
    }

    /** ThrowErrorCode 直译：error 求值 →（非异常载荷时）params 求值 → 共享 helper throwErrorCode。 */
    private String genThrowErrorCode(GenContext ctx, ThrowErrorCodeExecutable node) {
        String error = hoist(ctx, genExpr(ctx, node.getErrorExpr()));
        String params = ctx.temp();
        ctx.line("Object " + params + " = null;");
        ctx.line("if (!(" + error + " instanceof io.nop.api.core.exceptions.NopException)"
                + " && !(" + error + " instanceof java.lang.Throwable)) {");
        ctx.indent();
        String paramsValue = node.getParamsExpr() == null ? "null" : hoist(ctx, genExpr(ctx, node.getParamsExpr()));
        ctx.line(params + " = " + paramsValue + ";");
        ctx.unindent();
        ctx.line("}");
        ctx.line("XLangSemantics.throwErrorCode(" + ctx.locRef(node) + ", null, " + displayOf(node)
                + ", " + error + ", " + params + ");");
        return "null";
    }

    // ------------------------------------------------------------------
    // 覆盖 B：函数/闭包族生成方法（I4 Phase 1 §5 载荷处置裁定：下降为生成私有方法 + 适配包装）
    // ------------------------------------------------------------------

    /**
     * 函数值创建（LiteralExecutable(ExecutableFunction) 载荷 / BuildFuncRefExecutable）：
     * 函数体下降为 {@code private static Object $fn_k(IEvalScope, Object[] args, Object[] captured)}，
     * 值 = {@code XLangSemantics.generatedFunction} 适配包装；captured = 创建点读取当前帧 slot 值
     * （被捕获可变 slot 持 EvalReference 对象 → 按引用传递 = 共享 cell，与 bindClosureVars 快照一致）。
     */
    private String genFunctionValue(GenContext ctx, IExecutableExpression node, ExecutableFunction fn,
                                    int[] sourceSlots, int[] targetSlots) {
        if (fn.getBody() == null)
            throw unsupported(node, "function payload without body");
        String methodName = emitFunctionMethod(ctx, node, fn.getSlotNames(), fn.getBody(),
                fn.getArgCount(), fn.getDemandArgCount(), fn.getDefaultArgValues(), targetSlots);
        String captured = capturedArrayRef(ctx, node, sourceSlots);
        return "XLangSemantics.generatedFunction(" + fn.getArgCount() + ", " + fn.getDemandArgCount()
                + ", ($s, $a, $c) -> " + methodName + "($s, $a, $c), " + captured + ")";
    }

    /**
     * 非根 CallFunc（局部函数调用，I4 裁定并入）：被调函数体下降为私有方法（slotNames 帧由节点
     * 自携带，前端已内联缺省实参），调用点求值 argExprs（调用者帧）→ 直调 + 异常包装
     * （wrapCallFuncException，与解释器 CallFunc 族 catch 语义一致）；ExitMode 边界 = 私有方法边界（原生）。
     */
    private String genCallFunc(GenContext ctx, CallFuncExecutable call, Object unused) {
        IExecutableExpression[] argExprs = call.getArgExprs();
        String methodName = emitFunctionMethod(ctx, call, call.getSlotNames(), call.getBodyExpr(),
                argExprs.length, argExprs.length, null, null);
        return emitDirectCallSite(ctx, call, methodName, argExprs, null);
    }

    /**
     * CallFuncWithClosure：非根 CallFunc 形态 + 闭包捕获线程化——调用点 captured 数组 = 当前帧
     * sourceSlots 槽当前值（求值次序 args → captured，与解释器一致）；私有方法内
     * $v&lt;targetSlot[i]&gt; = $captured[i]（对应 BindVarExecutable 语义，捕获写入后于实参绑定）。
     */
    private String genCallFunc(CallFuncWithClosureExecutable call, GenContext ctx) {
        IExecutableExpression[] argExprs = call.getArgExprs();
        String methodName = emitFunctionMethod(ctx, call, call.getSlotNames(), call.getBodyExpr(),
                argExprs.length, argExprs.length, null, call.getTargetSlots());
        return emitDirectCallSite(ctx, call, methodName, argExprs, call.getSourceSlots());
    }

    /**
     * VarFunction/VarExecutableFunction 函数值调用：共享 helper callVarFunction（null 短路/
     * EXPR_NOT_RETURN_FUNC/ExecutableFunction/GeneratedEvalFunction 分派同一实现）；
     * 求值顺序保真——func 为 null 时实参不求值（与解释器 getFunction → null 检查 → 实参求值一致）。
     */
    private String genVarFunctionCall(GenContext ctx, IExecutableExpression node) {
        IExecutableExpression funcExpr;
        IExecutableExpression[] args;
        boolean optional;
        if (node instanceof VarFunctionExecutable) {
            VarFunctionExecutable var = (VarFunctionExecutable) node;
            funcExpr = var.getFuncExpr();
            args = var.getArgs();
            optional = var.isOptional();
        } else {
            VarExecutableFunction var = (VarExecutableFunction) node;
            funcExpr = var.getFuncExpr();
            args = var.getArgs();
            optional = var.isOptional();
        }
        String func = hoist(ctx, genExpr(ctx, funcExpr));
        String ret = ctx.temp();
        ctx.line("Object " + ret + ";");
        ctx.line("if (" + func + " == null) {");
        ctx.indent();
        ctx.line(ret + " = XLangSemantics.callVarFunction(" + ctx.locRef(node) + ", " + displayOf(node)
                + ", " + optional + ", null, null, $scope);");
        ctx.unindent();
        ctx.line("} else {");
        ctx.indent();
        String argArray = ctx.temp();
        ctx.line("Object[] " + argArray + " = new Object[]{" + genArgs(ctx, args) + "};");
        ctx.line(ret + " = XLangSemantics.callVarFunction(" + ctx.locRef(node) + ", " + displayOf(node)
                + ", " + optional + ", " + func + ", " + argArray + ", $scope);");
        ctx.unindent();
        ctx.line("}");
        return ret;
    }

    /**
     * BuildClosureBodyExecutable（无产生路径，合成树可转译形态）：以当前帧 sourceSlots 快照为
     * captured，生成函数值（体 = 目标槽绑定 captured 后求值 expr），存入 closureSlot。
     */
    private String genBuildClosureBody(GenContext ctx, BuildClosureBodyExecutable node) {
        checkSlot(ctx, node, node.getClosureSlot());
        int[] targetSlots = node.getTargetSlots();
        int frameSize = 0;
        for (int target : targetSlots)
            frameSize = Math.max(frameSize, target + 1);
        String[] frameSlots = new String[frameSize];
        java.util.Arrays.fill(frameSlots, "");
        String methodName = emitFunctionMethod(ctx, node, frameSlots, node.getExpr(),
                0, 0, null, targetSlots);
        String captured = capturedArrayRef(ctx, node, node.getSourceSlots());
        ctx.line("$v" + node.getClosureSlot() + " = XLangSemantics.generatedFunction(0, 0,"
                + " ($s, $a, $c) -> " + methodName + "($s, $a, $c), " + captured + ");");
        return "null";
    }

    /**
     * LazyCompiledExecutableFunction：转译期 force-compile（I4 Phase 1 §5 同类决策伞）——
     * getCompiled() 触发惰性编译得 ExecutableFunction，下降为私有方法直调（无适配器层）；
     * 载荷 null/不可解析 → 显式 fail-fast（矩阵最小实例 null 载荷即此路径的反证）。
     */
    private String genLazyCompiledCall(GenContext ctx, LazyCompiledExecutableFunction node) {
        ExecutableFunction compiled;
        try {
            compiled = node.getCompiled();
        } catch (RuntimeException e) {
            throw unsupported(node, "lazy function payload not resolvable: " + e);
        }
        if (compiled == null || compiled.getBody() == null)
            throw unsupported(node, "lazy function payload not resolvable");
        String methodName = emitFunctionMethod(ctx, node, compiled.getSlotNames(), compiled.getBody(),
                node.getArgExprs().length, node.getArgExprs().length, null, null);
        return emitDirectCallSite(ctx, node, methodName, node.getArgExprs(), null);
    }

    /** 控制跳转节点发射（native 模式 = 原生语句；换缓冲生成体 = ExitMode cell 通道）。 */
    private String genJump(GenContext ctx, IExecutableExpression node) {
        boolean cellMode = ctx.current().outBody;
        if (node instanceof ReturnExecutable) {
            ReturnExecutable ret = (ReturnExecutable) node;
            if (cellMode)
                ctx.line("$exit[0] = io.nop.core.lang.eval.ExitMode.RETURN;");
            if (ret.getExpr() != null) {
                ctx.line("return " + genExpr(ctx, ret.getExpr()) + ";");
            } else {
                ctx.line("return null;");
            }
            return JUMP_RETURN;
        }
        boolean isBreak = node instanceof BreakExecutable;
        if (isBreak && (ctx.current().loopDepth() > 0 || ctx.current().switchDepth() > 0)) {
            // 原生边界内：break跳到最内层的loop或switch（按进入顺序），与解释器"最内层优先"一致
            ctx.line("break " + ctx.current().currentBreakTargetLabel() + ";");
            return JUMP_BREAK_NATIVE;
        }
        if (!isBreak && ctx.current().loopDepth() > 0) {
            // continue只作用于最内层loop（落到 update/test）
            ctx.line("break " + ctx.current().currentIterLabel() + ";");
            return JUMP_BREAK_NATIVE;
        }
        if (cellMode) {
            ctx.line("$exit[0] = io.nop.core.lang.eval.ExitMode."
                    + (isBreak ? "BREAK" : "CONTINUE") + ";");
            ctx.line("return null;");
            return JUMP_EXIT_CELL;
        }
        // 生成方法内循环/switch外 break/continue = 前端拒绝形态（LSA已拒绝），显式 fail-fast
        throw unsupported(node, (isBreak ? "break" : "continue") + " statement outside loop or switch");
    }

    /** 输出族节点要求当前方法有 $out 形参（入口/换缓冲生成体）；$fn_k 函数体内输出 = 显式边界。 */
    private void requireOut(GenContext ctx, IExecutableExpression node) {
        ctx.usesOut = true;
        if (!ctx.current().hasOut)
            throw unsupported(node, "output node inside function body without $out parameter");
    }

    private String stringSetRef(java.util.Collection<String> names) {
        if (names == null || names.isEmpty())
            return "java.util.Collections.emptySet()";
        StringBuilder sb = new StringBuilder("java.util.Collections.unmodifiableSet(new java.util.HashSet(");
        sb.append("java.util.Arrays.asList(");
        boolean first = true;
        for (String name : names) {
            if (!first)
                sb.append(", ");
            first = false;
            sb.append('"').append(escape(name)).append('"');
        }
        sb.append(")))");
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // 覆盖 B：换缓冲族生成方法（共享 helper 回调 + ExitMode cell 通道 + 帧数组线程化）
    // ------------------------------------------------------------------

    /**
     * Collect* / GenXJson 换缓冲调用：生成体下降为
     * {@code private static Object $body_k(IEvalScope, IEvalOutput, ExitMode[], Object[] frame)}，
     * 换缓冲语义走共享 helper（save/set/try-body-finally-restore/collect 与解释器同一实现）；
     * 调用点帧 marshaling（try/finally 回写，异常路径不丢）+ 按 pending exit 分派
     * （RETURN→return 收集值；BREAK/CONTINUE 在循环内→原生跳转、无循环→return 收集值——
     * 解释器边界清零吞没 + Seq 停走语义的合并对应）。
     */
    private String genSwapCall(GenContext ctx, IExecutableExpression bodyExpr, String helper, String extraArgs) {
        String methodName = translateOutBody(ctx, bodyExpr);

        String frame = ctx.temp();
        emitFrameMarshal(ctx, frame);
        String exitVar = ctx.temp();
        ctx.line("io.nop.core.lang.eval.ExitMode[] " + exitVar + " = new io.nop.core.lang.eval.ExitMode[1];");
        String ret = ctx.temp();
        ctx.line("Object " + ret + " = null;");
        ctx.line("try {");
        ctx.indent();
        ctx.line(ret + " = XLangSemantics." + helper + "($scope" + (extraArgs != null ? ", " + extraArgs : "")
                + ", " + exitVar + ", ($s, $o, $e, $f) -> " + methodName + "($s, $o, $e, $f), " + frame + ");");
        ctx.unindent();
        ctx.line("} finally {");
        ctx.indent();
        emitFrameWriteback(ctx, frame);
        ctx.unindent();
        ctx.line("}");
        emitExitDispatch(ctx, exitVar, ret);
        return ret;
    }

    /**
     * GenNode 换缓冲：节点体先降级为 {@code $genBody_k(IEvalScope, IEvalOutput, ExitMode[], Object[] frame)}；
     * 发射体下降为 {@code private static void $gen_k(IEvalScope, IXNodeHandler, ExitMode[], Object[] frame)}
     * （属性/标签求值写槽在 body 前 flush），共享 helper genNode 承载 DisabledEvalOutput 收集 /
     * handler 直发分支，genNodeHandler 的 begin/body/end 序列与解释器同一实现。
     */
    private String genGenNode(GenContext ctx, GenNodeExecutable node) {
        String bodyMethod = node.getBodyExpr() == null ? null : translateOutBody(ctx, node.getBodyExpr());
        String genMethod = translateGenEmitter(ctx, node, bodyMethod);

        String frame = ctx.temp();
        emitFrameMarshal(ctx, frame);
        String exitVar = ctx.temp();
        ctx.line("io.nop.core.lang.eval.ExitMode[] " + exitVar + " = new io.nop.core.lang.eval.ExitMode[1];");
        String ret = ctx.temp();
        ctx.line("Object " + ret + " = null;");
        ctx.line("try {");
        ctx.indent();
        ctx.line(ret + " = XLangSemantics.genNode($out, " + exitVar + ", ($h, $e, $f) -> "
                + genMethod + "($scope, $h, $e, $f), " + frame + ");");
        ctx.unindent();
        ctx.line("} finally {");
        ctx.indent();
        emitFrameWriteback(ctx, frame);
        ctx.unindent();
        ctx.line("}");
        emitExitDispatch(ctx, exitVar, ret);
        return ret;
    }

    /** $gen_k 发射体方法（属性/标签求值 + genNodeHandler 接线；body lambda 引用 $genBody_k）。 */
    private String translateGenEmitter(GenContext ctx, GenNodeExecutable node, String bodyMethod) {
        String methodName = ctx.newMethodName("$gen");
        ctx.beginMethod(methodName, ctx.current().slotNames(), true, true,
                "io.nop.core.lang.xml.IXNodeHandler", true);
        emitFrameUnmarshal(ctx, "$frame");

        GenNodeAttrExecutable[] attrs = node.getAttrExprs();
        StringBuilder names = new StringBuilder("new String[]{");
        StringBuilder valueLocs = new StringBuilder("new io.nop.api.core.util.SourceLocation[]{");
        StringBuilder values = new StringBuilder("new Object[]{");
        for (int i = 0; i < attrs.length; i++) {
            String v = hoist(ctx, genExpr(ctx, attrs[i].getValueExpr()));
            if (i > 0) {
                names.append(", ");
                valueLocs.append(", ");
                values.append(", ");
            }
            names.append('"').append(escape(attrs[i].getName())).append('"');
            valueLocs.append(ctx.locRef(attrs[i].getValueExpr()));
            values.append(v);
        }
        names.append('}');
        valueLocs.append('}');
        values.append('}');
        String extAttrsValue = "null";
        if (node.getExtAttrs() != null)
            extAttrsValue = hoist(ctx, genExpr(ctx, node.getExtAttrs()));
        String tagNameValue = node.getTagName() != null ? "\"" + escape(node.getTagName()) + "\""
                : hoist(ctx, genExpr(ctx, node.getTagNameExpr()));
        List<String> attrNames = new ArrayList<>(attrs.length);
        for (GenNodeAttrExecutable attr : attrs)
            attrNames.add(attr.getName());
        String attrNamesRef = node.getExtAttrs() == null ? "null" : stringSetRef(attrNames);

        String attrMap = ctx.temp();
        String tag = ctx.temp();
        // 声明在 try 外（finally 写回帧后仍需在 genNodeHandler 调用中引用）
        ctx.line("java.util.Map " + attrMap + " = null;");
        ctx.line("String " + tag + " = null;");
        ctx.line("try {");
        ctx.indent();
        ctx.line(attrMap + " = XLangSemantics.genNodeAttrs(" + names + ", " + valueLocs
                + ", " + values + ", " + extAttrsValue + ", "
                + (node.getExtAttrs() == null ? "null" : ctx.locRef(node.getExtAttrs())) + ", " + attrNamesRef + ");");
        ctx.line(tag + " = XLangSemantics.genNodeTagName(" + ctx.locRef(node) + ", "
                + (node.getTagName() != null ? "\"" + escape(node.getTagName()) + "\"" : "null") + ", "
                + tagNameValue + ", "
                + (node.getTagNameExpr() == null ? "null" : displayOf(node.getTagNameExpr())) + ", "
                + displayOf(node) + ");");
        ctx.unindent();
        ctx.line("} finally {");
        ctx.indent();
        emitFrameWriteback(ctx, "$frame");
        ctx.unindent();
        ctx.line("}");
        String bodyLambda = bodyMethod == null ? "null"
                : "() -> " + bodyMethod + "($scope, $out, $exit, $frame)";
        ctx.line("XLangSemantics.genNodeHandler($out, " + ctx.locRef(node) + ", " + tag + ", " + attrMap
                + ", " + bodyLambda + ");");
        ctx.endMethod();
        return methodName;
    }

    /**
     * 统一函数私有方法生成（$fn_k）：slotNames 帧 → $v 局部变量；实参槽 = $args[i]
     * （i &lt; 提供数，缺省参数槽 = 内嵌字面量——前端 defaultArgValues 仅产生 Literal/CloneLiteral，
     * "解释器在调用者帧求值缺省"与"方法内嵌常量"行为等价）；闭包捕获 = $v&lt;targetSlot[i]&gt; =
     * $captured[i]（对应 BindVarExecutable 语义，捕获写入后于实参——与解释器
     * args → BindVar 次序一致）。
     */
    private String emitFunctionMethod(GenContext ctx, IExecutableExpression node, String[] slotNames,
                                      IExecutableExpression bodyExpr, int argCount, int demandArgCount,
                                      IExecutableExpression[] defaultArgValues, int[] capturedTargetSlots) {
        return emitFunctionMethod(ctx, node, slotNames, bodyExpr, argCount, demandArgCount,
                defaultArgValues, capturedTargetSlots, false);
    }

    /** withOut = 标签单元形态：$fn_k 追加 {@code IEvalOutput $out} 形参（输出族节点合法）。 */
    private String emitFunctionMethod(GenContext ctx, IExecutableExpression node, String[] slotNames,
                                      IExecutableExpression bodyExpr, int argCount, int demandArgCount,
                                      IExecutableExpression[] defaultArgValues, int[] capturedTargetSlots,
                                      boolean withOut) {
        String methodName = ctx.newMethodName("$fn");
        ctx.beginFnMethod(methodName, slotNames == null ? new String[0] : slotNames, withOut);
        int slotCount = slotNames == null ? 0 : slotNames.length;
        for (int i = 0; i < slotCount; i++) {
            ctx.line("Object $v" + i + " = null; // slot " + i + ": " + slotNames[i]);
        }
        for (int i = 0; i < argCount; i++) {
            if (i < demandArgCount || defaultArgValues == null || defaultArgValues.length == 0) {
                // 必填实参（或前端已内联缺省的调用形态）：直接取 $args[i]
                ctx.line("$v" + i + " = $args[" + i + "];");
            } else {
                String defaultValue = defaultArgValues[i - demandArgCount] == null ? "null"
                        : genExpr(ctx, defaultArgValues[i - demandArgCount]);
                ctx.line("$v" + i + " = $args.length > " + i + " ? $args[" + i + "] : " + defaultValue + ";");
            }
        }
        if (capturedTargetSlots != null) {
            for (int j = 0; j < capturedTargetSlots.length; j++) {
                ctx.line("$v" + capturedTargetSlots[j] + " = $captured[" + j + "];");
            }
        }
        emitRootValue(ctx, bodyExpr);
        ctx.endMethod();
        return methodName;
    }

    /** 直调调用点（非根 CallFunc/WithClosure/LazyCompiled）：args 数组 → captured 数组 → 直调 + 异常包装。 */
    private String emitDirectCallSite(GenContext ctx, IExecutableExpression node, String methodName,
                                      IExecutableExpression[] argExprs, int[] sourceSlots) {
        String argsVar = ctx.temp();
        ctx.line("Object[] " + argsVar + " = new Object[]{" + genArgs(ctx, argExprs) + "};");
        String capturedRef = capturedArrayRef(ctx, node, sourceSlots);
        String ret = ctx.temp();
        String exVar = ctx.temp();
        ctx.line("Object " + ret + " = null;");
        ctx.line("try {");
        ctx.indent();
        ctx.line(ret + " = " + methodName + "($scope, " + argsVar + ", " + capturedRef + ");");
        ctx.unindent();
        ctx.line("} catch (java.lang.Exception " + exVar + ") {");
        ctx.indent();
        ctx.line("throw XLangSemantics.wrapCallFuncException(" + displayOf(node) + ", " + ctx.locRef(node)
                + ", " + displayOf(node) + ", " + exVar + ");");
        ctx.unindent();
        ctx.line("}");
        return ret;
    }

    private String capturedArrayRef(GenContext ctx, IExecutableExpression node, int[] sourceSlots) {
        if (sourceSlots == null || sourceSlots.length == 0)
            return "new Object[0]";
        StringBuilder sb = new StringBuilder("new Object[]{");
        for (int i = 0; i < sourceSlots.length; i++) {
            checkSlot(ctx, node, sourceSlots[i]);
            if (i > 0)
                sb.append(", ");
            sb.append("$v").append(sourceSlots[i]);
        }
        sb.append('}');
        return sb.toString();
    }

    /**
     * 换缓冲生成体方法（$body_k / $genBody_k）：unmarshal 帧 → try{ 体 } finally{ writeback 帧 }
     * → 尾部 return（体为终端跳转时省略——跳转已在 try 内 return，尾部不可达）。
     */
    private String translateOutBody(GenContext ctx, IExecutableExpression bodyExpr) {
        String methodName = ctx.newMethodName("$body");
        ctx.beginMethod(methodName, ctx.current().slotNames(), true, true,
                "io.nop.core.lang.eval.IEvalOutput", false);
        emitFrameUnmarshal(ctx, "$frame");
        String result = ctx.temp();
        ctx.line("Object " + result + " = null;");
        ctx.line("try {");
        ctx.indent();
        String body = genBodyValue(ctx, bodyExpr);
        if (!isJump(body))
            ctx.line(result + " = " + body + ";");
        ctx.unindent();
        ctx.line("} finally {");
        ctx.indent();
        emitFrameWriteback(ctx, "$frame");
        ctx.unindent();
        ctx.line("}");
        if (!isTerminalJumpRef(body))
            ctx.line("return " + result + ";");
        ctx.endMethod();
        return methodName;
    }

    private void emitFrameMarshal(GenContext ctx, String frameVar) {
        StringBuilder sb = new StringBuilder("Object[] " + frameVar + " = new Object[]{");
        for (int i = 0; i < ctx.current().slotCount(); i++) {
            if (i > 0)
                sb.append(", ");
            sb.append("$v").append(i);
        }
        sb.append("};");
        ctx.line(sb.toString());
    }

    private void emitFrameUnmarshal(GenContext ctx, String frameVar) {
        for (int i = 0; i < ctx.current().slotCount(); i++) {
            ctx.line("Object $v" + i + " = " + frameVar + "[" + i + "];");
        }
    }

    private void emitFrameWriteback(GenContext ctx, String frameVar) {
        for (int i = 0; i < ctx.current().slotCount(); i++) {
            ctx.line(frameVar + "[" + i + "] = $v" + i + ";");
        }
    }

    /** 换缓冲调用点的 pending exit 分派（I4 Phase 1 §6 裁定形态）。 */
    private void emitExitDispatch(GenContext ctx, String exitVar, String valueVar) {
        ctx.line("if (" + exitVar + "[0] == io.nop.core.lang.eval.ExitMode.RETURN) {");
        ctx.indent();
        ctx.line("return " + valueVar + ";");
        ctx.unindent();
        ctx.line("}");
        if (ctx.current().loopDepth() > 0) {
            ctx.line("if (" + exitVar + "[0] == io.nop.core.lang.eval.ExitMode.BREAK) {");
            ctx.indent();
            // 跨缓冲边界的BREAK交给最内层可消费构造（loop或switch，与解释器一致）
            ctx.line("break " + (ctx.current().switchIsInnermostBreakTarget()
                    ? ctx.current().currentSwitchLabel()
                    : ctx.current().currentLoopLabel()) + ";");
            ctx.unindent();
            ctx.line("}");
            ctx.line("if (" + exitVar + "[0] == io.nop.core.lang.eval.ExitMode.CONTINUE) {");
            ctx.indent();
            ctx.line("break " + ctx.current().currentIterLabel() + ";");
            ctx.unindent();
            ctx.line("}");
        } else {
            // 无循环：解释器边界清零吞没 + Seq 停走语义的合并对应（收集值作为返回值）
            ctx.line("if (" + exitVar + "[0] != null) {");
            ctx.indent();
            ctx.line("return " + valueVar + ";");
            ctx.unindent();
            ctx.line("}");
        }
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
        int slot = ctx.current().indexOfSlotName(varName);
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
        boolean jumped = false;
        if (seq.isBlockStatement()) {
            for (IExecutableExpression child : exprs) {
                if (!genStatementChild(ctx, child)) {
                    jumped = true;
                    break;
                }
            }
            return jumped ? JUMP_RETURN : "null";
        }
        if (exprs.length == 0)
            return "null";
        for (int i = 0, n = exprs.length - 1; i < n; i++) {
            if (!genStatementChild(ctx, exprs[i]))
                return JUMP_RETURN; // 前置语句无条件跳转：Seq 停走，值为跳转承载（解释器 exitMode 语义）
        }
        String last = genBodyValue(ctx, exprs[exprs.length - 1]);
        if (isJump(last))
            return last;
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
        if (slot < 0 || slot >= ctx.current().slotCount())
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
    // 生成上下文（类级状态 + 方法发射器栈：入口方法与私有方法 $fn_k/$body_k/$gen_k 各一层）
    // ------------------------------------------------------------------

    private static final class GenContext {
        final String resourcePath;
        final Map<String, String> locConstants = new LinkedHashMap<>();
        final List<SourceLocation> locDecls = new ArrayList<>();
        final List<String> extraMethods = new ArrayList<>();
        boolean usesOut;
        boolean jumpCtx;
        /** xlib 每标签单元形态（I11）：入口参数 = (IEvalScope $scope, Object[] $args, IEvalOutput $out)。 */
        boolean entryTagForm;
        int methodCounter;

        private MethodEmitter current;
        private List<String> entryLines = new ArrayList<>();

        GenContext(String resourcePath) {
            this.resourcePath = resourcePath;
        }

        MethodEmitter current() {
            if (current == null)
                throw new IllegalStateException("no method emitter active");
            return current;
        }

        void beginEntryMethod(String[] slotNames) {
            current = new MethodEmitter(null, slotNames, false, true, null, false);
        }

        void beginMethod(String name, String[] slotNames, boolean outBody, boolean hasOut,
                         String outType, boolean voidMethod) {
            MethodEmitter m = new MethodEmitter(name, slotNames, outBody, hasOut, outType, voidMethod);
            m.caller = current;
            current = m;
        }

        /** 函数私有方法（$fn_k）：参数形态 = (IEvalScope $scope, Object[] $args, Object[] $captured[, IEvalOutput $out])。 */
        void beginFnMethod(String name, String[] slotNames) {
            beginFnMethod(name, slotNames, false);
        }

        void beginFnMethod(String name, String[] slotNames, boolean hasOut) {
            MethodEmitter m = new MethodEmitter(name, slotNames, false, hasOut, null, false);
            m.fnParams = true;
            m.caller = current;
            current = m;
        }

        void endMethod() {
            if (current == null)
                throw new IllegalStateException("no method emitter active");
            if (current.name == null) {
                entryLines = current.lines; // 入口方法：buildClass 直接消费
            } else {
                StringBuilder sb = new StringBuilder(256);
                sb.append("    private ").append(current.returnType()).append(' ').append(current.name).append('(')
                        .append("io.nop.core.lang.eval.IEvalScope $scope");
                if (current.fnParams) {
                    sb.append(", Object[] $args, Object[] $captured");
                    if (current.hasOut)
                        sb.append(", IEvalOutput ").append(EvalMethodConvention.OUT_PARAM);
                } else {
                    if (current.outType != null)
                        sb.append(", ").append(current.outType).append(" $out");
                    sb.append(", io.nop.core.lang.eval.ExitMode[] $exit, Object[] $frame");
                }
                sb.append(") {\n");
                for (String text : current.lines)
                    sb.append("    ").append(text).append('\n');
                sb.append("    }\n");
                extraMethods.add(sb.toString());
            }
            current = current.caller;
        }

        String newMethodName(String prefix) {
            return prefix + "_" + (++methodCounter);
        }

        String newLabel(String prefix) {
            return current().newLabel(prefix);
        }

        void line(String text) {
            current().line(text);
        }

        void indent() {
            current().indent++;
        }

        void unindent() {
            current().indent--;
        }

        String temp() {
            return current().temp();
        }

        void openLoop() {
            MethodEmitter m = current();
            m.loopDepth++;
            String loopVar = m.newLabel("$loop");
            String iterVar = m.newLabel("$iter");
            m.loopLabels.add(loopVar);
            m.iterLabels.add(iterVar);
            m.loopSeqs.add(++m.constructSeq);
            line(loopVar + ": while (true) {");
            indent();
            line(iterVar + ": {");
            indent();
        }

        void closeIterBlock() {
            unindent();
            line("} // iter block");
            indent();
        }

        void closeLoop() {
            unindent();
            line("}");
            MethodEmitter m = current();
            m.loopLabels.remove(m.loopLabels.size() - 1);
            m.iterLabels.remove(m.iterLabels.size() - 1);
            m.loopSeqs.remove(m.loopSeqs.size() - 1);
            m.loopDepth--;
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
            if (current != null)
                throw new IllegalStateException("unbalanced method emitters");
            StringBuilder sb = new StringBuilder(1024);
            sb.append("// source: ").append(resourcePath).append('\n');
            sb.append("package ").append(EvalMethodConvention.GENERATED_PACKAGE).append(";\n\n");
            sb.append("import ").append(SourceLocation.class.getName()).append(";\n");
            sb.append("import io.nop.core.lang.eval.IEvalScope;\n");
            sb.append("import io.nop.core.lang.eval.IEvalOutput;\n");
            sb.append("import io.nop.core.lang.eval.ExitMode;\n");
            sb.append("import io.nop.core.lang.xml.IXNodeHandler;\n");
            sb.append("import ").append(XLangSemantics.class.getName()).append(";\n\n");
            sb.append("public final class ").append(className).append(" {\n");
            for (int i = 0, n = locDecls.size(); i < n; i++) {
                SourceLocation loc = locDecls.get(i);
                sb.append("    private static final SourceLocation LOC_").append(i)
                        .append(" = SourceLocation.fromLine(\"").append(escape(loc.getPath()))
                        .append("\", ").append(loc.getLine()).append(", ").append(loc.getCol())
                        .append(");\n");
            }
            if (!locDecls.isEmpty())
                sb.append('\n');
            sb.append("    public static Object ").append(EvalMethodConvention.ENTRY_METHOD_NAME)
                    .append("(IEvalScope ").append(EvalMethodConvention.SCOPE_PARAM);
            if (entryTagForm) {
                sb.append(", Object[] ").append(EvalMethodConvention.ARGS_PARAM)
                        .append(", IEvalOutput ").append(EvalMethodConvention.OUT_PARAM);
            } else if (usesOut) {
                sb.append(", IEvalOutput ").append(EvalMethodConvention.OUT_PARAM);
            }
            sb.append(") {\n");
            for (String text : entryLines)
                sb.append("    ").append(text).append('\n');
            sb.append("    }\n");
            for (String method : extraMethods)
                sb.append('\n').append(method);
            sb.append("}\n");
            return sb.toString();
        }

        /** 单个生成方法的发射器（入口方法 name == null）。 */
        private static final class MethodEmitter {
            final String name;
            final List<String> lines = new ArrayList<>();
            final String[] slotNames;
            final int slotCount;
            final boolean outBody;
            final boolean hasOut;
            final String outType;
            final boolean voidMethod;
            boolean fnParams;
            MethodEmitter caller;
            int indent = 1;
            int tempCounter;
            int labelCounter;
            int loopDepth;
            final List<String> loopLabels = new ArrayList<>();
            final List<String> iterLabels = new ArrayList<>();
            final List<String> switchLabels = new ArrayList<>();
            final List<Integer> loopSeqs = new ArrayList<>();
            final List<Integer> switchSeqs = new ArrayList<>();
            int constructSeq;

            MethodEmitter(String name, String[] slotNames, boolean outBody, boolean hasOut,
                          String outType, boolean voidMethod) {
                this.name = name;
                this.slotNames = slotNames == null ? new String[0] : slotNames;
                this.slotCount = this.slotNames.length;
                this.outBody = outBody;
                this.hasOut = hasOut;
                this.outType = outType;
                this.voidMethod = voidMethod;
            }

            String returnType() {
                return voidMethod ? "static void" : "static Object";
            }

            int slotCount() {
                return slotCount;
            }

            String[] slotNames() {
                return slotNames;
            }

            int loopDepth() {
                return loopDepth;
            }

            String currentLoopLabel() {
                return loopLabels.get(loopLabels.size() - 1);
            }

            String currentIterLabel() {
                return iterLabels.get(iterLabels.size() - 1);
            }

            void openSwitch(String label) {
                switchLabels.add(label);
                switchSeqs.add(++constructSeq);
            }

            void closeSwitch() {
                switchLabels.remove(switchLabels.size() - 1);
                switchSeqs.remove(switchSeqs.size() - 1);
            }

            int switchDepth() {
                return switchLabels.size();
            }

            String currentSwitchLabel() {
                return switchLabels.get(switchLabels.size() - 1);
            }

            /**
             * break的最内层可跳转构造是否为switch（按进入顺序比较，loop与switch均记录序号）。
             */
            boolean switchIsInnermostBreakTarget() {
                if (switchLabels.isEmpty())
                    return false;
                if (loopLabels.isEmpty())
                    return true;
                return switchSeqs.get(switchSeqs.size() - 1) > loopSeqs.get(loopSeqs.size() - 1);
            }

            String currentBreakTargetLabel() {
                if (switchIsInnermostBreakTarget())
                    return currentSwitchLabel();
                return currentLoopLabel();
            }

            String newLabel(String prefix) {
                return prefix + "_" + (++labelCounter);
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

            String temp() {
                return "$t" + (tempCounter++);
            }
        }
    }
}
