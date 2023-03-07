/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.resource.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IFile;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceStore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static io.nop.core.CoreErrors.ARG_RESOURCE;
import static io.nop.core.CoreErrors.ERR_RESOURCE_NOT_DIR;

public class VirtualFile extends DelegateResource implements IFile {
    private static final long serialVersionUID = -6818490459711361754L;

    private final IResourceStore store;

    public VirtualFile(IResourceStore store, IResource file) {
        super(file.getPath(), file);
        this.store = store;
    }

    IFile getFile() {
        IResource resource = getResource();
        if (resource instanceof IFile)
            return (IFile) resource;
        return null;
    }

    public IFile getResource(String subPath) {
        String fullPath = StringHelper.appendPath(getStdPath(), subPath);
        return new VirtualFile(store, store.getResource(fullPath));
    }

    @Override
    public boolean mkdirs() {
        IFile file = getFile();
        if (file == null)
            return false;
        return file.mkdirs();
    }

    @Override
    public boolean createNewFile() {
        IFile file = getFile();
        if (file == null)
            return false;
        return file.createNewFile();
    }

    @Override
    public boolean renameTo(IResource resource) {
        IFile file = getFile();
        if (file == null)
            return false;
        return file.renameTo(resource);
    }

    @Override
    public void deleteOnExit() {
        IFile file = getFile();
        if (file == null)
            return;
        file.deleteOnExit();
    }

    @Override
    public IFile createTempFile(String prefix, String suffix) {
        IFile file = getFile();
        if (file == null)
            throw new NopException(ERR_RESOURCE_NOT_DIR).param(ARG_RESOURCE, this);
        return file.createTempFile(prefix, suffix);
    }

    @Override
    public List<IFile> getChildren() {
        Collection<? extends IResource> list = store.getChildren(getStdPath());
        if (list == null)
            return null;
        List<IFile> ret = new ArrayList<>(list.size());
        for (IResource resource : list) {
            ret.add(new VirtualFile(store, resource));
        }
        return ret;
    }
}