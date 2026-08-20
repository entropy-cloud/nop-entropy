package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.exec.XLangSemantics;

import java.util.Map;

/**
 * for-in 循环节点（ForInExecutable/SimpleForInExecutable 直译）：items 前置校验经共享
 * helper {@link XLangSemantics#asForInMap}（null → 跳过；非 Map 报错），逐 key 迭代写入
 * varSlot 后执行 body；XLBreak/XLContinue 捕获消费、XLReturn 穿透（消费语义同 XForNode）。
 */
public final class XForInNode extends XExprNode {

    private final SourceLocation loc;

    private final String display;

    private final int varSlot;

    private final XExprNode items;

    private final XExprNode body;

    public XForInNode(SourceLocation loc, String display, int varSlot, XExprNode items, XExprNode body) {
        this.loc = loc;
        this.display = display;
        this.varSlot = varSlot;
        this.items = items;
        this.body = body;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Map<String, Object> map = XLangSemantics.asForInMap(loc, display, items.execute(frame));
        if (map == null)
            return null;
        for (String name : map.keySet()) {
            frame.setObject(varSlot, name);
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
