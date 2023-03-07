/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.model.tree;

import java.util.Collection;

public interface ITreeChildrenStructure {
    /**
     * 如果不支持children, 则返回null
     *
     * @return
     */
    Collection<? extends ITreeChildrenStructure> getChildren();
}