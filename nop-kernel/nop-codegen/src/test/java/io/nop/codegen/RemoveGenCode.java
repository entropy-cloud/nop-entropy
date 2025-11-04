/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.codegen;

import io.nop.commons.util.MavenDirHelper;
import io.nop.core.model.tree.ITreeStateVisitor;
import io.nop.core.model.tree.TreeVisitResult;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceTreeVisitState;
import io.nop.core.resource.impl.FileResource;

import java.io.File;

public class RemoveGenCode {
    public static void main(String[] args) {
        File dir = MavenDirHelper.projectDir(RemoveGenCode.class);
        dir = new File(dir, "../../nop-app-mall");

        new FileResource(dir).visitResource("", new ITreeStateVisitor<ResourceTreeVisitState>() {
            @Override
            public TreeVisitResult beginNodeState(ResourceTreeVisitState state) {
                IResource resource = state.getCurrent();
                if (resource.getName().startsWith("."))
                    return TreeVisitResult.SKIP_CHILD;

                if (resource.getName().equals("test"))
                    return TreeVisitResult.SKIP_CHILD;

                String name = resource.getName();
                if (name.endsWith(".xmeta") && name.startsWith("_")) {
                    resource.delete();
                }
                return TreeVisitResult.CONTINUE;
            }
        });
    }
}
