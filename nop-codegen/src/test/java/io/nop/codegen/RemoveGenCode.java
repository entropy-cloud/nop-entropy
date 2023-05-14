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
        dir = new File(dir, "..");

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
