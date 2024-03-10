/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource;

import io.nop.commons.lang.IDestroyable;
import io.nop.commons.lang.IRefreshable;
import io.nop.core.resource.impl.VirtualFile;
import jakarta.annotation.Nonnull;

import java.util.Set;

/**
 * 类似于Docker使用的差量文件系统，内部由多层IResourceStore组成，对外暴露为统一的路径空间，并支持差量定制。
 */
public interface IVirtualFileSystem extends IResourceStore, IDestroyable, IRefreshable {

    IResource getRawResource(String path, boolean returnNullIfExists);

    default IFile getVirtualFile(String path) {
        return new VirtualFile(this, getResource(path));
    }

    void registerNamespaceHandler(@Nonnull IResourceNamespaceHandler handler);

    void unregisterNamespaceHandler(@Nonnull IResourceNamespaceHandler handler);

    /**
     * GraalVM生成native镜像时不支持类路径扫描，需要将事先收集类路径下资源文件
     */
    Set<String> getClassPathResources();

    default void updateInMemoryLayer(IResourceStore layer) {

    }

    default IResourceStore getInMemoryLayer() {
        return null;
    }

    default void refresh(boolean refreshDepends) {

    }
}