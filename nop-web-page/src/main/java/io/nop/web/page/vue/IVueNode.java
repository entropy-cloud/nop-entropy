/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.web.page.vue;

import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.ast.Expression;

import java.util.List;
import java.util.function.Consumer;

public interface IVueNode extends ISourceLocationGetter {
    SourceLocation getLocation();

    String getType();

    boolean isSlot();

    Expression getContentExpr();

    List<VueNode> getChildren();

    void forEachNode(Consumer<IVueNode> action);
}
