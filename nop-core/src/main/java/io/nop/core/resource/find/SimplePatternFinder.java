/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.resource.find;

import io.nop.api.core.util.ProcessResult;
import io.nop.core.model.tree.ITreeStateVisitor;
import io.nop.core.model.tree.TreeVisitResult;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceTreeVisitState;

import java.util.function.Function;

public class SimplePatternFinder implements ITreeStateVisitor<ResourceTreeVisitState> {
    private Function<IResource, ProcessResult> consumer;
    private SimplePathPattern pathPattern;
    private int matchIndex = -1;

    public SimplePatternFinder(String pathPattern, Function<IResource, ProcessResult> consumer) {
        this.pathPattern = SimplePathPattern.of(pathPattern);
        this.consumer = consumer;
    }

    @Override
    public TreeVisitResult beginNodeState(ResourceTreeVisitState state) {
        if (matchIndex == -1) {
            // 跳过根节点
            matchIndex++;
            return TreeVisitResult.CONTINUE;
        }
        String name = state.getCurrent().getName();
        if (!pathPattern.matchComponent(matchIndex, name)) {
            return TreeVisitResult.SKIP_CHILD;
        }

        if (matchIndex + 1 == pathPattern.size()) {
            ProcessResult result = consumer.apply(state.getCurrent());
            if (result == ProcessResult.STOP)
                return TreeVisitResult.END;

            return TreeVisitResult.SKIP_CHILD;
        }
        matchIndex++;
        return TreeVisitResult.CONTINUE;
    }

    @Override
    public TreeVisitResult endNodeState(ResourceTreeVisitState state) {
        matchIndex--;
        return TreeVisitResult.CONTINUE;
    }
}