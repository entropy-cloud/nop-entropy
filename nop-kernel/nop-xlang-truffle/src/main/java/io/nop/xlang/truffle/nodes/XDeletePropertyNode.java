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
 * 属性删除节点（DeletePropertyExecutable 直译）：接收者单次求值后经共享 helper
 * 执行 Map-like/Bean 分派删除，返回删除前值非 null 的 boolean（解释器语义）。
 */
public final class XDeletePropertyNode extends XExprNode {

    private final SourceLocation loc;

    private final String display;

    private final String propName;

    private final XExprNode obj;

    public XDeletePropertyNode(SourceLocation loc, String display, String propName, XExprNode obj) {
        this.loc = loc;
        this.display = display;
        this.propName = propName;
        this.obj = obj;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object target = obj.execute(frame);
        return XLangSemantics.deleteProperty(loc, display, propName, target,
                XLangLanguage.currentContext().requireEvalScope());
    }
}
