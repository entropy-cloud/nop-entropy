package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.EvalReference;
import io.nop.xlang.exec.XLangSemantics;

import java.util.Iterator;

/**
 * for-of 循环节点（ForOfExecutable/SimpleForOfExecutable 直译）：items 迭代器经共享 helper
 * {@link XLangSemantics#forOfIterator}；逐元素写入 varSlot（useRef 时新建 EvalReference
 * cell——每迭代新 cell，与解释器一致）、indexSlot 计数；XLBreak/XLContinue 捕获消费、
 * XLReturn 穿透。
 */
public final class XForOfNode extends XExprNode {

    private final SourceLocation loc;

    private final String display;

    private final String itemsDisplay;

    private final int varSlot;

    private final int indexSlot;

    private final boolean useRef;

    private final XExprNode items;

    private final XExprNode body;

    public XForOfNode(SourceLocation loc, String display, String itemsDisplay, int varSlot,
                      int indexSlot, boolean useRef, XExprNode items, XExprNode body) {
        this.loc = loc;
        this.display = display;
        this.itemsDisplay = itemsDisplay;
        this.varSlot = varSlot;
        this.indexSlot = indexSlot;
        this.useRef = useRef;
        this.items = items;
        this.body = body;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object itemsValue = items.execute(frame);
        if (itemsValue == null)
            return null;
        Iterator<Object> it = XLangSemantics.forOfIterator(loc, display, itemsDisplay, itemsValue);

        int index = 0;
        while (it.hasNext()) {
            Object var = it.next();
            frame.setObject(varSlot, useRef ? new EvalReference(var) : var);
            if (indexSlot >= 0) {
                frame.setObject(indexSlot, index);
                index++;
            }
            try {
                body.execute(frame);
            } catch (XLBreakException e) {
                break;
            } catch (XLContinueException e) {
                // 消费后进入下一迭代
            }
        }
        return null;
    }
}
