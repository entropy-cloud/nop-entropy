package io.nop.xlang.truffle.translate;

import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.xlang.exec.AbstractBinaryExecutable;
import io.nop.xlang.exec.AbstractObjFunctionExecutable;
import io.nop.xlang.exec.AndExecutable;
import io.nop.xlang.exec.CallFuncExecutable;
import io.nop.xlang.exec.CompareOpExecutable;
import io.nop.xlang.exec.DivideExecutable;
import io.nop.xlang.exec.EqExecutable;
import io.nop.xlang.exec.FunctionExecutable;
import io.nop.xlang.exec.GeExecutable;
import io.nop.xlang.exec.GtExecutable;
import io.nop.xlang.exec.GuardNotNullExecutable;
import io.nop.xlang.exec.ISeqExecutable;
import io.nop.xlang.exec.LeExecutable;
import io.nop.xlang.exec.LiteralExecutable;
import io.nop.xlang.exec.LtExecutable;
import io.nop.xlang.exec.MinusExecutable;
import io.nop.xlang.exec.MultiplyExecutable;
import io.nop.xlang.exec.NeExecutable;
import io.nop.xlang.exec.NotExecutable;
import io.nop.xlang.exec.NullExecutable;
import io.nop.xlang.exec.ObjFunctionExecutable;
import io.nop.xlang.exec.OrExecutable;
import io.nop.xlang.exec.PlusExecutable;
import io.nop.xlang.exec.ReturnNullExecutable;
import io.nop.xlang.exec.SlotAssignExecutable;
import io.nop.xlang.exec.SlotIdentifierExecutable;
import io.nop.xlang.exec.StaticFunctionExecutable;
import io.nop.xlang.exec.StrictEqExecutable;
import io.nop.xlang.exec.StrictNeExecutable;
import io.nop.xlang.exec.XLangSemantics;
import io.nop.xlang.truffle.frame.FrameLayout;
import io.nop.xlang.truffle.frame.FrameLayoutMapper;
import io.nop.xlang.truffle.lang.XLangLanguage;
import io.nop.xlang.truffle.nodes.XBinaryOpNode;
import io.nop.xlang.truffle.nodes.XCompareOpNode;
import io.nop.xlang.truffle.nodes.XExprNode;
import io.nop.xlang.truffle.nodes.XGlobalFuncNode;
import io.nop.xlang.truffle.nodes.XGuardNotNullNode;
import io.nop.xlang.truffle.nodes.XLiteralNode;
import io.nop.xlang.truffle.nodes.XLogicShortCircuitNode;
import io.nop.xlang.truffle.nodes.XNotNode;
import io.nop.xlang.truffle.nodes.XNullNode;
import io.nop.xlang.truffle.nodes.XObjMethodNode;
import io.nop.xlang.truffle.nodes.XReturnNullNode;
import io.nop.xlang.truffle.nodes.XLangRootNode;
import io.nop.xlang.truffle.nodes.XSeqNode;
import io.nop.xlang.truffle.nodes.XSlotReadNode;
import io.nop.xlang.truffle.nodes.XSlotWriteNode;
import io.nop.xlang.truffle.nodes.XStaticMethodNode;

import static io.nop.xlang.XLangErrors.ARG_CLASS_NAME;
import static io.nop.xlang.XLangErrors.ARG_LOCATION;
import static io.nop.xlang.XLangErrors.ERR_EXEC_TRANSLATE_UNSUPPORTED_NODE;

/**
 * Executable 树 → Truffle AST 的纯函数翻译器（设计 truffle 02 §四/§七：树翻译而非适配包装）。
 *
 * <p>子集（与 I1 corpus v1 / I2 java 转译器同口径）：字面量 / slot 标识符 / 算术 / 逻辑 /
 * 比较 / 简单方法调用（宿主反射分派族）+ 结构性载体（CallFunc 程序入口 / Block / Seq /
 * SlotAssign / ReturnNull / GuardNotNull / Null）。
 *
 * <p>硬保证：fail-fast——子集外节点翻译失败（报节点类名 + SourceLocation，禁止部分翻译）；
 * 语义敏感操作统一调用 {@link XLangSemantics} 共享 helper（D3）；可抛错点携带合成
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

    private XExprNode genExpr(IExecutableExpression node, FrameLayout layout) {
        if (node == null)
            return XNullNode.INSTANCE;

        XExprNode result;
        if (node instanceof LiteralExecutable) {
            result = new XLiteralNode(((LiteralExecutable) node).getValue());
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
        } else if (node instanceof CompareOpExecutable) {
            CompareOpExecutable cmp = (CompareOpExecutable) node;
            result = new XCompareOpNode(cmp.getFilterOp(), genExpr(cmp.getLeft(), layout),
                    genExpr(cmp.getRight(), layout));
        } else if (node instanceof ObjFunctionExecutable) {
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
        } else if (node instanceof ISeqExecutable) {
            ISeqExecutable seq = (ISeqExecutable) node;
            result = new XSeqNode(genArgs(seq.getExprs(), layout), seq.isBlockStatement());
        } else if (node instanceof ReturnNullExecutable) {
            result = new XReturnNullNode(genExpr(((ReturnNullExecutable) node).getExecutable(), layout));
        } else {
            // CallFunc 族非根位置 = 局部函数调用；其余节点类 = 子集外（ExitMode 控制流族亦在此 fail-fast，归 I7）
            throw unsupported(node, null);
        }

        result.setSourceSection(SyntheticSources.sectionOf(node.getLocation()));
        return result;
    }

    private XExprNode binary(XBinaryOpNode.Op op, IExecutableExpression node, FrameLayout layout) {
        AbstractBinaryExecutable binary = (AbstractBinaryExecutable) node;
        return new XBinaryOpNode(op, genExpr(binary.getLeft(), layout), genExpr(binary.getRight(), layout));
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
