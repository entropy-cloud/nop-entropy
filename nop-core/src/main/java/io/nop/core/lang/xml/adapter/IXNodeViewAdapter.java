/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.xml.adapter;

import io.nop.core.model.tree.ITreeChildrenAdapter;
import io.nop.core.model.tree.ITreeParentAdapter;

import java.util.Collection;

public interface IXNodeViewAdapter<T> extends ITreeChildrenAdapter<T>, ITreeParentAdapter<T> {

    String tagName(T node);

    Object attr(T node, String attrName);

    T getParent(T node);

    Collection<T> getChildren(T node);

    Object value(T node);

    String text(T node);

    String xml(T node);

    String innerXml(T node);

    String html(T node);

    String innerHtml(T node);
}
