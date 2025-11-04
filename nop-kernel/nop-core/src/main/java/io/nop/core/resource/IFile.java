/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource;

import io.nop.core.model.tree.ITreeChildrenStructure;

import java.util.List;

/**
 * 在Resource的基础上提供目录树抽象。只允许向下遍历child, 不能直接访问parent。访问parent需要从IResourceLocator进入。
 */
public interface IFile extends IResource, ITreeChildrenStructure, IResourceLoader {

    boolean mkdirs();

    /**
     * 如果不是目录，则children必然返回null
     *
     * @return
     */
    boolean isDirectory();

    /**
     * 创建新文件
     *
     * @return
     */
    boolean createNewFile();

    /**
     * 仿照File.createTempFile的逻辑创建临时路径
     *
     * @param prefix
     * @param suffix
     * @return
     */
    IFile createTempFile(String prefix, String suffix);

    /**
     * 资源重命名
     *
     * @param resource
     * @return
     */
    boolean renameTo(IResource resource);

    /**
     * 考虑到安全性要求，relativeName只能是子目录或者子文件的相对路径，而且其中不能包含./或者../这种相对定位形式。
     *
     * @param relativeName
     * @return
     */
    IFile getResource(String relativeName);

    default List<IFile> getChildren(String path) {
        return getResource(path).getChildren();
    }

    /**
     * 要求返回的children按照名称排序
     */
    List<IFile> getChildren();

    /**
     * JVM正常关闭时会试图删除此资源
     */
    void deleteOnExit();
}