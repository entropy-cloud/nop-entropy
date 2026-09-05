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
 * 作用域变量删除节点（DeleteScopeVarExecutable 直译）：varName 为静态名；
 * attrExpr 非空时运行时求值覆盖变量名（null 时返回 false），最终经共享 helper
 * removeLocalValue 并返回旧值非 null 的 boolean。
 */
public final class XDeleteScopeVarNode extends XExprNode {

    private final SourceLocation loc;

    private final String varName;

    private final XExprNode attr;

    public XDeleteScopeVarNode(SourceLocation loc, String varName, XExprNode attr) {
        this.loc = loc;
        this.varName = varName;
        this.attr = attr;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        String name = varName;
        if (attr != null) {
            Object attrValue = attr.execute(frame);
            if (attrValue == null)
                return false;
            name = String.valueOf(attrValue);
        }
        if (name == null)
            return false;
        return XLangSemantics.deleteScopeValue(loc,
                XLangLanguage.currentContext().requireEvalScope(), name);
    }
}
