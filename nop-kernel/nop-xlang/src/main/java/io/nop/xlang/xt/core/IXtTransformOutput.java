/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xt.core;

import io.nop.core.lang.xml.XNode;

public interface IXtTransformOutput {

    void addChild(XNode node);

    void setValue(Object value);

    void addAttr(String name, Object value);

    XNode getCurrentNode();

    XNode newOutputNode(String tagName);

    void pushNode(XNode node);

    void popNode();
}
