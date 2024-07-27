/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.ast;

import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.ast._gen._ArrayExpression;

import java.util.List;

public class ArrayExpression extends _ArrayExpression {
    public static ArrayExpression valueOf(SourceLocation loc, List<XLangASTNode> list) {
        ArrayExpression ret = new ArrayExpression();
        ret.setLocation(loc);
        ret.setElements(list);
        return ret;
    }
}