/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.component.version;

import io.nop.api.core.util.IComponentModel;

import java.util.List;

public interface IVersionedModelStore<T extends IComponentModel> {
    Long getLatestVersion(String modelName);

    /**
     * 得到模型的所有版本号，从小到到排列
     */
    List<Long> getAllVersions(String modelName);

//    IResource getModelResource(String modelName, Long modelVersion);

    T getModel(String modelName, Long modelVersion);

    void removeModelCache(String modelName, Long modelVersion);
}
