package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.xlang.exec.XLangSemantics;

import java.util.Map;

/**
 * Map 构造节点（NewMapExecutable 直译，含 MapItemExecutable 元素）：逐项按序求值（键先值后），
 * spread 项经共享 helper 展开，键经 {@code StringHelper.toString(key, null)} 归一（解释器同源）。
 */
public final class XNewMapNode extends XExprNode {

    private final boolean[] spread;

    private final XExprNode[] keys;

    private final XExprNode[] values;

    public XNewMapNode(boolean[] spread, XExprNode[] keys, XExprNode[] values) {
        this.spread = spread;
        this.keys = keys;
        this.values = values;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Map<String, Object> map = CollectionHelper.newLinkedHashMap(values.length);
        for (int i = 0; i < values.length; i++) {
            if (spread[i]) {
                XLangSemantics.spreadMapPut(map, values[i].execute(frame));
            } else {
                Object key = keys[i].execute(frame);
                Object value = values[i].execute(frame);
                map.put(StringHelper.toString(key, null), value);
            }
        }
        return map;
    }
}
