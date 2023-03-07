/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xpath;

import io.nop.commons.functional.select.ISelector;

import java.io.Serializable;

/**
 * 从XNode节点上选择一个值
 */
public interface IXPathValueSelector<E, V> extends ISelector<E, IXPathContext<E>, V>, Serializable {
    /**
     * 是否仅包含标签名和属性匹配条件，因此可以唯一定位到一个节点。 单一匹配时，可以用于setValue。例如 a/b[@name='xxx']/$value, 用于设置值时会自动创建a节点和b节点
     *
     * @return
     */
    default boolean isBiDirectional() {
        return false;
    }

    /**
     * 是否从根节点开始查找，而不是从当前source节点开始查找
     */
    default boolean selectFromRoot() {
        return false;
    }
}
