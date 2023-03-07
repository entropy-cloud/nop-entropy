/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.ast;

import io.nop.api.core.util.Guard;
import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.ast._gen._OutputXmlExtAttrsExpression;

import java.util.Set;

public class OutputXmlExtAttrsExpression extends _OutputXmlExtAttrsExpression {
    public static OutputXmlExtAttrsExpression valueOf(SourceLocation loc, Set<String> excludeNames,
                                                      Expression extAttrs) {
        Guard.notNull(excludeNames, "excludeNames");
        Guard.notNull(extAttrs, "extAttrs");

        OutputXmlExtAttrsExpression node = new OutputXmlExtAttrsExpression();
        node.setLocation(loc);
        node.setExcludeNames(excludeNames);
        node.setExtAttrs(extAttrs);
        return node;
    }
}