/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.xml;

import io.nop.core.CoreConstants;
import io.nop.core.context.IEvalContext;

public interface IXNodeGenerator {
    XNode generateNode(IEvalContext context);

    default IXNodeGenerator both(IXNodeGenerator gen) {
        return ctx -> {
            XNode node = generateNode(ctx);
            if (node == null)
                return gen.generateNode(ctx);

            if (node.isDummyNode()) {
                if (!node.hasChild())
                    return gen.generateNode(ctx);
                XNode other = gen.generateNode(ctx);
                if (other == null)
                    return node;
                if (other.isDummyNode()) {
                    node.appendChildren(other.detachChildren());
                } else {
                    node.appendChild(other);
                }
                return node;
            } else {
                XNode other = gen.generateNode(ctx);
                if (other == null)
                    return node;
                if (other.isDummyNode()) {
                    other.prependChild(node);
                    return other;
                } else {
                    XNode ret = XNode.makeDocNode(CoreConstants.DUMMY_TAG_NAME);
                    ret.appendChild(node);
                    ret.appendChild(other);
                    return ret;
                }
            }
        };
    }
}
