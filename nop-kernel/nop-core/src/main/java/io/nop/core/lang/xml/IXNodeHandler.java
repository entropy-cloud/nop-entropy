/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.xml;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.lang.eval.IEvalOutput;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 类似于SAX接口，通过回调函数接收XNode结构数据
 */
public interface IXNodeHandler extends IEvalOutput {
    void beginDoc(String encoding, String docType, String instruction);

    void comment(String comment);

    void simpleNode(SourceLocation loc, String tagName, Map<String, ValueWithLocation> attrs);

    void beginNode(SourceLocation loc, String tagName, Map<String, ValueWithLocation> attrs);

    default void simpleNode(String tagName) {
        simpleNode(null, tagName, Collections.emptyMap());
    }

    default void beginNode(String tagName) {
        beginNode(null, tagName, Collections.emptyMap());
    }

    default void appendChild(SourceLocation loc, String tagName, Map<String, Object> attrs) {
        XNode node = XNode.make(tagName);
        node.setLocation(loc);
        if (attrs != null)
            node.setAttrs(attrs);
        appendChild(node);
    }

    void value(SourceLocation loc, Object value);

    void text(SourceLocation loc, String text);

    default void appendChild(XNode node) {
        node.process(this);
    }

    default void appendChildren(List<XNode> children) {
        if (children != null) {
            for (XNode child : children) {
                appendChild(child);
            }
        }
    }

    void endNode(String tagName);

    XNode endDoc();
}