/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xdsl;

import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;

/**
 * 从资源文件加载XDSL模型对象，封装了x:extends展开过程。使用此接口可以避免直接依赖nop-xlang包来实现自定义dsl parser
 */
public interface IXDslNodeLoader {
    default XDslExtendResult loadFromResource(IResource resource) {
        return loadFromResource(resource, null);
    }

    default XDslExtendResult loadFromResource(IResource resource, String requiredSchema) {
        return loadFromResource(resource, requiredSchema, XDslExtendPhase.validate);
    }

    XDslExtendResult loadFromResource(IResource resource, String requiredSchema, XDslExtendPhase phase);

    XDslExtendResult loadFromNode(XNode node, String requiredSchema, XDslExtendPhase phase);
}
