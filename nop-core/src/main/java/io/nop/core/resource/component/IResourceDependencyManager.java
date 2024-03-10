/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.component;

import java.util.function.Supplier;

public interface IResourceDependencyManager {

    boolean isDependencyChanged(String resourcePath);

    /**
     * 根据task处理过程中访问的资源文件情况，把它们收集到依赖集合对象中
     *
     * @param resourcePath 正在收集此资源文件的依赖集合
     * @param task         处理任务，在其中通过traceDepends函数来记录依赖文件
     * @return task的返回结果
     */
    <T> T collectDepends(String resourcePath, Supplier<T> task);

    /**
     * 忽略task执行过程中的依赖关系，不把它们追加到当前的依赖集合中
     */
    <T> T ignoreDepends(Supplier<T> task);

    /**
     * 将资源文件追加到当前上下文的依赖集合中
     *
     * @param depResourcePath 被依赖的文件
     */
    void traceDepends(String depResourcePath);

    <T> T runWhenDependsChanged(String resourcePath, Supplier<T> task);
}
