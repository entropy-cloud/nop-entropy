package io.nop.xlang.truffle.translate;

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
import io.nop.xlang.exec.TypeOfExecutable;
import io.nop.xlang.exec.VarStatusExecutable;
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
import io.nop.xlang.truffle.nodes.XCastNode;
import io.nop.xlang.truffle.nodes.XCloneLiteralNode;
import io.nop.xlang.truffle.nodes.XCompareOpNode;
import io.nop.xlang.truffle.nodes.XConcatNode;
import io.nop.xlang.truffle.nodes.XConvertNode;
import io.nop.xlang.truffle.nodes.XConvertWithDefaultNode;
import io.nop.xlang.truffle.nodes.XDebugIdentifierNode;
import io.nop.xlang.truffle.nodes.XDebugNode;
import io.nop.xlang.truffle.nodes.XExprNode;
import io.nop.xlang.truffle.nodes.XGetAttrNode;
import io.nop.xlang.truffle.nodes.XGetPropertyNode;
import io.nop.xlang.truffle.nodes.XGetterGetPropertyNode;
import io.nop.xlang.truffle.nodes.XGlobalFuncNode;
import io.nop.xlang.truffle.nodes.XGlobalVarReadNode;
import io.nop.xlang.truffle.nodes.XGuardNotNullNode;
import io.nop.xlang.truffle.nodes.XGuardNotEmptyNode;
import io.nop.xlang.truffle.nodes.XInstanceOfNode;
import io.nop.xlang.truffle.nodes.XLangRootNode;
import io.nop.xlang.truffle.nodes.XLiteralNode;
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
import io.nop.xlang.truffle.nodes.XPropInNode;
import io.nop.xlang.truffle.nodes.XRangeNode;
import io.nop.xlang.truffle.nodes.XRefReadNode;
import io.nop.xlang.truffle.nodes.XRefRenewNode;
import io.nop.xlang.truffle.nodes.XRefSelfAssignNode;
import io.nop.xlang.truffle.nodes.XRefSelfIncNode;
import io.nop.xlang.truffle.nodes.XRefSlotInitNode;
import io.nop.xlang.truffle.nodes.XRefWriteNode;
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
import io.nop.xlang.truffle.nodes.XUnaryOpNode;
import io.nop.xlang.truffle.nodes.XVarStatusNode;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import static io.nop.xlang.XLangErrors.ARG_CLASS_NAME;
import static io.nop.xlang.XLangErrors.ARG_LOCATION;
import static io.nop.xlang.XLangErrors.ERR_EXEC_TRANSLATE_UNSUPPORTED_NODE;

/**
 * Executable 树 → Truffle AST 的纯函数翻译器（设计 truffle 02 §四/§七：树翻译而非适配包装）。
 *
 * <p>支持集（与 java 转译器/I1 corpus 同口径）：I2 表达式子集 + 覆盖 A 五族（作用域链访问
 * （含引用族）/类型操作/对象集合构造访问/绑定守卫调试/slot 写）+ 并入残余算子族 =
 * {@code ExecNodeBaseline.registeredTarget()}（87 类）。作用域链访问族按设计 Q3 落地：
 * 翻译期 slot 化优先（引用族/slot 写族走帧 slot），无法 slot 化的按名访问翻译为 context
 * 持有的 scope 链查找节点（经 {@code XLangContext} 求值窗口存取）。
 *
 * <p>硬保证：fail-fast——支持集外节点翻译失败（报节点类名 + SourceLocation，禁止部分翻译）；
 * 语义敏感操作统一调用 {@link XLangSemantics} 共享 helper（D3，与解释器/java 侧同一实现来源，
 * 禁止在翻译节点内重写语义等价实现）；不新增语义特化 fast-path（归 I7）；可抛错点携带合成
 * SourceSection（回映射源位置）；每编译单元一个 RootNode + CallTarget（JIT 编译粒度）。
 */
public final class ExecToTruffleTranslator {

