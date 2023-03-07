/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.model.tree;

import java.util.Collection;

/**
 * 对树形关系的最小封装，只定义父子关系，不包括属性等其他信息
 */
public interface ITreeStructure extends ITreeChildrenStructure, ITreeParentStructure {
    ITreeStructure getParent();

    @Override
    Collection<? extends ITreeStructure> getChildren();
}