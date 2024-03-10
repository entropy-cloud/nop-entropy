/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ioc.impl.resolvers;

import io.nop.core.lang.xml.XNode;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.api.IBeanScope;
import io.nop.ioc.impl.IBeanPropValueResolver;
import io.nop.xlang.api.EvalCode;

public class XplValueResolver implements IBeanPropValueResolver {
    private final EvalCode code;

    public XplValueResolver(EvalCode code) {
        this.code = code;
    }

    @Override
    public String toConfigString() {
        return null;
    }

    @Override
    public XNode toConfigNode() {
        XNode node = XNode.make("ioc:xpl");
        node.setContentValue(code.getCode());
        return node;
    }

    @Override
    public Object resolveValue(IBeanContainerImplementor container, IBeanScope scope) {
        return code.getAction();
    }
}