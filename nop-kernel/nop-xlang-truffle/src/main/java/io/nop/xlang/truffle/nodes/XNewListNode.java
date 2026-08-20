package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.xlang.exec.XLangSemantics;

import java.util.ArrayList;
import java.util.List;

/**
 * 列表构造节点（NewListExecutable 直译，含 ListItemExecutable 元素）：逐元素按序求值，
 * spread 元素经共享 helper 展开，与解释器同一实现来源。
 */
public final class XNewListNode extends XExprNode {

    private final boolean[] spread;

    private final XExprNode[] values;

    public XNewListNode(boolean[] spread, XExprNode[] values) {
        this.spread = spread;
        this.values = values;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        List<Object> list = new ArrayList<>(values.length);
        for (int i = 0; i < values.length; i++) {
            Object value = values[i].execute(frame);
            if (spread[i]) {
                XLangSemantics.spreadListAdd(list, value);
            } else {
                list.add(value);
            }
        }
        return list;
    }
}
