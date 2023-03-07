/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.resource.component;

import io.nop.api.core.util.IComponentModel;

/**
 * 根据模型，生成指定格式的组件对象。
 */
public interface IComponentGenerator {
    IGeneratedComponent generateComponent(IComponentModel model, String genFormat, IResourceComponentManager manager);
}