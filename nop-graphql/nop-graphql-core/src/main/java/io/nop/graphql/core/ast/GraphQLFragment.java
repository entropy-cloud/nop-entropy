/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.ast;

import io.nop.graphql.core.ast._gen._GraphQLFragment;

public class GraphQLFragment extends _GraphQLFragment {
    private boolean resolved;

    public boolean isResolved() {
        return resolved;
    }

    public void setResolved(boolean resolved) {
        this.resolved = resolved;
    }

    public boolean isExceedDepth(int depth) {
        GraphQLSelectionSet selection = getSelectionSet();
        if (selection != null)
            return selection.isExceedDepth(depth);
        return false;
    }
}