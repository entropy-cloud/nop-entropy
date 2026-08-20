package io.nop.xlang.truffle.translate;

import com.oracle.truffle.api.RootCallTarget;
import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.xlang.exec.AbstractBinaryExecutable;
import io.nop.xlang.exec.AbstractObjFunctionExecutable;
import io.nop.xlang.exec.AndExecutable;
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
import io.nop.xlang.exec.CompareOpExecutable;
import io.nop.xlang.exec.ConcatExecutable;
import io.nop.xlang.exec.ContinueExecutable;
import io.nop.xlang.exec.ConvertExecutable;
import io.nop.xlang.exec.ConvertWithDefaultExecutable;
import io.nop.xlang.exec.DebugExecutable;
import io.nop.xlang.exec.DebugIdentifierExecutable;
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
import io.nop.xlang.exec.GenNodeAttrExecutable;
import io.nop.xlang.exec.GenNodeExecutable;
import io.nop.xlang.exec.GenXJsonExecutable;
import io.nop.xlang.exec.GeExecutable;
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
import io.nop.xlang.exec.ReturnExecutable;
import io.nop.xlang.exec.ReturnNullExecutable;
import io.nop.xlang.exec.ResolvedObjFunctionExecutable;
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
import io.nop.xlang.truffle.frame.FrameLayout;
import io.nop.xlang.truffle.frame.FrameLayoutMapper;
import io.nop.xlang.truffle.lang.XLangLanguage;
import io.nop.xlang.truffle.nodes.XArrayBindingNode;
import io.nop.xlang.truffle.nodes.XAssertOpNode;
import io.nop.xlang.truffle.nodes.XBetweenOpNode;
import io.nop.xlang.truffle.nodes.XBinaryEvalNode;
import io.nop.xlang.truffle.nodes.XBinaryOpNode;
import io.nop.xlang.truffle.nodes.XBindVarNode;
import io.nop.xlang.truffle.nodes.XBindingAssign;
import io.nop.xlang.truffle.nodes.XBreakNode;
import io.nop.xlang.truffle.nodes.XBuildClosureBodyNode;
import io.nop.xlang.truffle.nodes.XBuildFuncRefNode;
import io.nop.xlang.truffle.nodes.XCastNode;
import io.nop.xlang.truffle.nodes.XCloneLiteralNode;
import io.nop.xlang.truffle.nodes.XCompareOpNode;
import io.nop.xlang.truffle.nodes.XConcatNode;
import io.nop.xlang.truffle.nodes.XContinueNode;
import io.nop.xlang.truffle.nodes.XConvertNode;
import io.nop.xlang.truffle.nodes.XConvertWithDefaultNode;
import io.nop.xlang.truffle.nodes.XDebugIdentifierNode;
import io.nop.xlang.truffle.nodes.XDebugNode;
import io.nop.xlang.truffle.nodes.XDoWhileNode;
import io.nop.xlang.truffle.nodes.XEscapeOutputNode;
import io.nop.xlang.truffle.nodes.XExprNode;
import io.nop.xlang.truffle.nodes.XForInNode;
import io.nop.xlang.truffle.nodes.XForNode;
import io.nop.xlang.truffle.nodes.XForOfNode;
import io.nop.xlang.truffle.nodes.XFunctionValueNode;
import io.nop.xlang.truffle.nodes.XFunctionalAdapterNode;
import io.nop.xlang.truffle.nodes.XGenNodeNode;
import io.nop.xlang.truffle.nodes.XGetAttrNode;
import io.nop.xlang.truffle.nodes.XGetPropertyNode;
import io.nop.xlang.truffle.nodes.XGetterGetPropertyNode;
import io.nop.xlang.truffle.nodes.XGlobalFuncNode;
import io.nop.xlang.truffle.nodes.XGlobalVarReadNode;
import io.nop.xlang.truffle.nodes.XGuardNotNullNode;
import io.nop.xlang.truffle.nodes.XGuardNotEmptyNode;
import io.nop.xlang.truffle.nodes.XIfNode;
import io.nop.xlang.truffle.nodes.XInstanceOfNode;
import io.nop.xlang.truffle.nodes.XLangFunctionRootNode;
import io.nop.xlang.truffle.nodes.XLangRootNode;
import io.nop.xlang.truffle.nodes.XLiteralNode;
import io.nop.xlang.truffle.nodes.XLocalCallNode;
import io.nop.xlang.truffle.nodes.XLocationFunctionNode;
import io.nop.xlang.truffle.nodes.XLogicShortCircuitNode;
import io.nop.xlang.truffle.nodes.XMakePropertyNode;
import io.nop.xlang.truffle.nodes.XNewListNode;
import io.nop.xlang.truffle.nodes.XNewMapNode;
import io.nop.xlang.truffle.nodes.XNewObjectNode;
import io.nop.xlang.truffle.nodes.XNotNode;
import io.nop.xlang.truffle.nodes.XNullCheckNode;
import io.nop.xlang.truffle.nodes.XNullCoalesceNode;
import io.nop.xlang.truffle.nodes.XNullNode;
import io.nop.xlang.truffle.nodes.XObjMethodNode;
import io.nop.xlang.truffle.nodes.XObjectBindingNode;
import io.nop.xlang.truffle.nodes.XOutputSwapNode;
import io.nop.xlang.truffle.nodes.XOutputTextNode;
import io.nop.xlang.truffle.nodes.XOutputValueNode;
import io.nop.xlang.truffle.nodes.XOutputXmlAttrNode;
import io.nop.xlang.truffle.nodes.XOutputXmlExtAttrsNode;
import io.nop.xlang.truffle.nodes.XPropInNode;
import io.nop.xlang.truffle.nodes.XRangeNode;
import io.nop.xlang.truffle.nodes.XRefReadNode;
import io.nop.xlang.truffle.nodes.XRefRenewNode;
import io.nop.xlang.truffle.nodes.XRefSelfAssignNode;
import io.nop.xlang.truffle.nodes.XRefSelfIncNode;
import io.nop.xlang.truffle.nodes.XRefSlotInitNode;
import io.nop.xlang.truffle.nodes.XRefWriteNode;
import io.nop.xlang.truffle.nodes.XReturnNode;
import io.nop.xlang.truffle.nodes.XReturnNullNode;
import io.nop.xlang.truffle.nodes.XScopeReadNode;
import io.nop.xlang.truffle.nodes.XScopeSelfAssignNode;
import io.nop.xlang.truffle.nodes.XScopeSelfIncNode;
import io.nop.xlang.truffle.nodes.XScopeWriteNode;
import io.nop.xlang.truffle.nodes.XSelfAssignAttrNode;
import io.nop.xlang.truffle.nodes.XSelfAssignPropertyNode;
import io.nop.xlang.truffle.nodes.XSeqNode;
import io.nop.xlang.truffle.nodes.XSetAttrNode;
import io.nop.xlang.truffle.nodes.XSetPropertyNode;
import io.nop.xlang.truffle.nodes.XSetterSetPropertyNode;
import io.nop.xlang.truffle.nodes.XSlotReadNode;
import io.nop.xlang.truffle.nodes.XSlotSelfAssignNode;
import io.nop.xlang.truffle.nodes.XSlotSelfIncNode;
import io.nop.xlang.truffle.nodes.XSlotWriteNode;
import io.nop.xlang.truffle.nodes.XStaticGetPropertyNode;
import io.nop.xlang.truffle.nodes.XStaticMethodNode;
import io.nop.xlang.truffle.nodes.XSwitchNode;
import io.nop.xlang.truffle.nodes.XThrowErrorCodeNode;
import io.nop.xlang.truffle.nodes.XThrowExceptionNode;
import io.nop.xlang.truffle.nodes.XTryNode;
import io.nop.xlang.truffle.nodes.XUnaryOpNode;
import io.nop.xlang.truffle.nodes.XVarFunctionCallNode;
import io.nop.xlang.truffle.nodes.XVarStatusNode;
import io.nop.xlang.truffle.nodes.XWhileNode;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import static io.nop.xlang.XLangErrors.ARG_CLASS_NAME;
import static io.nop.xlang.XLangErrors.ARG_LOCATION;
import static io.nop.xlang.XLangErrors.ERR_EXEC_TRANSLATE_UNSUPPORTED_NODE;

