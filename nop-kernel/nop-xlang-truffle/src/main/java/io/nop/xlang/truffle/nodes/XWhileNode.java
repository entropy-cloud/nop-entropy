package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.api.core.convert.ConvertHelper;

/**
 * while 循环节点（WhileExecutable/SimpleWhileExecutable 直译）：test 前置真值判定
 * （toTruthy）；XLBreak/XLContinue 捕获消费、XLReturn 穿透。
 */
public final class XWhileNode extends XExprNode {

    private final XExprNode test;

    private final XExprNode body;

    public XWhileNode(XExprNode test, XExprNode body) {
        this.test = test;
        this.body = body;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        while (ConvertHelper.toTruthy(test.execute(frame))) {
            try {
                body.execute(frame);
            } catch (XLBreakException e) {
                break;
            } catch (XLContinueException e) {
                // 消费后进入下一轮 test
            }
        }
        return null;
    }
}
