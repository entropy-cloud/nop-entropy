/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xpath;

import io.nop.core.context.IEvalContext;
import io.nop.core.lang.xml.adapter.IXNodeAdapter;

public interface IXPathContext<E> extends IEvalContext {
    /**
     * 保存根节点
     *
     * @return
     */
    E root();

    E getThisNode();

    void setThisNode(E node);

    /**
     * 用于将外部结构适配为xpath所需结构
     */
    IXNodeAdapter<E> adapter();
}