/**
 * Executable 树 → Truffle AST 的纯函数翻译器（设计 truffle 02 §四/§七：树翻译而非适配包装）。
 *
 * <p>支持集（与 java 转译器/共享基线同口径）= {@code ExecNodeBaseline.truffleRegisteredTarget()}
 * 收敛后的全量口径（I2 表达式子集 + 覆盖 A 五族 + 并入残余 + <b>覆盖 B 三族（plan I7）</b> =
 * 120 类）。作用域链访问族按设计 Q3 落地：翻译期 slot 化优先，无法 slot 化的按名访问翻译为
 * context 持有的 scope 链查找节点。
 *
 * <p>覆盖 B（plan I7）：ExitMode → 控制流异常族（三值一一对应，SL 模式）；函数/闭包族按
 * <b>急切值拷贝</b>形态落地（函数体 = 独立 RootNode + 独立 FrameDescriptor，两拷贝时序载体
 * 各自保真：BuildFuncRef 捕获时快照 / CallFuncWithClosure 调用时拷值）；函数值调用点带
 * <b>两级内联缓存</b>（L1 CallTarget 身份 + L2 DirectCallNode，{@code XFunctionDispatchNode}），
 * 局部/闭包/LazyCompiled 调用为静态直达（L2）；输出/节点生成族经 context 换缓冲协议 +
 * I4 共享换缓冲 helper。
 *
 * <p>硬保证：fail-fast——支持集外节点翻译失败（报节点类名 + SourceLocation，禁止部分翻译）；
 * 语义敏感操作统一调用 {@link XLangSemantics} 共享 helper（D3，与解释器/java 侧同一实现来源，
 * 禁止在翻译节点内重写语义等价实现）；语义特化 fast-path 仅在已证实语义等价处引入
 * （plan I7：控制流异常族载体本身即已证实等价的机制性特化）；可抛错点携带合成 SourceSection
 * （回映射源位置）；每编译单元一个 RootNode + CallTarget（JIT 编译粒度），每函数体一个
 * 函数 RootNode + CallTarget。
 */
public final class ExecToTruffleTranslator {

    public TranslatedUnit translate(String sourceKey, long treeFingerprint, IExecutableExpression tree,
                                    XLangLanguage language) {
        FrameCtx ctx = new FrameCtx(language, FrameLayoutMapper.map(tree));
        XExprNode body;
        if (tree instanceof CallFuncExecutable) {
            CallFuncExecutable entry = (CallFuncExecutable) tree;
            if (entry.getArgExprs() != null && entry.getArgExprs().length > 0)
                throw unsupported(entry, "program entry with declared arguments");
            body = genExpr(entry.getBodyExpr(), ctx);
        } else {
            body = genExpr(tree, ctx);
        }
        XLangRootNode root = new XLangRootNode(language, ctx.layout, sourceKey, treeFingerprint, tree, body);
        return new TranslatedUnit(sourceKey, treeFingerprint, tree, root, root.getCallTarget());
    }

    // ------------------------------------------------------------------
    // 支持集可编程枚举（矩阵注册证据之一；分派为 instanceof 链，本集合与分派分支一一对应，
    // 一致性由矩阵测试逐类真实翻译验证——非清单自证）
    // ------------------------------------------------------------------

    /**
     * 翻译器已注册的具体节点类（I2 子集 + 覆盖 A 五族 + 并入残余 + 覆盖 B 三族（plan I7）=
     * {@code ExecNodeBaseline.truffleRegisteredTarget()} 收敛后全量 120 类，同基线同口径单一事实源）。
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
        // 覆盖 A：对象/集合构造与访问族（ListItem/MapItem 为 NewList/NewMap 复合元素，随宿主翻译）
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
        // 覆盖 B（plan I7）：函数/闭包族（8）
        Collections.addAll(set, VarFunctionExecutable.class, VarExecutableFunction.class,
                LazyCompiledExecutableFunction.class, FunctionalAdapterExecutable.class,
                CallFuncWithClosureExecutable.class, BuildFuncRefExecutable.class,
                BuildClosureBodyExecutable.class, LocationFunction.class);
        // 覆盖 B：控制流族（13）
        Collections.addAll(set, IfExecutable.class, SwitchExecutable.class,
                ForExecutable.class, ForInExecutable.class, ForOfExecutable.class,
                WhileExecutable.class, DoWhileExecutable.class,
                BreakExecutable.class, ContinueExecutable.class, ReturnExecutable.class,
                TryExecutable.class, ThrowErrorCodeExecutable.class, ThrowExceptionExecutable.class);
        // 覆盖 B：输出/节点生成族（12；GenNodeAttrExecutable 为 GenNode 属性描述符宿主载体）
        Collections.addAll(set, OutputTextExecutable.class, OutputValueExecutable.class,
                OutputXmlAttrExecutable.class, OutputXmlExtAttrsExecutable.class,
                GenNodeExecutable.class, GenNodeAttrExecutable.class,
                GenXJsonExecutable.class, CollectJsonExecutable.class, CollectNodeExecutable.class,
                CollectSqlExecutable.class, CollectTextExecutable.class, EscapeOutputExecutable.class);
        return Collections.unmodifiableSet(set);
    }

    /**
     * 翻译器支持集（矩阵消费；文件级具体类 + 嵌套同族特化变体经继承链解析）。
     */
    public static Set<Class<?>> getSupportedNodeClasses() {
        return SUPPORTED_NODE_CLASSES;
    }

