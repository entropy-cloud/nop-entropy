/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource;

import io.nop.api.core.util.ProcessResult;
import io.nop.commons.collections.IterableIterator;
import io.nop.core.model.tree.ITreeStateVisitor;
import io.nop.core.model.tree.TreeVisitors;
import io.nop.core.resource.find.SimplePatternFinder;
import io.nop.core.resource.impl.RelativeResourceLoader;
import io.nop.core.resource.impl.ResourceChildrenAdapter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * 通过路径名可以定位资源文件，支持遍历文件树。
 */
public interface IResourceLoader {
    IResource getResource(String path);

    default IResource makeResource(String path){
        return getResource(path);
    }

    Collection<? extends IResource> getChildren(String path);

    default IResourceLoader relativeLoader(String basePath) {
        return new RelativeResourceLoader(this, basePath);
    }

    /**
     * 获取在某个虚拟文件路径下，具有特定后缀的文件集合。标准路径相同时定制文件将取代基础文件。
     *
     * @param path
     * @param suffix
     */
    default Collection<? extends IResource> getAllResources(String path, String suffix) {
        IResource root = getResource(path);
        IterableIterator<IResource> it = TreeVisitors.depthFirstIterator(new ResourceChildrenAdapter(this), root, false,
                res -> suffix == null || res.isDirectory() || res.getName().endsWith(suffix));
        List<IResource> ret = new ArrayList<>();
        for (IResource resource : it) {
            if (resource.isDirectory())
                continue;
            ret.add(resource);
        }
        return ret;
    }

    default void visitResource(String path, ITreeStateVisitor<ResourceTreeVisitState> visitor) {
        IResource resource = getResource(path);
        TreeVisitors.visitTreeState(new ResourceTreeVisitState(resource, new ResourceChildrenAdapter(this)), visitor);
    }

    default void findAll(String path, String pattern, Function<IResource, ProcessResult> consumer) {
        visitResource(path, new SimplePatternFinder(pattern, consumer));
    }

    default List<IResource> findAll(String path, String pattern) {
        List<IResource> ret = new ArrayList<>();
        findAll(path, pattern, res -> {
            ret.add(res);
            return ProcessResult.CONTINUE;
        });
        return ret;
    }

    default List<IResource> findAll(String pattern) {
        return findAll("/", pattern);
    }
}