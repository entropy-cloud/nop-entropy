/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.idea.plugin.resource;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import io.nop.api.core.util.progress.IStepProgressListener;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceNamespaceHandler;
import io.nop.core.resource.IVirtualFileSystem;
import io.nop.core.resource.impl.UnknownResource;
import io.nop.idea.plugin.utils.ProjectFileHelper;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class ProjectVirtualFileSystem implements IVirtualFileSystem {
    static final Logger LOG = LoggerFactory.getLogger(ProjectVirtualFileSystem.class);

    @Override
    public IResource getResource(String path, boolean returnNull) {
        Project project = ProjectEnv.currentProject();
        if (project == null)
            return returnNull ? null : new UnknownResource(path);

        String fileName = StringHelper.fileFullName(path);
        PsiFile[] files = FilenameIndex.getFilesByName(project, fileName, GlobalSearchScope.allScope(project));
        if (files.length == 0) {
            LOG.debug("nop.project.file-index-not-found:{}", fileName);
            return returnNull ? null : new UnknownResource(path);
        }

        if (path.startsWith("/dict/")) {
            // 在模块中查找
            for (PsiFile file : files) {
                String matchPath = ProjectFileHelper.getNopVfsPath(file.getVirtualFile());
                if (matchPath != null && matchPath.startsWith(path))
                    return new VirtualFileResource(path, file.getVirtualFile());
            }
        } else {
            for (PsiFile file : files) {
                String matchPath = ProjectFileHelper.getNopVfsPath(file.getVirtualFile());
                if (Objects.equals(path, matchPath))
                    return new VirtualFileResource(path, file.getVirtualFile());
            }
        }

        return returnNull ? null : new UnknownResource(path);
    }

    @Override
    public IResource getRawResource(String path, boolean ignoreUnknown) {
        return getResource(path, ignoreUnknown);
    }

    @Override
    public void registerNamespaceHandler(@NotNull IResourceNamespaceHandler iResourceNamespaceHandler) {

    }

    @Override
    public void unregisterNamespaceHandler(@NotNull IResourceNamespaceHandler iResourceNamespaceHandler) {

    }

    @Override
    public Set<String> getClassPathResources() {
        return Collections.emptySet();
    }

    @Override
    public void destroy() {

    }

    @Override
    public List<? extends IResource> getChildren(String path) {
        return Collections.emptyList();
    }

    @Override
    public boolean supportSave(String path) {
        return false;
    }

    @Override
    public String saveResource(String path, IResource iResource, IStepProgressListener iStepProgressListener, Map<String, Object> map) {
        return null;
    }
}
