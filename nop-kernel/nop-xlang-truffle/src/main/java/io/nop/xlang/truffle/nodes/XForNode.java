package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.api.core.convert.ConvertHelper;

/**
 * for 循环节点（ForExecutable/SimpleForExecutable 直译）：init → while(test){ body; update }
 * 与解释器逐迭代次序一一对应（test 前置、update 尾部）。
 *
 * <p>控制流异常（plan I7 Phase 1 §1）：XLReturn 穿透（= 解释器循环 return ret）；XLBreak/
 * XLContinue 捕获消费（消费即清零——break 退出循环、continue 进入下轮迭代前先执行 update，
 * 与解释器 continue 后执行 update 的次序一致）。
 */
public final class XForNode extends XExprNode {

    private final XExprNode init;

    private final XExprNode test;

    private final XExprNode update;

    private final XExprNode body;

    public XForNode(XExprNode init, XExprNode test, XExprNode update, XExprNode body) {
        this.init = init;
        this.test = test;
        this.update = update;
        this.body = body;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        if (init != null)
            init.execute(frame);
        while (passTest(frame)) {
            try {
                body.execute(frame);
            } catch (XLBreakException e) {
                break;
            } catch (XLContinueException e) {
                // 消费后进入 update（与解释器 continue → update 次序一致）
            }
            if (update != null)
                update.execute(frame);
        }
        return null;
    }

    private boolean passTest(VirtualFrame frame) {
        return test == null || ConvertHelper.toTruthy(test.execute(frame));
    }
}
