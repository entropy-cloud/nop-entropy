/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.diff;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;
import java.util.Map;

public interface IDiffValue {
    DiffType getDiffType();

    Object getOldValue();

    Object getNewValue();

    /**
     * 如果diffType是update, 描述对象属性之间的差异
     *
     * @return
     */
    Map<String, ? extends IDiffValue> getPropDiffs();

    /**
     * 如果diffType是update, 描述列表元素之间的差异
     *
     * @return
     */
    List<? extends IDiffValue> getElementDiffs();

    Map<String, ? extends IDiffValue> getKeyedElementDiffs();

    @JsonIgnore
    default boolean isSame() {
        return getDiffType() == DiffType.same;
    }

    @JsonIgnore
    default boolean isAdd() {
        return getDiffType() == DiffType.add;
    }

    @JsonIgnore
    default boolean isRemove() {
        return getDiffType() == DiffType.remove;
    }

    @JsonIgnore
    default boolean isUpdate() {
        return getDiffType() == DiffType.update;
    }

    @JsonIgnore
    default boolean isReplace() {
        return getDiffType() == DiffType.replace;
    }
}