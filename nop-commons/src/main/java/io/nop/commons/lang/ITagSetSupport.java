/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.lang;

import io.nop.commons.util.TagsHelper;

import java.util.Collection;

/**
 * @author canonical_entropy@163.com
 */
public interface ITagSetSupport {
    Collection<String> getTagSet();

    default boolean containsTag(String tag) {
        return TagsHelper.contains(getTagSet(), tag);
    }

    default boolean containsAnyTag(Collection<String> tags) {
        return TagsHelper.containsAny(getTagSet(), tags);
    }

    default boolean containsAllTag(Collection<String> tags) {
        return TagsHelper.containsAll(getTagSet(), tags);
    }
}