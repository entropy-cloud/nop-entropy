/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.ast;

import io.nop.api.core.util.Guard;
import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.ast._gen._EscapeOutputExpression;

public class EscapeOutputExpression extends _EscapeOutputExpression {

    public static EscapeOutputExpression valueOf(SourceLocation loc, XLangEscapeMode escapeMode, Expression text) {
        Guard.notNull(text, "text is null");
        Guard.notNull(escapeMode, "escapeMode is null");
        EscapeOutputExpression node = new EscapeOutputExpression();
        node.setLocation(loc);
        node.setEscapeMode(escapeMode);
        node.setText(text);
        return node;
    }

    public static EscapeOutputExpression plainText(SourceLocation loc, String text) {
        return valueOf(loc, XLangEscapeMode.none, Literal.valueOf(loc, text));
    }
}