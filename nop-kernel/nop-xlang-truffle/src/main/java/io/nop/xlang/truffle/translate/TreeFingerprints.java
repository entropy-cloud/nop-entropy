package io.nop.xlang.truffle.translate;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.xlang.exec.AbstractBinaryExecutable;
import io.nop.xlang.exec.AbstractObjFunctionExecutable;
import io.nop.xlang.exec.CallFuncExecutable;
import io.nop.xlang.exec.CompareOpExecutable;
import io.nop.xlang.exec.FunctionExecutable;
import io.nop.xlang.exec.GuardNotNullExecutable;
import io.nop.xlang.exec.ISeqExecutable;
import io.nop.xlang.exec.LiteralExecutable;
import io.nop.xlang.exec.NotExecutable;
import io.nop.xlang.exec.NullExecutable;
import io.nop.xlang.exec.ReturnNullExecutable;
import io.nop.xlang.exec.SlotAssignExecutable;
import io.nop.xlang.exec.SlotIdentifierExecutable;
import io.nop.xlang.exec.StaticFunctionExecutable;

/**
 * Executable 树指纹（翻译缓存键组成部分，与 java 后端生成类清单的指纹纪律对称）：
 * 递归混合节点类名、源位置（path:line:col）与节点语义载荷（字面量值/slot 下标/函数名等），
 * 不含对象身份——结构相同的两棵树指纹相同，任一差异（含源位置差异）必产生不同指纹，
 * 保证"同路径不同树"（租户差异、资源热变更后重载）不串用旧翻译产物。
 */
public final class TreeFingerprints {

    private TreeFingerprints() {
    }

    public static long fingerprint(IExecutableExpression tree) {
        return mix(0x584c414e47L, tree);
    }

    private static long mix(long seed, IExecutableExpression node) {
        if (node == null)
            return seed * 31 + 1;
        long h = seed * 31 + node.getClass().getName().hashCode();
        h = h * 31 + locationHash(node.getLocation());

        if (node instanceof LiteralExecutable) {
            Object value = ((LiteralExecutable) node).getValue();
            h = h * 31 + (value == null ? 0 : value.getClass().getName().hashCode());
            h = h * 31 + (value == null ? 0 : String.valueOf(value).hashCode());
        } else if (node instanceof SlotIdentifierExecutable) {
            h = h * 31 + ((SlotIdentifierExecutable) node).getSlot();
        } else if (node instanceof SlotAssignExecutable) {
            SlotAssignExecutable assign = (SlotAssignExecutable) node;
            h = h * 31 + assign.getSlot();
            h = mix(h, assign.getExpr());
        } else if (node instanceof CallFuncExecutable) {
            CallFuncExecutable entry = (CallFuncExecutable) node;
            h = h * 31 + (entry.getFuncName() == null ? 0 : entry.getFuncName().hashCode());
            h = h * 31 + arrayHash(entry.getSlotNames());
            h = mixAll(h, entry.getArgExprs());
            h = mix(h, entry.getBodyExpr());
        } else if (node instanceof CompareOpExecutable) {
            CompareOpExecutable cmp = (CompareOpExecutable) node;
            h = h * 31 + cmp.getFilterOp().name().hashCode();
            h = mix(h, cmp.getLeft());
            h = mix(h, cmp.getRight());
        } else if (node instanceof AbstractObjFunctionExecutable) {
            AbstractObjFunctionExecutable fn = (AbstractObjFunctionExecutable) node;
            h = h * 31 + fn.getFuncName().hashCode();
            h = mix(h, fn.getObjExpr());
            h = mixAll(h, fn.getArgs());
        } else if (node instanceof FunctionExecutable) {
            FunctionExecutable fn = (FunctionExecutable) node;
            h = h * 31 + fn.getFuncName().hashCode();
            h = mixAll(h, fn.getArgs());
        } else if (node instanceof StaticFunctionExecutable) {
            StaticFunctionExecutable fn = (StaticFunctionExecutable) node;
            h = h * 31 + fn.getClassName().hashCode();
            h = h * 31 + fn.getFuncName().hashCode();
            h = mixAll(h, fn.getArgExprs());
        } else if (node instanceof NotExecutable) {
            h = mix(h, ((NotExecutable) node).getExpr());
        } else if (node instanceof GuardNotNullExecutable) {
            h = mix(h, ((GuardNotNullExecutable) node).getExpr());
        } else if (node instanceof ReturnNullExecutable) {
            h = mix(h, ((ReturnNullExecutable) node).getExecutable());
        } else if (node instanceof ISeqExecutable) {
            h = mixAll(h, ((ISeqExecutable) node).getExprs());
        } else if (node instanceof NullExecutable) {
            // 无语义载荷
        } else {
            // 其余子集内节点（二元算术/比较/逻辑族）：仅类名 + 子节点
            if (node instanceof AbstractBinaryExecutable) {
                AbstractBinaryExecutable binary = (AbstractBinaryExecutable) node;
                h = mix(h, binary.getLeft());
                h = mix(h, binary.getRight());
            }
        }
        return h;
    }

    private static long mixAll(long seed, IExecutableExpression[] nodes) {
        long h = seed;
        if (nodes == null)
            return h * 31;
        h = h * 31 + nodes.length;
        for (IExecutableExpression node : nodes) {
            h = mix(h, node);
        }
        return h;
    }

    private static long arrayHash(String[] values) {
        if (values == null)
            return 0;
        long h = values.length;
        for (String value : values) {
            h = h * 31 + (value == null ? 0 : value.hashCode());
        }
        return h;
    }

    private static long locationHash(SourceLocation loc) {
        if (loc == null)
            return 0;
        long h = loc.getPath() == null ? 0 : loc.getPath().hashCode();
        h = h * 31 + loc.getLine();
        h = h * 31 + loc.getCol();
        return h;
    }
}
