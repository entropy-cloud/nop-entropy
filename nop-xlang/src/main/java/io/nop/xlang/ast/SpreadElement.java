/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.ast;

import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.ast._gen._SpreadElement;

public class SpreadElement extends _SpreadElement {
    public static SpreadElement valueOf(SourceLocation loc, Expression expr) {
        SpreadElement elm = new SpreadElement();
        elm.setLocation(loc);
        elm.setArgument(expr);
        return elm;
    }
}