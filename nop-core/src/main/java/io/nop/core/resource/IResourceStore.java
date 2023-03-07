/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.resource;

import io.nop.api.core.util.progress.IStepProgressListener;

import java.util.List;
import java.util.Map;

/**
 * 在IResourceLoader的基础上增加saveResource保存资源文件的功能
 */
public interface IResourceStore extends IResourceLoader {
    /**
     * 返回虚拟路径对应的资源
     *
     * @param path 必须是绝对路径
     * @return
     */
    IResource getResource(String path, boolean returnNullIfNotExists);

    default IResource getResource(String path) {
        return getResource(path, false);
    }

    /**
     * 获取目录下的所有子资源。考虑到复杂的资源定位逻辑，父子关系不宜定义在IResource接口上。IResource完全根据名字来定位。
     *
     * @param path
     * @return
     */
    List<? extends IResource> getChildren(String path);

    boolean supportSave(String path);

    /**
     * 将文件保存到远程存储系统中，并返回一个引用路径。使用该路径可以通过IResourceStore.getResource(resultPath)来获取到远程文件
     *
     * @param path     目标保存路径
     * @param resource 指向需要存储到store中的的文件
     * @param options
     * @return IResourceStore可以识别的存储路径。store有可能分配一个随机生成的路径来作为保存文件路径
     */
    String saveResource(String path, IResource resource, IStepProgressListener listener, Map<String, Object> options);

}