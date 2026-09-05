/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical_entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.exec.XLangSemantics;
import io.nop.xlang.truffle.lang.XLangLanguage;

/**
 * 计算式删除节点（DeleteAttrExecutable 直译）：接收者与 attr 按序各单次求值，
 * 经共享 helper 分派（数组拒绝 / List 按索引或值 / Map 按 key / Bean 走 deleteProperty）。
 */
public final class XDeleteAttrNode extends XExprNode {

    private final SourceLocation loc;

    private final String display;

    private final String attrDisplay;

    private final XExprNode obj;

    private final XExprNode attr;

    public XDeleteAttrNode(SourceLocation loc, String display, String attrDisplay,
                           XExprNode obj, XExprNode attr) {
        this.loc = loc;
        this.display = display;
        this.attrDisplay = attrDisplay;
        this.obj = obj;
        this.attr = attr;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object target = obj.execute(frame);
        Object attrValue = attr.execute(frame);
        return XLangSemantics.deleteAttr(loc, display, attrDisplay, target, attrValue,
                XLangLanguage.currentContext().requireEvalScope());
    }
}
