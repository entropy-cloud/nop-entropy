package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.api.core.convert.ConvertHelper;

/**
 * do-while 循环节点（DoWhileExecutable/SimpleDoWhileExecutable 直译）：body 先行至少一次，
 * test 尾部真值判定（null test 恒真，与解释器 passTest 一致）；XLBreak/XLContinue 捕获消费、
 * XLReturn 穿透。
 */
public final class XDoWhileNode extends XExprNode {

    private final XExprNode test;

    private final XExprNode body;

    public XDoWhileNode(XExprNode test, XExprNode body) {
        this.test = test;
        this.body = body;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        do {
            try {
                body.execute(frame);
            } catch (XLBreakException e) {
                break;
            } catch (XLContinueException e) {
                // 消费后进入尾部 test
            }
        } while (test == null || ConvertHelper.toTruthy(test.execute(frame)));
        return null;
    }
}
