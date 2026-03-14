/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state;

/**
 * A {@link StateDescriptor} for {@link ListState}.
 *
 * @param <T> The type of the elements in the list state.
 */
public class ListStateDescriptor<T> extends StateDescriptor<T> {

    public ListStateDescriptor(String name, Class<T> valueType) {
        super(name, valueType);
    }
}
