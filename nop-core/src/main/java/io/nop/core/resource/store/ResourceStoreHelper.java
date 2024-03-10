/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.store;

import io.nop.core.model.tree.ITreeStateVisitor;
import io.nop.core.model.tree.TreeVisitResult;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceStore;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.ResourceTreeVisitState;
import io.nop.core.resource.VirtualFileSystem;

public class ResourceStoreHelper {
    public static void saveStore(IResourceStore srcStore, String fromPath, IResourceStore targetStore, String toPath) {
        srcStore.visitResource(fromPath, new ITreeStateVisitor<>() {
            @Override
            public TreeVisitResult beginNodeState(ResourceTreeVisitState state) {
                String targetPath = state.buildFullPath(toPath);
                IResource targetResource = targetStore.getResource(targetPath);
                state.getCurrent().saveToResource(targetResource);
                return TreeVisitResult.CONTINUE;
            }
        });
    }

    public static void dumpStore(IResourceStore store, String path) {
        store.visitResource(path, new ITreeStateVisitor<>() {
            @Override
            public TreeVisitResult beginNodeState(ResourceTreeVisitState state) {
                if(state.getCurrent().isDirectory())
                    return TreeVisitResult.CONTINUE;
                String dumpPath = ResourceHelper.getDumpPath(state.getCurrent().getPath());
                IResource targetResource = VirtualFileSystem.instance().getResource(dumpPath);
                state.getCurrent().saveToResource(targetResource);
                return TreeVisitResult.CONTINUE;
            }
        });
    }
}