    /**
     * 节点类是否可翻译（沿继承/接口链解析到已注册类；未注册 = false，对应分派 fail-fast）。
     */
    public static boolean isNodeClassSupported(Class<?> nodeClass) {
        for (Class<?> c = nodeClass; c != null; c = c.getSuperclass()) {
            if (SUPPORTED_NODE_CLASSES.contains(c))
                return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // 翻译上下文（帧级）：帧布局 + 词法循环嵌套深度（换缓冲族 pending exit 分派的静态标志）
    // ------------------------------------------------------------------

    private static final class FrameCtx {
        final XLangLanguage language;

        final FrameLayout layout;

        int loopDepth;

        FrameCtx(XLangLanguage language, FrameLayout layout) {
            this.language = language;
            this.layout = layout;
        }
    }

    // ------------------------------------------------------------------
    // 函数体翻译（plan I7：急切值拷贝闭包形态——每函数体独立 RootNode + CallTarget）
    // ------------------------------------------------------------------

    /**
     * ExecutableFunction 载荷翻译（BuildFuncRef 捕获载体 / Literal 函数字面量载荷下降）：
     * 函数体 → {@link XLangFunctionRootNode}（实参槽/缺省槽/闭包目标槽入口绑定）。
     */
    private RootCallTarget functionTarget(ExecutableFunction fn, int[] targetSlots,
                                                 IExecutableExpression node, FrameCtx caller) {
        if (fn == null || fn.getBody() == null)
            throw unsupported(node, "function payload without body");
        int[] closureSlots = targetSlots == null ? new int[0] : targetSlots;
        FrameLayout fnLayout = FrameLayoutMapper.mapFunction(fn.getSlotNames(), fn.getArgCount(),
                closureSlots, fn.getBody());
        FrameCtx fnCtx = new FrameCtx(caller.language, fnLayout);
        XExprNode fnBody = genExpr(fn.getBody(), fnCtx);
        XExprNode[] defaults = fn.getDefaultArgValues() == null || fn.getDefaultArgValues().length == 0
                ? null : genArgs(fn.getDefaultArgValues(), fnCtx);
        XLangFunctionRootNode root = new XLangFunctionRootNode(caller.language, fnLayout,
                "xl:fn:" + fn.getFuncName(), fn.getArgCount(), defaults, closureSlots, fnBody);
        return root.getCallTarget();
    }

    /**
     * 局部调用目标翻译（非根 CallFunc / CallFuncWithClosure / LazyCompiled 被调体）：
     * 实参槽 = 前端已内联缺省的全量实参（I4 裁定口径，无缺省槽），闭包目标槽按载体传入。
     */
    private RootCallTarget localCallTarget(String[] slotNames, IExecutableExpression body,
                                                  int argCount, int[] targetSlots,
                                                  IExecutableExpression node, FrameCtx caller) {
        if (body == null)
            throw unsupported(node, "local function without body");
        int[] closureSlots = targetSlots == null ? new int[0] : targetSlots;
        FrameLayout fnLayout = FrameLayoutMapper.mapFunction(slotNames, argCount, closureSlots, body);
        FrameCtx fnCtx = new FrameCtx(caller.language, fnLayout);
        XExprNode fnBody = genExpr(body, fnCtx);
        XLangFunctionRootNode root = new XLangFunctionRootNode(caller.language, fnLayout,
                "xl:fn:" + displayOf(node), argCount, null, closureSlots, fnBody);
        return root.getCallTarget();
    }

    /**
     * LazyCompiled 载荷 force-compile（I4 Phase 1 §5 同类决策伞消费）：null/不可解析 → 显式
     * fail-fast（矩阵最小实例 null 载荷即此路径的反证）。
     */
    private static ExecutableFunction forceCompile(LazyCompiledExecutableFunction node,
                                                   IExecutableExpression source) {
        try {
            return node.getCompiled();
        } catch (RuntimeException e) {
            throw unsupported(source, "lazy function payload not resolvable: " + e);
        }
    }

    // ------------------------------------------------------------------
    // 表达式分派
    // ------------------------------------------------------------------

    private XExprNode genExpr(IExecutableExpression node, FrameCtx ctx) {
        if (node == null)
            return XNullNode.INSTANCE;

        XExprNode result;
        if (node instanceof LiteralExecutable) {
            Object value = ((LiteralExecutable) node).getValue();
            if (value instanceof ExecutableFunction) {
                // 函数字面量载荷下降（plan I7：与 java 侧 I4 §5 载荷处置对称）
                ExecutableFunction fn = (ExecutableFunction) value;
                result = new XFunctionValueNode(fn, functionTarget(fn, null, node, ctx));
            } else {
                result = new XLiteralNode(value);
            }
        } else if (node instanceof CloneLiteralExecutable) {
            result = new XCloneLiteralNode(((CloneLiteralExecutable) node).getValue());
        } else if (node instanceof NullExecutable) {
            result = XNullNode.INSTANCE;
        } else if (node instanceof SlotIdentifierExecutable) {
            SlotIdentifierExecutable slot = (SlotIdentifierExecutable) node;
            result = new XSlotReadNode(checkSlot(slot.getSlot(), node, ctx.layout), slot.getId());
        } else if (node instanceof SlotAssignExecutable) {
            SlotAssignExecutable assign = (SlotAssignExecutable) node;
            int slotIndex = checkSlot(assign.getSlot(), node, ctx.layout);
            XExprNode value = genExpr(assign.getExpr(), ctx);
            result = new XSlotWriteNode(slotIndex, ctx.layout.getSlot(slotIndex).getKind(),
                    assign.getVarName(), value);
        } else if (node instanceof PlusExecutable) {
            result = binary(XBinaryOpNode.Op.PLUS, node, ctx);
        } else if (node instanceof MinusExecutable) {
            result = binary(XBinaryOpNode.Op.MINUS, node, ctx);
        } else if (node instanceof MultiplyExecutable) {
            result = binary(XBinaryOpNode.Op.MULTIPLY, node, ctx);
        } else if (node instanceof DivideExecutable) {
            result = binary(XBinaryOpNode.Op.DIVIDE, node, ctx);
        } else if (node instanceof EqExecutable || node instanceof StrictEqExecutable) {
            result = binary(XBinaryOpNode.Op.EQ, node, ctx);
        } else if (node instanceof NeExecutable || node instanceof StrictNeExecutable) {
            result = binary(XBinaryOpNode.Op.NE, node, ctx);
        } else if (node instanceof GtExecutable) {
            result = binary(XBinaryOpNode.Op.GT, node, ctx);
        } else if (node instanceof GeExecutable) {
            result = binary(XBinaryOpNode.Op.GE, node, ctx);
        } else if (node instanceof LtExecutable) {
            result = binary(XBinaryOpNode.Op.LT, node, ctx);
        } else if (node instanceof LeExecutable) {
            result = binary(XBinaryOpNode.Op.LE, node, ctx);
        } else if (node instanceof BinaryExecutable) {
            BinaryExecutable binary = (BinaryExecutable) node;
            result = new XBinaryEvalNode(binary.getOperator(),
                    genExpr(binary.getLeft(), ctx), genExpr(binary.getRight(), ctx));
        } else if (node instanceof AndExecutable) {
            AbstractBinaryExecutable binary = (AbstractBinaryExecutable) node;
            result = new XLogicShortCircuitNode(true, genExpr(binary.getLeft(), ctx),
                    genExpr(binary.getRight(), ctx));
        } else if (node instanceof OrExecutable) {
            AbstractBinaryExecutable binary = (AbstractBinaryExecutable) node;
            result = new XLogicShortCircuitNode(false, genExpr(binary.getLeft(), ctx),
                    genExpr(binary.getRight(), ctx));
        } else if (node instanceof NotExecutable) {
            result = new XNotNode(genExpr(((NotExecutable) node).getExpr(), ctx));
        } else if (node instanceof NegExecutable) {
            result = new XUnaryOpNode(XUnaryOpNode.Op.NEG, genExpr(((NegExecutable) node).getExpr(), ctx));
        } else if (node instanceof BitNotExecutable) {
            result = new XUnaryOpNode(XUnaryOpNode.Op.BIT_NOT, genExpr(((BitNotExecutable) node).getExpr(), ctx));
        } else if (node instanceof TypeOfExecutable) {
            result = new XUnaryOpNode(XUnaryOpNode.Op.TYPE_OF, genExpr(((TypeOfExecutable) node).getExpr(), ctx));
        } else if (node instanceof NullCoalesceExecutable) {
            AbstractBinaryExecutable binary = (AbstractBinaryExecutable) node;
            result = new XNullCoalesceNode(genExpr(binary.getLeft(), ctx),
                    genExpr(binary.getRight(), ctx));
        } else if (node instanceof EqNullExecutable || node instanceof StrictEqNullExecutable) {
            IExecutableExpression inner = node instanceof EqNullExecutable
                    ? ((EqNullExecutable) node).getExpr() : ((StrictEqNullExecutable) node).getExpr();
            result = new XNullCheckNode(XNullCheckNode.Mode.EQ_NULL, genExpr(inner, ctx));
        } else if (node instanceof NeNullExecutable || node instanceof StrictNeNullExecutable) {
            IExecutableExpression inner = node instanceof NeNullExecutable
                    ? ((NeNullExecutable) node).getExpr() : ((StrictNeNullExecutable) node).getExpr();
            result = new XNullCheckNode(XNullCheckNode.Mode.NE_NULL, genExpr(inner, ctx));
        } else if (node instanceof CompareOpExecutable) {
            CompareOpExecutable cmp = (CompareOpExecutable) node;
            result = new XCompareOpNode(cmp.getFilterOp(), genExpr(cmp.getLeft(), ctx),
                    genExpr(cmp.getRight(), ctx));
        } else if (node instanceof BetweenOpExecutable) {
            result = genBetweenOp((BetweenOpExecutable) node, ctx);
        } else if (node instanceof AssertOpExecutable) {
            AssertOpExecutable assertOp = (AssertOpExecutable) node;
            result = new XAssertOpNode(assertOp.getFilterOp(),
                    genExpr(assertOp.getValueExpr(), ctx));
        } else if (node instanceof RangeExecutable) {
            RangeExecutable range = (RangeExecutable) node;
            result = new XRangeNode(node.getLocation(), displayOf(node), genExpr(range.getBeginExpr(), ctx),
                    genExpr(range.getEndExpr(), ctx), genExpr(range.getStepExpr(), ctx));
        } else if (node instanceof PropInExecutable) {
            AbstractBinaryExecutable binary = (AbstractBinaryExecutable) node;
            result = new XPropInNode(genExpr(binary.getLeft(), ctx), genExpr(binary.getRight(), ctx));
        } else if (node instanceof ConcatExecutable) {
            result = new XConcatNode(genArgs(((ConcatExecutable) node).getExprs(), ctx));
        } else if (node instanceof AbstractObjFunctionExecutable) {
            AbstractObjFunctionExecutable fn = (AbstractObjFunctionExecutable) node;
            result = new XObjMethodNode(node.getLocation(), displayOf(node), fn.getFuncName(),
                    genExpr(fn.getObjExpr(), ctx), genArgs(fn.getArgs(), ctx));
        } else if (node instanceof FunctionExecutable) {
            FunctionExecutable fn = (FunctionExecutable) node;
            result = new XGlobalFuncNode(node.getLocation(), displayOf(node), fn.getFuncName(),
                    genArgs(fn.getArgs(), ctx));
        } else if (node instanceof StaticFunctionExecutable) {
            StaticFunctionExecutable fn = (StaticFunctionExecutable) node;
            // plan I7 既有节点缓存回填：编译期常量 methodCollection 直持（L2 直达形态）
            result = new XStaticMethodNode(node.getLocation(), displayOf(node), fn.getClassName(),
                    fn.getFuncName(), fn.isOptional(), fn.getMethodCollection(), genArgs(fn.getArgExprs(), ctx));
        } else if (node instanceof GuardNotNullExecutable) {
            result = new XGuardNotNullNode(node.getLocation(), displayOf(node),
                    genExpr(((GuardNotNullExecutable) node).getExpr(), ctx));
        } else if (node instanceof GuardNotEmptyExecutable) {
            GuardNotEmptyExecutable guard = (GuardNotEmptyExecutable) node;
            result = new XGuardNotEmptyNode(node.getLocation(), displayOf(node),
                    String.valueOf(guard.getTarget()), genExpr(guard.getExpr(), ctx));
        } else if (node instanceof ISeqExecutable) {
            ISeqExecutable seq = (ISeqExecutable) node;
            result = new XSeqNode(genArgs(seq.getExprs(), ctx), seq.isBlockStatement());
        } else if (node instanceof ReturnNullExecutable) {
            result = new XReturnNullNode(genExpr(((ReturnNullExecutable) node).getExecutable(), ctx));
        } else if (node instanceof ScopeIdentifierExecutable) {
            result = new XScopeReadNode(node.getLocation(), displayOf(node),
                    ((ScopeIdentifierExecutable) node).getVarName());
        } else if (node instanceof GlobalVarExecutable) {
            result = new XGlobalVarReadNode(node.getLocation(), displayOf(node),
                    ((GlobalVarExecutable) node).getVarName());
        } else if (node instanceof ScopeAssignExecutable) {
            ScopeAssignExecutable assign = (ScopeAssignExecutable) node;
            result = new XScopeWriteNode(node.getLocation(), assign.getVarName(),
                    genExpr(assign.getExpr(), ctx));
        } else if (node instanceof ScopeSelfAssignExecutable) {
            ScopeSelfAssignExecutable self = (ScopeSelfAssignExecutable) node;
            result = new XScopeSelfAssignNode(node.getLocation(), displayOf(node), self.getVarName(),
                    self.getOperator(), genExpr(self.getExpr(), ctx));
        } else if (node instanceof ScopeSelfIncExecutable) {
            result = new XScopeSelfIncNode(node.getLocation(),
                    ((ScopeSelfIncExecutable) node).getVarName(), 1);
        } else if (node instanceof ScopeSelfDecExecutable) {
            result = new XScopeSelfIncNode(node.getLocation(),
                    ((ScopeSelfDecExecutable) node).getVarName(), -1);
        } else if (node instanceof ReferenceIdentifierExecutable) {
            ReferenceIdentifierExecutable ref = (ReferenceIdentifierExecutable) node;
            result = new XRefReadNode(node.getLocation(), displayOf(node), ref.getId(),
                    checkSlot(ref.getSlot(), node, ctx.layout));
        } else if (node instanceof ReferenceAssignExecutable) {
            ReferenceAssignExecutable assign = (ReferenceAssignExecutable) node;
            result = new XRefWriteNode(checkSlot(assign.getSlot(), node, ctx.layout),
                    genExpr(assign.getExpr(), ctx));
        } else if (node instanceof ReferenceSelfAssignExecutable) {
            ReferenceSelfAssignExecutable self = (ReferenceSelfAssignExecutable) node;
            result = new XRefSelfAssignNode(node.getLocation(), displayOf(node), self.getVarName(),
                    checkSlot(self.getSlot(), node, ctx.layout), self.getOperator(),
                    genExpr(self.getExpr(), ctx));
        } else if (node instanceof ReferenceSelfIncExecutable) {
            ReferenceSelfIncExecutable self = (ReferenceSelfIncExecutable) node;
            result = new XRefSelfIncNode(node.getLocation(), displayOf(node), self.getVarName(),
                    checkSlot(self.getSlot(), node, ctx.layout), 1);
        } else if (node instanceof ReferenceSelfDecExecutable) {
            ReferenceSelfDecExecutable self = (ReferenceSelfDecExecutable) node;
            result = new XRefSelfIncNode(node.getLocation(), displayOf(node), self.getVarName(),
                    checkSlot(self.getSlot(), node, ctx.layout), -1);
        } else if (node instanceof RenewReferenceExecutable) {
            result = new XRefRenewNode(checkSlot(((RenewReferenceExecutable) node).getSlot(), node, ctx.layout));
        } else if (node instanceof CastExecutable) {
            CastExecutable cast = (CastExecutable) node;
            result = new XCastNode(node.getLocation(), displayOf(node), cast.getClazz().getName(),
                    genExpr(cast.getExpr(), ctx));
        } else if (node instanceof ConvertExecutable) {
            ConvertExecutable convert = (ConvertExecutable) node;
            result = new XConvertNode(node.getLocation(), displayOf(node), convert.getFuncName(),
                    genExpr(convert.getExpr(), ctx));
        } else if (node instanceof ConvertWithDefaultExecutable) {
            ConvertWithDefaultExecutable convert = (ConvertWithDefaultExecutable) node;
            result = new XConvertWithDefaultNode(node.getLocation(), displayOf(node), convert.getFuncName(),
                    genExpr(convert.getExpr(), ctx), genExpr(convert.getDefaultExpr(), ctx));
        } else if (node instanceof InstanceOfExecutable) {
            InstanceOfExecutable inst = (InstanceOfExecutable) node;
            result = new XInstanceOfNode(inst.getType().getRawClass().getName(),
                    genExpr(inst.getExpr(), ctx));
        } else if (node instanceof MakePropertyExecutable) {
            MakePropertyExecutable make = (MakePropertyExecutable) node;
            result = new XMakePropertyNode(node.getLocation(), displayOf(node),
                    displayOf(make.getObjExpr()), make.getPropName(),
                    genExpr(make.getObjExpr(), ctx));
        } else if (node instanceof GetPropertyExecutable) {
            GetPropertyExecutable get = (GetPropertyExecutable) node;
            result = new XGetPropertyNode(node.getLocation(), displayOf(node), displayOf(get.getObjExpr()),
                    get.isOptional(), get.getPropName(), genExpr(get.getObjExpr(), ctx));
        } else if (node instanceof GetterGetPropertyExecutable) {
            GetterGetPropertyExecutable get = (GetterGetPropertyExecutable) node;
            result = new XGetterGetPropertyNode(node.getLocation(), displayOf(node), get.getPropName(),
                    genExpr(get.getObjExpr(), ctx));
        } else if (node instanceof StaticGetterGetPropertyExecutable) {
            StaticGetterGetPropertyExecutable get = (StaticGetterGetPropertyExecutable) node;
            result = new XStaticGetPropertyNode(node.getLocation(), displayOf(node),
                    get.getClassName(), get.getPropName());
        } else if (node instanceof SetPropertyExecutable) {
            SetPropertyExecutable set = (SetPropertyExecutable) node;
            result = new XSetPropertyNode(node.getLocation(), displayOf(node), set.getPropName(),
                    genExpr(set.getObjExpr(), ctx), genExpr(set.getValueExpr(), ctx));
        } else if (node instanceof SetterSetPropertyExecutable) {
            SetterSetPropertyExecutable set = (SetterSetPropertyExecutable) node;
            result = new XSetterSetPropertyNode(node.getLocation(), displayOf(node), set.getPropName(),
                    genExpr(set.getObjExpr(), ctx), genExpr(set.getValueExpr(), ctx));
        } else if (node instanceof SelfAssignPropertyExecutable) {
            SelfAssignPropertyExecutable self = (SelfAssignPropertyExecutable) node;
            result = new XSelfAssignPropertyNode(node.getLocation(), displayOf(node), self.getPropName(),
                    self.getOperator(), genExpr(self.getObjExpr(), ctx),
                    genExpr(self.getValueExpr(), ctx));
        } else if (node instanceof GetAttrExecutable) {
            GetAttrExecutable get = (GetAttrExecutable) node;
            result = new XGetAttrNode(node.getLocation(), displayOf(node), displayOf(get.getObjExpr()),
                    displayOf(get.getAttrExpr()), get.isOptional(),
                    genExpr(get.getObjExpr(), ctx), genExpr(get.getAttrExpr(), ctx));
        } else if (node instanceof SetAttrExecutable) {
            SetAttrExecutable set = (SetAttrExecutable) node;
            result = new XSetAttrNode(node.getLocation(), displayOf(node), displayOf(set.getAttrExpr()),
                    genExpr(set.getObjExpr(), ctx), genExpr(set.getAttrExpr(), ctx),
                    genExpr(set.getValueExpr(), ctx));
        } else if (node instanceof SelfAssignAttrExecutable) {
            SelfAssignAttrExecutable self = (SelfAssignAttrExecutable) node;
            result = new XSelfAssignAttrNode(node.getLocation(), displayOf(node),
                    displayOf(self.getAttrExpr()), self.getOperator(),
                    genExpr(self.getObjExpr(), ctx), genExpr(self.getAttrExpr(), ctx),
                    genExpr(self.getValueExpr(), ctx));
        } else if (node instanceof NewObjectExecutable) {
            NewObjectExecutable newExpr = (NewObjectExecutable) node;
            result = new XNewObjectNode(node.getLocation(), displayOf(node), newExpr.getClassModel(),
                    genArgs(newExpr.getArgExprs(), ctx));
        } else if (node instanceof NewListExecutable) {
            result = genNewList((NewListExecutable) node, ctx);
        } else if (node instanceof NewMapExecutable) {
            result = genNewMap((NewMapExecutable) node, ctx);
        } else if (node instanceof BindVarExecutable) {
            result = genBindVar((BindVarExecutable) node, ctx);
        } else if (node instanceof ArrayBindingAssignExecutable) {
            result = genArrayBinding((ArrayBindingAssignExecutable) node, ctx);
        } else if (node instanceof ObjectBindingAssignExecutable) {
            result = genObjectBinding((ObjectBindingAssignExecutable) node, ctx);
        } else if (node instanceof InitRefSlotExecutable) {
            result = new XRefSlotInitNode(checkSlot(((InitRefSlotExecutable) node).getSlot(), node, ctx.layout),
                    XRefSlotInitNode.Mode.INIT);
        } else if (node instanceof EnhanceRefSlotExecutable) {
            result = new XRefSlotInitNode(checkSlot(((EnhanceRefSlotExecutable) node).getSlot(), node, ctx.layout),
                    XRefSlotInitNode.Mode.ENHANCE);
        } else if (node instanceof DebugExecutable) {
            DebugExecutable debug = (DebugExecutable) node;
            result = new XDebugNode(node.getLocation(), displayOf(debug.getValueExpr()),
                    genExpr(debug.getValueExpr(), ctx), genExpr(debug.getPrefixExpr(), ctx));
        } else if (node instanceof DebugIdentifierExecutable) {
            DebugIdentifierExecutable identifier = (DebugIdentifierExecutable) node;
            result = new XDebugIdentifierNode(indexOfSlotName(ctx.layout, identifier.getVarName()),
                    identifier.getVarName());
        } else if (node instanceof VarStatusExecutable) {
            VarStatusExecutable varStatus = (VarStatusExecutable) node;
            result = new XVarStatusNode(node.getLocation(), displayOf(node),
                    displayOf(varStatus.getItemsExpr()),
                    checkSlot(varStatus.getVarStatusSlot(), node, ctx.layout),
                    genExpr(varStatus.getItemsExpr(), ctx));
        } else if (node instanceof SelfAssignExecutable) {
            SelfAssignExecutable self = (SelfAssignExecutable) node;
            result = new XSlotSelfAssignNode(node.getLocation(), displayOf(node),
                    checkSlot(self.getSlot(), node, ctx.layout), self.getOperator(),
                    genExpr(self.getExpr(), ctx));
        } else if (node instanceof SelfIncExecutable) {
            result = new XSlotSelfIncNode(
                    checkSlot(((SelfIncExecutable) node).getSlot(), node, ctx.layout), 1);
        } else if (node instanceof SelfDecExecutable) {
            result = new XSlotSelfIncNode(
                    checkSlot(((SelfDecExecutable) node).getSlot(), node, ctx.layout), -1);
        } else if (node instanceof IfExecutable) {
            IfExecutable ifExpr = (IfExecutable) node;
            result = new XIfNode(genExpr(ifExpr.getTest(), ctx),
                    genExpr(ifExpr.getConsequent(), ctx),
                    ifExpr.getAlternate() == null ? null : genExpr(ifExpr.getAlternate(), ctx));
        } else if (node instanceof SwitchExecutable) {
            result = genSwitch((SwitchExecutable) node, ctx);
        } else if (node instanceof ForExecutable) {
            ForExecutable forExpr = (ForExecutable) node;
            result = new XForNode(
                    forExpr.getInitExpr() == null ? null : genExpr(forExpr.getInitExpr(), ctx),
                    forExpr.getTestExpr() == null ? null : genExpr(forExpr.getTestExpr(), ctx),
                    forExpr.getUpdateExpr() == null ? null : genExpr(forExpr.getUpdateExpr(), ctx),
                    loopBody(forExpr.getBodyExpr(), ctx));
        } else if (node instanceof ForInExecutable) {
            ForInExecutable forIn = (ForInExecutable) node;
            result = new XForInNode(node.getLocation(), displayOf(node),
                    checkSlot(forIn.getVarSlot(), node, ctx.layout),
                    genExpr(forIn.getItemsExpr(), ctx), loopBody(forIn.getBodyExpr(), ctx));
        } else if (node instanceof ForOfExecutable) {
            ForOfExecutable forOf = (ForOfExecutable) node;
            checkSlot(forOf.getVarSlot(), node, ctx.layout);
            if (forOf.getIndexSlot() >= 0)
                checkSlot(forOf.getIndexSlot(), node, ctx.layout);
            result = new XForOfNode(node.getLocation(), displayOf(node), displayOf(forOf.getItemsExpr()),
                    forOf.getVarSlot(), forOf.getIndexSlot(), forOf.isUseRef(),
                    genExpr(forOf.getItemsExpr(), ctx), loopBody(forOf.getBodyExpr(), ctx));
        } else if (node instanceof WhileExecutable) {
            WhileExecutable whileExpr = (WhileExecutable) node;
            result = new XWhileNode(genExpr(whileExpr.getTestExpr(), ctx),
                    loopBody(whileExpr.getBodyExpr(), ctx));
        } else if (node instanceof DoWhileExecutable) {
            DoWhileExecutable doWhile = (DoWhileExecutable) node;
            result = new XDoWhileNode(
                    doWhile.getTestExpr() == null ? null : genExpr(doWhile.getTestExpr(), ctx),
                    loopBody(doWhile.getBodyExpr(), ctx));
        } else if (node instanceof BreakExecutable) {
            result = XBreakNode.INSTANCE;
        } else if (node instanceof ContinueExecutable) {
            result = XContinueNode.INSTANCE;
        } else if (node instanceof ReturnExecutable) {
            ReturnExecutable ret = (ReturnExecutable) node;
            result = new XReturnNode(ret.getExpr() == null ? null : genExpr(ret.getExpr(), ctx));
        } else if (node instanceof TryExecutable) {
            TryExecutable tryExpr = (TryExecutable) node;
            int exceptionSlot = tryExpr.getExceptionSlot();
            if (exceptionSlot >= 0)
                checkSlot(exceptionSlot, node, ctx.layout);
            result = new XTryNode(genExpr(tryExpr.getBodyExpr(), ctx), exceptionSlot,
                    tryExpr.getCatchExpr() == null ? null : genExpr(tryExpr.getCatchExpr(), ctx),
                    tryExpr.getFinallyExpr() == null ? null : genExpr(tryExpr.getFinallyExpr(), ctx));
        } else if (node instanceof ThrowErrorCodeExecutable) {
            ThrowErrorCodeExecutable thr = (ThrowErrorCodeExecutable) node;
            result = new XThrowErrorCodeNode(node.getLocation(), displayOf(node),
                    genExpr(thr.getErrorExpr(), ctx),
                    thr.getParamsExpr() == null ? null : genExpr(thr.getParamsExpr(), ctx));
        } else if (node instanceof ThrowExceptionExecutable) {
            ThrowExceptionExecutable thr = (ThrowExceptionExecutable) node;
            result = new XThrowExceptionNode(node.getLocation(), displayOf(node),
                    genExpr(thr.getExpr(), ctx));
        } else if (node instanceof CallFuncWithClosureExecutable) {
            // 局部具名函数调用（调用时闭包拷贝载体）：L2 静态直达 + sourceSlots 调用者帧拷值
            CallFuncWithClosureExecutable call = (CallFuncWithClosureExecutable) node;
            result = new XLocalCallNode(node, node.getLocation(), displayOf(node),
                    localCallTarget(call.getSlotNames(), call.getBodyExpr(),
                            call.getArgExprs().length, call.getTargetSlots(), node, ctx),
                    genArgs(call.getArgExprs(), ctx), call.getSourceSlots(), true);
        } else if (node instanceof CallFuncExecutable) {
            // 非根 CallFunc = 局部函数调用（I4 Phase 1 §2 裁定并入；前端已内联缺省实参）
            CallFuncExecutable call = (CallFuncExecutable) node;
            result = new XLocalCallNode(node, node.getLocation(), displayOf(node),
                    localCallTarget(call.getSlotNames(), call.getBodyExpr(),
                            call.getArgExprs().length, null, node, ctx),
                    genArgs(call.getArgExprs(), ctx), new int[0], true);
        } else if (node instanceof LazyCompiledExecutableFunction) {
            LazyCompiledExecutableFunction lazy = (LazyCompiledExecutableFunction) node;
            ExecutableFunction fn = forceCompile(lazy, node);
            result = new XLocalCallNode(node, node.getLocation(), displayOf(node),
                    localCallTarget(fn.getSlotNames(), fn.getBody(),
                            lazy.getArgExprs().length, null, node, ctx),
                    genArgs(lazy.getArgExprs(), ctx), new int[0], false);
        } else if (node instanceof BuildFuncRefExecutable) {
            // 函数指针捕获（捕获时闭包拷贝载体）：急切值快照 → XLangTruffleFunction
            BuildFuncRefExecutable ref = (BuildFuncRefExecutable) node;
            for (int slot : ref.getSourceSlots())
                checkSlot(slot, node, ctx.layout);
            result = new XBuildFuncRefNode(ref.getFunc(), ref.getSourceSlots(),
                    functionTarget(ref.getFunc(), ref.getTargetSlots(), node, ctx));
        } else if (node instanceof BuildClosureBodyExecutable) {
            BuildClosureBodyExecutable closure = (BuildClosureBodyExecutable) node;
            checkSlot(closure.getClosureSlot(), node, ctx.layout);
            for (int slot : closure.getSourceSlots())
                checkSlot(slot, node, ctx.layout);
            result = new XBuildClosureBodyNode(closure.getClosureSlot(), closure.getSourceSlots(),
                    closure.getTargetSlots(), closure.getExpr());
        } else if (node instanceof VarFunctionExecutable) {
            VarFunctionExecutable var = (VarFunctionExecutable) node;
            result = new XVarFunctionCallNode(node.getLocation(), displayOf(node), var.isOptional(),
                    genExpr(var.getFuncExpr(), ctx), genArgs(var.getArgs(), ctx));
        } else if (node instanceof VarExecutableFunction) {
            VarExecutableFunction var = (VarExecutableFunction) node;
            result = new XVarFunctionCallNode(node.getLocation(), displayOf(node), var.isOptional(),
                    genExpr(var.getFuncExpr(), ctx), genArgs(var.getArgs(), ctx));
        } else if (node instanceof FunctionalAdapterExecutable) {
            // truffle 无跨 JVM 约束：载荷 IEvalFunction 编译期常量直持（真实翻译）
            result = new XFunctionalAdapterNode(node.getLocation(),
                    ((FunctionalAdapterExecutable) node).getFunction());
        } else if (node instanceof LocationFunction) {
            result = new XLocationFunctionNode(node.getLocation());
        } else if (node instanceof OutputTextExecutable) {
            result = new XOutputTextNode(node.getLocation(), ((OutputTextExecutable) node).getText());
        } else if (node instanceof OutputValueExecutable) {
            result = new XOutputValueNode(node.getLocation(),
                    genExpr(((OutputValueExecutable) node).getValueExpr(), ctx));
        } else if (node instanceof OutputXmlAttrExecutable) {
            OutputXmlAttrExecutable attr = (OutputXmlAttrExecutable) node;
            result = new XOutputXmlAttrNode(node.getLocation(), attr.getName(),
                    genExpr(attr.getValueExpr(), ctx));
        } else if (node instanceof OutputXmlExtAttrsExecutable) {
            OutputXmlExtAttrsExecutable extAttrs = (OutputXmlExtAttrsExecutable) node;
            result = new XOutputXmlExtAttrsNode(node.getLocation(), displayOf(node),
                    extAttrs.getExcludeNames(), genExpr(extAttrs.getAttrsExpr(), ctx));
        } else if (node instanceof EscapeOutputExecutable) {
            EscapeOutputExecutable escape = (EscapeOutputExecutable) node;
            result = new XEscapeOutputNode(node.getLocation(), escape.getEscapeMode(),
                    genExpr(escape.getValueExpr(), ctx));
        } else if (node instanceof GenXJsonExecutable) {
            result = new XOutputSwapNode(XOutputSwapNode.Kind.GEN_XJSON, node.getLocation(), false,
                    ctx.loopDepth > 0, genExpr(((GenXJsonExecutable) node).getExecutable(), ctx));
        } else if (node instanceof CollectTextExecutable) {
            result = new XOutputSwapNode(XOutputSwapNode.Kind.COLLECT_TEXT, node.getLocation(), false,
                    ctx.loopDepth > 0, genExpr(((CollectTextExecutable) node).getBodyExpr(), ctx));
        } else if (node instanceof CollectJsonExecutable) {
            result = new XOutputSwapNode(XOutputSwapNode.Kind.COLLECT_JSON, node.getLocation(), false,
                    ctx.loopDepth > 0, genExpr(((CollectJsonExecutable) node).getBodyExpr(), ctx));
        } else if (node instanceof CollectNodeExecutable) {
            CollectNodeExecutable collect = (CollectNodeExecutable) node;
            result = new XOutputSwapNode(XOutputSwapNode.Kind.COLLECT_NODE, node.getLocation(),
                    collect.isSingleNode(), ctx.loopDepth > 0, genExpr(collect.getBodyExpr(), ctx));
        } else if (node instanceof CollectSqlExecutable) {
            result = new XOutputSwapNode(XOutputSwapNode.Kind.COLLECT_SQL, node.getLocation(), false,
                    ctx.loopDepth > 0, genExpr(((CollectSqlExecutable) node).getBodyExpr(), ctx));
        } else if (node instanceof GenNodeExecutable) {
            result = genGenNode((GenNodeExecutable) node, ctx);
        } else {
            // 支持集外节点（fail-fast；排除分区两类经矩阵反证覆盖）
            throw unsupported(node, null);
        }

        result.setSourceSection(SyntheticSources.sectionOf(node.getLocation()));
        return result;
    }

    /** 循环体翻译：词法循环深度 +1（body 内换缓冲族节点的 pending exit 分派静态标志）。 */
    private XExprNode loopBody(IExecutableExpression body, FrameCtx ctx) {
        ctx.loopDepth++;
        try {
            return genExpr(body, ctx);
        } finally {
            ctx.loopDepth--;
        }
    }

    private XExprNode genSwitch(SwitchExecutable node, FrameCtx ctx) {
        XExprNode[] tests = new XExprNode[node.getTests().length];
        XExprNode[] consequences = new XExprNode[node.getConsequences().length];
        for (int i = 0; i < tests.length; i++) {
            tests[i] = genExpr(node.getTests()[i], ctx);
            consequences[i] = genExpr(node.getConsequences()[i], ctx);
        }
        return new XSwitchNode(node.isAsExpr(), genExpr(node.getDiscriminant(), ctx), tests,
                consequences, node.getFallthroughs(),
                node.getDefaultCase() == null ? null : genExpr(node.getDefaultCase(), ctx));
    }

    private XExprNode genGenNode(GenNodeExecutable node, FrameCtx ctx) {
        GenNodeAttrExecutable[] attrExprs = node.getAttrExprs();
        String[] names = new String[attrExprs.length];
        SourceLocation[] valueLocs = new SourceLocation[attrExprs.length];
        XExprNode[] values = new XExprNode[attrExprs.length];
        for (int i = 0; i < attrExprs.length; i++) {
            names[i] = attrExprs[i].getName();
            valueLocs[i] = attrExprs[i].getValueExpr().getLocation();
            values[i] = genExpr(attrExprs[i].getValueExpr(), ctx);
        }
        Set<String> attrNames = null;
        if (node.getExtAttrs() != null) {
            attrNames = new LinkedHashSet<>();
            Collections.addAll(attrNames, names);
        }
        return new XGenNodeNode(node.getLocation(), displayOf(node), node.getTagName(),
                node.getTagNameExpr(), names, valueLocs, values,
                node.getTagNameExpr() == null ? null : genExpr(node.getTagNameExpr(), ctx),
                node.getExtAttrs() == null ? null : genExpr(node.getExtAttrs(), ctx),
                node.getExtAttrs() == null ? null : node.getExtAttrs().getLocation(),
                attrNames, ctx.loopDepth > 0,
                node.getBodyExpr() == null ? null : genExpr(node.getBodyExpr(), ctx));
    }

    private XExprNode binary(XBinaryOpNode.Op op, IExecutableExpression node, FrameCtx ctx) {
        AbstractBinaryExecutable binary = (AbstractBinaryExecutable) node;
        return new XBinaryOpNode(op, genExpr(binary.getLeft(), ctx), genExpr(binary.getRight(), ctx));
    }

    private XExprNode genBetweenOp(BetweenOpExecutable between, FrameCtx ctx) {
        return new XBetweenOpNode(between.getFilterOp(), between.isExcludeMin(), between.isExcludeMax(),
                genExpr(between.getValueExpr(), ctx), genExpr(between.getMinExpr(), ctx),
                genExpr(between.getMaxExpr(), ctx));
    }

    private XExprNode genNewList(NewListExecutable node, FrameCtx ctx) {
        ListItemExecutable[] items = node.getItems();
        boolean[] spread = new boolean[items.length];
        XExprNode[] values = new XExprNode[items.length];
        for (int i = 0; i < items.length; i++) {
            spread[i] = items[i].isSpread();
            values[i] = genExpr(items[i].getValueExpr(), ctx);
        }
        return new XNewListNode(spread, values);
    }

    private XExprNode genNewMap(NewMapExecutable node, FrameCtx ctx) {
        MapItemExecutable[] items = node.getItems();
        boolean[] spread = new boolean[items.length];
        XExprNode[] keys = new XExprNode[items.length];
        XExprNode[] values = new XExprNode[items.length];
        for (int i = 0; i < items.length; i++) {
            spread[i] = items[i].isSpread();
            keys[i] = genExpr(items[i].getKeyExpr(), ctx);
            values[i] = genExpr(items[i].getValueExpr(), ctx);
        }
        return new XNewMapNode(spread, keys, values);
    }

    private XExprNode genBindVar(BindVarExecutable node, FrameCtx ctx) {
        int[] slots = node.getSlots();
        for (int slot : slots) {
            checkSlot(slot, node, ctx.layout);
        }
        return new XBindVarNode(slots, node.getVars(), genExpr(node.getExpr(), ctx));
    }

    private XExprNode genArrayBinding(ArrayBindingAssignExecutable node, FrameCtx ctx) {
        AssignIdentifier[] bindings = node.getElementBindings();
        XBindingAssign[] elements = new XBindingAssign[bindings.length];
        for (int i = 0; i < bindings.length; i++) {
            elements[i] = bindingAssign(bindings[i], node, ctx);
        }
        XBindingAssign rest = node.getRestBinding() == null ? null
                : bindingAssign(node.getRestBinding(), node, ctx);
        return new XArrayBindingNode(node.getLocation(), displayOf(node),
                genExpr(node.getExpr(), ctx), elements, rest);
    }

    private XExprNode genObjectBinding(ObjectBindingAssignExecutable node, FrameCtx ctx) {
        PropBinding[] propBindings = node.getPropBindings();
        String[] keys = new String[propBindings.length];
        XExprNode[] initializers = new XExprNode[propBindings.length];
        XBindingAssign[] bindings = new XBindingAssign[propBindings.length];
        Set<String> boundKeys = new LinkedHashSet<>();
        for (int i = 0; i < propBindings.length; i++) {
            keys[i] = propBindings[i].getKey();
            boundKeys.add(propBindings[i].getKey());
            initializers[i] = propBindings[i].getInitializer() == null ? null
                    : genExpr(propBindings[i].getInitializer(), ctx);
            bindings[i] = bindingAssign(propBindings[i], node, ctx);
        }
        XBindingAssign rest = node.getRestBinding() == null ? null
                : bindingAssign(node.getRestBinding(), node, ctx);
        return new XObjectBindingNode(node.getLocation(), displayOf(node), genExpr(node.getExpr(), ctx),
                keys, initializers, bindings, boundKeys, rest);
    }

    /**
     * AssignIdentifier 语义：slot>=0 时 useRef 走引用 cell 写、否则直写 slot（slot 化）；
     * slot<0 按 scope 名写入（Q3 残余路径）。数组解构元素不应用默认初始化器（解释器现状）。
     */
    private XBindingAssign bindingAssign(AssignIdentifier binding, IExecutableExpression node,
                                         FrameCtx ctx) {
        int slot = binding.getVarSlot();
        if (slot >= 0)
            checkSlot(slot, node, ctx.layout);
        return new XBindingAssign(binding.getLocation(), slot, binding.getVarName(), binding.isUseRef());
    }

    private XExprNode[] genArgs(IExecutableExpression[] argExprs, FrameCtx ctx) {
        if (argExprs == null || argExprs.length == 0)
            return new XExprNode[0];
        XExprNode[] nodes = new XExprNode[argExprs.length];
        for (int i = 0; i < argExprs.length; i++) {
            nodes[i] = genExpr(argExprs[i], ctx);
        }
        return nodes;
    }

    private int checkSlot(int slot, IExecutableExpression node, FrameLayout layout) {
        if (slot < 0 || slot >= layout.getSlotCount())
            throw unsupported(node, "slot read/write outside program entry frame: slot=" + slot);
        return slot;
    }

    private static int indexOfSlotName(FrameLayout layout, String varName) {
        for (int i = 0; i < layout.getSlotCount(); i++) {
            if (varName.equals(layout.getSlot(i).getName()))
                return i;
        }
        return -1;
    }

    private static String displayOf(IExecutableExpression node) {
        StringBuilder sb = new StringBuilder();
        node.display(sb);
        return sb.toString();
    }

    static NopEvalException unsupported(IExecutableExpression node, String detail) {
        SourceLocation loc = node.getLocation();
        NopEvalException err = new NopEvalException(ERR_EXEC_TRANSLATE_UNSUPPORTED_NODE, null);
        err.param(ARG_CLASS_NAME, node.getClass().getName()).param(ARG_LOCATION, String.valueOf(loc));
        if (detail != null)
            err.param("detail", detail);
        if (loc != null)
            err.loc(loc);
        return err;
    }
}
