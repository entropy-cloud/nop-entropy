/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource;

/**
 * 为避免概念混淆，特别定义一个装载接口，此接口必须是允许反复调用多次的。而IResourceParser接口一般是新建parser对象，只允许调用一次。
 *
 * @param <T> 模型对象类型
 */
public interface IResourceObjectLoader<T> {

    /**
     * 如果路径对应的资源不存在，则可能返回null
     *
     * @param path 有的时候需要根据传入的path经过复杂的计算才能定位IResource, 为了避免重新计算，这里传入原始的path参数
     */
    T loadObjectFromPath(String path);

    default T loadObjectFromResource(IResource resource) {
        return loadObjectFromPath(resource.getPath());
    }
}