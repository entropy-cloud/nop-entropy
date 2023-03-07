/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.type;

public enum CollectionKind {
    Collection, Set, List, NONE;

    public boolean isCollectionLike() {
        return this != NONE;
    }

    public boolean isSetLike() {
        return this == Set;
    }

    public boolean isListLike() {
        return this == List;
    }
}
