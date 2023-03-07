/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.resource;

/**
 * 为避免概念混淆，特别定义一个装载接口，此接口必须是允许反复调用多次的。而IResourceParser接口一般是新建parser对象，只允许调用一次。
 *
 * @param <T>
 */
@FunctionalInterface
public interface IResourceObjectLoader<T> {

    /**
     * 如果路径对应的资源不存在，则可能返回null
     *
     * @param path 有的时候需要根据传入的path经过复杂的计算才能定位IResource, 为了避免重新计算，这里传入原始的path参数
     */
    T loadObjectFromPath(String path);
}