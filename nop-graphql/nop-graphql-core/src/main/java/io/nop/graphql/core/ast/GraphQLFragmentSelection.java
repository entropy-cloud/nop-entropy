/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.ast;

import io.nop.graphql.core.ast._gen._GraphQLFragmentSelection;

public class GraphQLFragmentSelection extends _GraphQLFragmentSelection {
    private GraphQLFragment resolvedFragment;

    public GraphQLFragment getResolvedFragment() {
        return resolvedFragment;
    }

    public void setResolvedFragment(GraphQLFragment resolvedFragment) {
        this.resolvedFragment = resolvedFragment;
    }

    public GraphQLFragmentSelection newInstance() {
        GraphQLFragmentSelection ret = new GraphQLFragmentSelection();
        ret.setResolvedFragment(resolvedFragment);
        return ret;
    }

    @Override
    public boolean isExceedDepth(int maxDepth) {
        if (resolvedFragment == null)
            return true;

        return resolvedFragment.isExceedDepth(maxDepth);
    }
}