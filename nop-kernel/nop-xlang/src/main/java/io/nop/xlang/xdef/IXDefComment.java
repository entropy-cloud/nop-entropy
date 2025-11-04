/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xdef;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Map;
import java.util.Set;

public interface IXDefComment {
    String toComment();

    String getMainDisplayName();

    String getMainDescription();

    Map<String, ? extends IXDefSubComment> getSubComments();

    @JsonIgnore
    default Set<String> getSubNames() {
        return getSubComments().keySet();
    }

    default String getSubDisplayName(String subName) {
        IXDefSubComment subComment = getSubComments().get(subName);
        return subComment == null ? null : subComment.getDisplayName();
    }

    default String getSubDescription(String subName) {
        IXDefSubComment subComment = getSubComments().get(subName);
        return subComment == null ? null : subComment.getDescription();
    }

    /**
     * 合并两个comment。sub名称相同时，采用comment对象中的内容覆盖当前对象中的内容
     *
     * @param comment 包含
     * @return 生成一个新的、对应于合并后结果的对象
     */
    IXDefComment applyOverride(IXDefComment comment);
}