    public TranslatedUnit translate(String sourceKey, long treeFingerprint, IExecutableExpression tree,
                                    XLangLanguage language) {
        FrameLayout layout = FrameLayoutMapper.map(tree);
        XExprNode body;
        if (tree instanceof CallFuncExecutable) {
            CallFuncExecutable entry = (CallFuncExecutable) tree;
            if (entry.getArgExprs() != null && entry.getArgExprs().length > 0)
                throw unsupported(entry, "program entry with declared arguments");
            body = genExpr(entry.getBodyExpr(), layout);
        } else {
            body = genExpr(tree, layout);
        }
        XLangRootNode root = new XLangRootNode(language, layout, sourceKey, treeFingerprint, tree, body);
        return new TranslatedUnit(sourceKey, treeFingerprint, tree, root, root.getCallTarget());
    }

    // ------------------------------------------------------------------
    // 支持集可编程枚举（矩阵注册证据之一；分派为 instanceof 链，本集合与分派分支一一对应，
    // 一致性由矩阵测试逐类真实翻译验证——非清单自证）
    // ------------------------------------------------------------------

    /**
     * 翻译器已注册的具体节点类（I2 子集 + 覆盖 A 五族 + 并入残余 =
     * {@code ExecNodeBaseline.registeredTarget()}，同基线同口径单一事实源）。
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
    // 表达式分派
    // ------------------------------------------------------------------

    private XExprNode genExpr(IExecutableExpression node, FrameLayout layout) {
        if (node == null)
            return XNullNode.INSTANCE;

        XExprNode result;
        if (node instanceof LiteralExecutable) {
            result = new XLiteralNode(((LiteralExecutable) node).getValue());
        } else if (node instanceof CloneLiteralExecutable) {
            result = new XCloneLiteralNode(((CloneLiteralExecutable) node).getValue());
        } else if (node instanceof NullExecutable) {
            result = XNullNode.INSTANCE;
        } else if (node instanceof SlotIdentifierExecutable) {
            SlotIdentifierExecutable slot = (SlotIdentifierExecutable) node;
            result = new XSlotReadNode(checkSlot(slot.getSlot(), node, layout), slot.getId());
        } else if (node instanceof SlotAssignExecutable) {
            SlotAssignExecutable assign = (SlotAssignExecutable) node;
            int slotIndex = checkSlot(assign.getSlot(), node, layout);
            XExprNode value = genExpr(assign.getExpr(), layout);
            result = new XSlotWriteNode(slotIndex, layout.getSlot(slotIndex).getKind(),
                    assign.getVarName(), value);
        } else if (node instanceof PlusExecutable) {
            result = binary(XBinaryOpNode.Op.PLUS, node, layout);
        } else if (node instanceof MinusExecutable) {
            result = binary(XBinaryOpNode.Op.MINUS, node, layout);
        } else if (node instanceof MultiplyExecutable) {
            result = binary(XBinaryOpNode.Op.MULTIPLY, node, layout);
        } else if (node instanceof DivideExecutable) {
            result = binary(XBinaryOpNode.Op.DIVIDE, node, layout);
        } else if (node instanceof EqExecutable || node instanceof StrictEqExecutable) {
            result = binary(XBinaryOpNode.Op.EQ, node, layout);
        } else if (node instanceof NeExecutable || node instanceof StrictNeExecutable) {
            result = binary(XBinaryOpNode.Op.NE, node, layout);
        } else if (node instanceof GtExecutable) {
            result = binary(XBinaryOpNode.Op.GT, node, layout);
        } else if (node instanceof GeExecutable) {
            result = binary(XBinaryOpNode.Op.GE, node, layout);
        } else if (node instanceof LtExecutable) {
            result = binary(XBinaryOpNode.Op.LT, node, layout);
        } else if (node instanceof LeExecutable) {
            result = binary(XBinaryOpNode.Op.LE, node, layout);
        } else if (node instanceof BinaryExecutable) {
            BinaryExecutable binary = (BinaryExecutable) node;
            result = new XBinaryEvalNode(binary.getOperator(),
                    genExpr(binary.getLeft(), layout), genExpr(binary.getRight(), layout));
        } else if (node instanceof AndExecutable) {
            AbstractBinaryExecutable binary = (AbstractBinaryExecutable) node;
            result = new XLogicShortCircuitNode(true, genExpr(binary.getLeft(), layout),
                    genExpr(binary.getRight(), layout));
        } else if (node instanceof OrExecutable) {
            AbstractBinaryExecutable binary = (AbstractBinaryExecutable) node;
            result = new XLogicShortCircuitNode(false, genExpr(binary.getLeft(), layout),
                    genExpr(binary.getRight(), layout));
        } else if (node instanceof NotExecutable) {
            result = new XNotNode(genExpr(((NotExecutable) node).getExpr(), layout));
        } else if (node instanceof NegExecutable) {
            result = new XUnaryOpNode(XUnaryOpNode.Op.NEG, genExpr(((NegExecutable) node).getExpr(), layout));
        } else if (node instanceof BitNotExecutable) {
            result = new XUnaryOpNode(XUnaryOpNode.Op.BIT_NOT, genExpr(((BitNotExecutable) node).getExpr(), layout));
        } else if (node instanceof TypeOfExecutable) {
            result = new XUnaryOpNode(XUnaryOpNode.Op.TYPE_OF, genExpr(((TypeOfExecutable) node).getExpr(), layout));
        } else if (node instanceof NullCoalesceExecutable) {
            AbstractBinaryExecutable binary = (AbstractBinaryExecutable) node;
            result = new XNullCoalesceNode(genExpr(binary.getLeft(), layout),
                    genExpr(binary.getRight(), layout));
        } else if (node instanceof EqNullExecutable || node instanceof StrictEqNullExecutable) {
            IExecutableExpression inner = node instanceof EqNullExecutable
                    ? ((EqNullExecutable) node).getExpr() : ((StrictEqNullExecutable) node).getExpr();
            result = new XNullCheckNode(XNullCheckNode.Mode.EQ_NULL, genExpr(inner, layout));
        } else if (node instanceof NeNullExecutable || node instanceof StrictNeNullExecutable) {
            IExecutableExpression inner = node instanceof NeNullExecutable
                    ? ((NeNullExecutable) node).getExpr() : ((StrictNeNullExecutable) node).getExpr();
            result = new XNullCheckNode(XNullCheckNode.Mode.NE_NULL, genExpr(inner, layout));
        } else if (node instanceof CompareOpExecutable) {
            CompareOpExecutable cmp = (CompareOpExecutable) node;
            result = new XCompareOpNode(cmp.getFilterOp(), genExpr(cmp.getLeft(), layout),
                    genExpr(cmp.getRight(), layout));
        } else if (node instanceof BetweenOpExecutable) {
            result = genBetweenOp((BetweenOpExecutable) node, layout);
        } else if (node instanceof AssertOpExecutable) {
            AssertOpExecutable assertOp = (AssertOpExecutable) node;
            result = new XAssertOpNode(assertOp.getFilterOp(),
                    genExpr(assertOp.getValueExpr(), layout));
        } else if (node instanceof RangeExecutable) {
            RangeExecutable range = (RangeExecutable) node;
            result = new XRangeNode(node.getLocation(), displayOf(node), genExpr(range.getBeginExpr(), layout),
                    genExpr(range.getEndExpr(), layout), genExpr(range.getStepExpr(), layout));
        } else if (node instanceof PropInExecutable) {
            AbstractBinaryExecutable binary = (AbstractBinaryExecutable) node;
            result = new XPropInNode(genExpr(binary.getLeft(), layout), genExpr(binary.getRight(), layout));
        } else if (node instanceof ConcatExecutable) {
            result = new XConcatNode(genArgs(((ConcatExecutable) node).getExprs(), layout));
        } else if (node instanceof AbstractObjFunctionExecutable) {
            AbstractObjFunctionExecutable fn = (AbstractObjFunctionExecutable) node;
            result = new XObjMethodNode(node.getLocation(), displayOf(node), fn.getFuncName(),
                    genExpr(fn.getObjExpr(), layout), genArgs(fn.getArgs(), layout));
        } else if (node instanceof FunctionExecutable) {
            FunctionExecutable fn = (FunctionExecutable) node;
            result = new XGlobalFuncNode(node.getLocation(), displayOf(node), fn.getFuncName(),
                    genArgs(fn.getArgs(), layout));
        } else if (node instanceof StaticFunctionExecutable) {
            StaticFunctionExecutable fn = (StaticFunctionExecutable) node;
            result = new XStaticMethodNode(node.getLocation(), displayOf(node), fn.getClassName(),
                    fn.getFuncName(), fn.isOptional(), genArgs(fn.getArgExprs(), layout));
        } else if (node instanceof GuardNotNullExecutable) {
            result = new XGuardNotNullNode(node.getLocation(), displayOf(node),
                    genExpr(((GuardNotNullExecutable) node).getExpr(), layout));
        } else if (node instanceof GuardNotEmptyExecutable) {
            GuardNotEmptyExecutable guard = (GuardNotEmptyExecutable) node;
            result = new XGuardNotEmptyNode(node.getLocation(), displayOf(node),
                    String.valueOf(guard.getTarget()), genExpr(guard.getExpr(), layout));
        } else if (node instanceof ISeqExecutable) {
            ISeqExecutable seq = (ISeqExecutable) node;
            result = new XSeqNode(genArgs(seq.getExprs(), layout), seq.isBlockStatement());
        } else if (node instanceof ReturnNullExecutable) {
            result = new XReturnNullNode(genExpr(((ReturnNullExecutable) node).getExecutable(), layout));
        } else if (node instanceof ScopeIdentifierExecutable) {
            result = new XScopeReadNode(node.getLocation(), displayOf(node),
                    ((ScopeIdentifierExecutable) node).getVarName());
        } else if (node instanceof GlobalVarExecutable) {
            result = new XGlobalVarReadNode(node.getLocation(), displayOf(node),
                    ((GlobalVarExecutable) node).getVarName());
        } else if (node instanceof ScopeAssignExecutable) {
            ScopeAssignExecutable assign = (ScopeAssignExecutable) node;
            result = new XScopeWriteNode(node.getLocation(), assign.getVarName(),
                    genExpr(assign.getExpr(), layout));
        } else if (node instanceof ScopeSelfAssignExecutable) {
            ScopeSelfAssignExecutable self = (ScopeSelfAssignExecutable) node;
            result = new XScopeSelfAssignNode(node.getLocation(), displayOf(node), self.getVarName(),
                    self.getOperator(), genExpr(self.getExpr(), layout));
        } else if (node instanceof ScopeSelfIncExecutable) {
            result = new XScopeSelfIncNode(node.getLocation(),
                    ((ScopeSelfIncExecutable) node).getVarName(), 1);
        } else if (node instanceof ScopeSelfDecExecutable) {
            result = new XScopeSelfIncNode(node.getLocation(),
                    ((ScopeSelfDecExecutable) node).getVarName(), -1);
        } else if (node instanceof ReferenceIdentifierExecutable) {
            ReferenceIdentifierExecutable ref = (ReferenceIdentifierExecutable) node;
            result = new XRefReadNode(node.getLocation(), displayOf(node), ref.getId(),
                    checkSlot(ref.getSlot(), node, layout));
        } else if (node instanceof ReferenceAssignExecutable) {
            ReferenceAssignExecutable assign = (ReferenceAssignExecutable) node;
            result = new XRefWriteNode(checkSlot(assign.getSlot(), node, layout),
                    genExpr(assign.getExpr(), layout));
        } else if (node instanceof ReferenceSelfAssignExecutable) {
            ReferenceSelfAssignExecutable self = (ReferenceSelfAssignExecutable) node;
            result = new XRefSelfAssignNode(node.getLocation(), displayOf(node), self.getVarName(),
                    checkSlot(self.getSlot(), node, layout), self.getOperator(),
                    genExpr(self.getExpr(), layout));
        } else if (node instanceof ReferenceSelfIncExecutable) {
            ReferenceSelfIncExecutable self = (ReferenceSelfIncExecutable) node;
            result = new XRefSelfIncNode(node.getLocation(), displayOf(node), self.getVarName(),
                    checkSlot(self.getSlot(), node, layout), 1);
        } else if (node instanceof ReferenceSelfDecExecutable) {
            ReferenceSelfDecExecutable self = (ReferenceSelfDecExecutable) node;
            result = new XRefSelfIncNode(node.getLocation(), displayOf(node), self.getVarName(),
                    checkSlot(self.getSlot(), node, layout), -1);
        } else if (node instanceof RenewReferenceExecutable) {
            result = new XRefRenewNode(checkSlot(((RenewReferenceExecutable) node).getSlot(), node, layout));
        } else if (node instanceof CastExecutable) {
            CastExecutable cast = (CastExecutable) node;
            result = new XCastNode(node.getLocation(), displayOf(node), cast.getClazz().getName(),
                    genExpr(cast.getExpr(), layout));
        } else if (node instanceof ConvertExecutable) {
            ConvertExecutable convert = (ConvertExecutable) node;
            result = new XConvertNode(node.getLocation(), displayOf(node), convert.getFuncName(),
                    genExpr(convert.getExpr(), layout));
        } else if (node instanceof ConvertWithDefaultExecutable) {
            ConvertWithDefaultExecutable convert = (ConvertWithDefaultExecutable) node;
            result = new XConvertWithDefaultNode(node.getLocation(), displayOf(node), convert.getFuncName(),
                    genExpr(convert.getExpr(), layout), genExpr(convert.getDefaultExpr(), layout));
        } else if (node instanceof InstanceOfExecutable) {
            InstanceOfExecutable inst = (InstanceOfExecutable) node;
            result = new XInstanceOfNode(inst.getType().getRawClass().getName(),
                    genExpr(inst.getExpr(), layout));
        } else if (node instanceof MakePropertyExecutable) {
            MakePropertyExecutable make = (MakePropertyExecutable) node;
            result = new XMakePropertyNode(node.getLocation(), displayOf(node),
                    displayOf(make.getObjExpr()), make.getPropName(),
                    genExpr(make.getObjExpr(), layout));
        } else if (node instanceof GetPropertyExecutable) {
            GetPropertyExecutable get = (GetPropertyExecutable) node;
            result = new XGetPropertyNode(node.getLocation(), displayOf(node), displayOf(get.getObjExpr()),
                    get.isOptional(), get.getPropName(), genExpr(get.getObjExpr(), layout));
        } else if (node instanceof GetterGetPropertyExecutable) {
            GetterGetPropertyExecutable get = (GetterGetPropertyExecutable) node;
            result = new XGetterGetPropertyNode(node.getLocation(), displayOf(node), get.getPropName(),
                    genExpr(get.getObjExpr(), layout));
        } else if (node instanceof StaticGetterGetPropertyExecutable) {
            StaticGetterGetPropertyExecutable get = (StaticGetterGetPropertyExecutable) node;
            result = new XStaticGetPropertyNode(node.getLocation(), displayOf(node),
                    get.getClassName(), get.getPropName());
        } else if (node instanceof SetPropertyExecutable) {
            SetPropertyExecutable set = (SetPropertyExecutable) node;
            result = new XSetPropertyNode(node.getLocation(), displayOf(node), set.getPropName(),
                    genExpr(set.getObjExpr(), layout), genExpr(set.getValueExpr(), layout));
        } else if (node instanceof SetterSetPropertyExecutable) {
            SetterSetPropertyExecutable set = (SetterSetPropertyExecutable) node;
            result = new XSetterSetPropertyNode(node.getLocation(), displayOf(node), set.getPropName(),
                    genExpr(set.getObjExpr(), layout), genExpr(set.getValueExpr(), layout));
        } else if (node instanceof SelfAssignPropertyExecutable) {
            SelfAssignPropertyExecutable self = (SelfAssignPropertyExecutable) node;
            result = new XSelfAssignPropertyNode(node.getLocation(), displayOf(node), self.getPropName(),
                    self.getOperator(), genExpr(self.getObjExpr(), layout),
                    genExpr(self.getValueExpr(), layout));
        } else if (node instanceof GetAttrExecutable) {
            GetAttrExecutable get = (GetAttrExecutable) node;
            result = new XGetAttrNode(node.getLocation(), displayOf(node), displayOf(get.getObjExpr()),
                    displayOf(get.getAttrExpr()), get.isOptional(),
                    genExpr(get.getObjExpr(), layout), genExpr(get.getAttrExpr(), layout));
        } else if (node instanceof SetAttrExecutable) {
            SetAttrExecutable set = (SetAttrExecutable) node;
            result = new XSetAttrNode(node.getLocation(), displayOf(node), displayOf(set.getAttrExpr()),
                    genExpr(set.getObjExpr(), layout), genExpr(set.getAttrExpr(), layout),
                    genExpr(set.getValueExpr(), layout));
        } else if (node instanceof SelfAssignAttrExecutable) {
            SelfAssignAttrExecutable self = (SelfAssignAttrExecutable) node;
            result = new XSelfAssignAttrNode(node.getLocation(), displayOf(node),
                    displayOf(self.getAttrExpr()), self.getOperator(),
                    genExpr(self.getObjExpr(), layout), genExpr(self.getAttrExpr(), layout),
                    genExpr(self.getValueExpr(), layout));
        } else if (node instanceof NewObjectExecutable) {
            NewObjectExecutable newExpr = (NewObjectExecutable) node;
            result = new XNewObjectNode(node.getLocation(), displayOf(node), newExpr.getClassModel(),
                    genArgs(newExpr.getArgExprs(), layout));
        } else if (node instanceof NewListExecutable) {
            result = genNewList((NewListExecutable) node, layout);
        } else if (node instanceof NewMapExecutable) {
            result = genNewMap((NewMapExecutable) node, layout);
        } else if (node instanceof BindVarExecutable) {
            result = genBindVar((BindVarExecutable) node, layout);
        } else if (node instanceof ArrayBindingAssignExecutable) {
            result = genArrayBinding((ArrayBindingAssignExecutable) node, layout);
        } else if (node instanceof ObjectBindingAssignExecutable) {
            result = genObjectBinding((ObjectBindingAssignExecutable) node, layout);
        } else if (node instanceof InitRefSlotExecutable) {
            result = new XRefSlotInitNode(checkSlot(((InitRefSlotExecutable) node).getSlot(), node, layout),
                    XRefSlotInitNode.Mode.INIT);
        } else if (node instanceof EnhanceRefSlotExecutable) {
            result = new XRefSlotInitNode(checkSlot(((EnhanceRefSlotExecutable) node).getSlot(), node, layout),
                    XRefSlotInitNode.Mode.ENHANCE);
        } else if (node instanceof DebugExecutable) {
            DebugExecutable debug = (DebugExecutable) node;
            result = new XDebugNode(node.getLocation(), displayOf(debug.getValueExpr()),
                    genExpr(debug.getValueExpr(), layout), genExpr(debug.getPrefixExpr(), layout));
        } else if (node instanceof DebugIdentifierExecutable) {
            DebugIdentifierExecutable identifier = (DebugIdentifierExecutable) node;
            result = new XDebugIdentifierNode(indexOfSlotName(layout, identifier.getVarName()),
                    identifier.getVarName());
        } else if (node instanceof VarStatusExecutable) {
            VarStatusExecutable varStatus = (VarStatusExecutable) node;
            result = new XVarStatusNode(node.getLocation(), displayOf(node),
                    displayOf(varStatus.getItemsExpr()),
                    checkSlot(varStatus.getVarStatusSlot(), node, layout),
                    genExpr(varStatus.getItemsExpr(), layout));
        } else if (node instanceof SelfAssignExecutable) {
            SelfAssignExecutable self = (SelfAssignExecutable) node;
            result = new XSlotSelfAssignNode(node.getLocation(), displayOf(node),
                    checkSlot(self.getSlot(), node, layout), self.getOperator(),
                    genExpr(self.getExpr(), layout));
        } else if (node instanceof SelfIncExecutable) {
            result = new XSlotSelfIncNode(
                    checkSlot(((SelfIncExecutable) node).getSlot(), node, layout), 1);
        } else if (node instanceof SelfDecExecutable) {
            result = new XSlotSelfIncNode(
                    checkSlot(((SelfDecExecutable) node).getSlot(), node, layout), -1);
        } else {
            // CallFunc 族非根位置 = 局部函数调用；其余节点类 = 支持集外（ExitMode 控制流族等 B 族亦在此 fail-fast，归 I7）
            throw unsupported(node, null);
        }

        result.setSourceSection(SyntheticSources.sectionOf(node.getLocation()));
        return result;
    }

    private XExprNode binary(XBinaryOpNode.Op op, IExecutableExpression node, FrameLayout layout) {
        AbstractBinaryExecutable binary = (AbstractBinaryExecutable) node;
        return new XBinaryOpNode(op, genExpr(binary.getLeft(), layout), genExpr(binary.getRight(), layout));
    }

    private XExprNode genBetweenOp(BetweenOpExecutable between, FrameLayout layout) {
        return new XBetweenOpNode(between.getFilterOp(), between.isExcludeMin(), between.isExcludeMax(),
                genExpr(between.getValueExpr(), layout), genExpr(between.getMinExpr(), layout),
                genExpr(between.getMaxExpr(), layout));
    }

    private XExprNode genNewList(NewListExecutable node, FrameLayout layout) {
        ListItemExecutable[] items = node.getItems();
        boolean[] spread = new boolean[items.length];
        XExprNode[] values = new XExprNode[items.length];
        for (int i = 0; i < items.length; i++) {
            spread[i] = items[i].isSpread();
            values[i] = genExpr(items[i].getValueExpr(), layout);
        }
        return new XNewListNode(spread, values);
    }

    private XExprNode genNewMap(NewMapExecutable node, FrameLayout layout) {
        MapItemExecutable[] items = node.getItems();
        boolean[] spread = new boolean[items.length];
        XExprNode[] keys = new XExprNode[items.length];
        XExprNode[] values = new XExprNode[items.length];
        for (int i = 0; i < items.length; i++) {
            spread[i] = items[i].isSpread();
            keys[i] = genExpr(items[i].getKeyExpr(), layout);
            values[i] = genExpr(items[i].getValueExpr(), layout);
        }
        return new XNewMapNode(spread, keys, values);
    }

    private XExprNode genBindVar(BindVarExecutable node, FrameLayout layout) {
        int[] slots = node.getSlots();
        for (int slot : slots) {
            checkSlot(slot, node, layout);
        }
        return new XBindVarNode(slots, node.getVars(), genExpr(node.getExpr(), layout));
    }

    private XExprNode genArrayBinding(ArrayBindingAssignExecutable node, FrameLayout layout) {
        AssignIdentifier[] bindings = node.getElementBindings();
        XBindingAssign[] elements = new XBindingAssign[bindings.length];
        for (int i = 0; i < bindings.length; i++) {
            elements[i] = bindingAssign(bindings[i], node, layout);
        }
        XBindingAssign rest = node.getRestBinding() == null ? null
                : bindingAssign(node.getRestBinding(), node, layout);
        return new XArrayBindingNode(node.getLocation(), displayOf(node),
                genExpr(node.getExpr(), layout), elements, rest);
    }

    private XExprNode genObjectBinding(ObjectBindingAssignExecutable node, FrameLayout layout) {
        PropBinding[] propBindings = node.getPropBindings();
        String[] keys = new String[propBindings.length];
        XExprNode[] initializers = new XExprNode[propBindings.length];
        XBindingAssign[] bindings = new XBindingAssign[propBindings.length];
        Set<String> boundKeys = new LinkedHashSet<>();
        for (int i = 0; i < propBindings.length; i++) {
            keys[i] = propBindings[i].getKey();
            boundKeys.add(propBindings[i].getKey());
            initializers[i] = propBindings[i].getInitializer() == null ? null
                    : genExpr(propBindings[i].getInitializer(), layout);
            bindings[i] = bindingAssign(propBindings[i], node, layout);
        }
        XBindingAssign rest = node.getRestBinding() == null ? null
                : bindingAssign(node.getRestBinding(), node, layout);
        return new XObjectBindingNode(node.getLocation(), displayOf(node), genExpr(node.getExpr(), layout),
                keys, initializers, bindings, boundKeys, rest);
    }

    /**
     * AssignIdentifier 语义：slot>=0 时 useRef 走引用 cell 写、否则直写 slot（slot 化）；
     * slot<0 按 scope 名写入（Q3 残余路径）。数组解构元素不应用默认初始化器（解释器现状）。
     */
    private XBindingAssign bindingAssign(AssignIdentifier binding, IExecutableExpression node,
                                         FrameLayout layout) {
        int slot = binding.getVarSlot();
        if (slot >= 0)
            checkSlot(slot, node, layout);
        return new XBindingAssign(binding.getLocation(), slot, binding.getVarName(), binding.isUseRef());
    }

    private XExprNode[] genArgs(IExecutableExpression[] argExprs, FrameLayout layout) {
        if (argExprs == null || argExprs.length == 0)
            return new XExprNode[0];
        XExprNode[] nodes = new XExprNode[argExprs.length];
        for (int i = 0; i < argExprs.length; i++) {
            nodes[i] = genExpr(argExprs[i], layout);
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
