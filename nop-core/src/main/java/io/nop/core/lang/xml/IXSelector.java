/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.xml;

import java.util.Collection;

/**
 * 类似XPath的选择符
 */
public interface IXSelector<T> {
    Object select(T node);

    /**
     * 如果是可以唯一确定值的选择符，则可以利用它来更新节点的值
     *
     * @param adapter 适配器
     * @param node    起始节点
     * @param value   需要更新的值
     */
    void updateSelected(T node, Object value);

    Collection<?> selectAll(T node);
}