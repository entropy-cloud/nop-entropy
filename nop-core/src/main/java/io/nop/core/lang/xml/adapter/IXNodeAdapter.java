/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.xml.adapter;

/**
 * 在xpath选择器中使用的适配器
 */
public interface IXNodeAdapter<T> extends IXNodeViewAdapter<T> {

    void setAttr(T node, String attrName, Object value);

    void setValue(T node, Object value);

    void setXml(T node, String xml);

    void setInnerXml(T node, String innerXml);

    void setHtml(T node, String html);

    void setInnerHtml(T node, String html);

    void setText(T node, String text);
}