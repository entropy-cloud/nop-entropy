/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.resource.component;

import io.nop.commons.lang.IRefreshable;
import io.nop.core.resource.IResource;

/**
 * 根据模型文件生成的组件。全局组件管理器会缓存所有读取过的组件文件的内容
 */
public interface IGeneratedComponent extends IRefreshable {
    /**
     * 模型文件的源路径
     *
     * @return
     */
    String getModelPath();

    IResource getModelResource();

    /**
     * 根据模型生成的组件文件的格式
     *
     * @return
     */
    String getGenFormat();

    /**
     * 根据模型生成的组件文件
     *
     * @return
     */
    IResource getGenResource();

    default String getGenPath() {
        IResource resource = getGenResource();
        return resource == null ? null : resource.getPath();
    }

    /**
     * 生成的组件文件的文本内容。相比于直接调用getGenResource().readText()，这里的函数后首先读取缓存中的内容。
     *
     * @return
     */
    default String getGenText() {
        return getGenResource().readText();
    }

    /**
     * 一个模型有可能生成多个子组件
     *
     * @param subPath 子组件路径
     * @return 如果没有对应子组件，则返回null
     */
    default IResource getSubResource(String subPath) {
        return null;
    }

    default String getSubResourceText(String subPath) {
        IResource resource = getSubResource(subPath);
        if (resource == null)
            return null;
        return resource.readText();
    }
}