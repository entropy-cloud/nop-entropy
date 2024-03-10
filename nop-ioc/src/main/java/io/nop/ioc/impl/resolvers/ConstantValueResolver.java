/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ioc.impl.resolvers;

import io.nop.core.lang.eval.DisabledEvalScope;
import io.nop.core.lang.xml.XNode;
import io.nop.core.reflect.IFieldModel;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.api.IBeanScope;
import io.nop.ioc.impl.IBeanPropValueResolver;

public class ConstantValueResolver implements IBeanPropValueResolver {
    private final String staticField;
    private final IFieldModel field;

    public ConstantValueResolver(String staticField, IFieldModel field) {
        this.staticField = staticField;
        this.field = field;
    }

    @Override
    public String toConfigString() {
        return null;
    }

    @Override
    public XNode toConfigNode() {
        XNode node = XNode.make("util:constant");
        node.setAttr("static-field", staticField);
        return node;
    }

    @Override
    public Object resolveValue(IBeanContainerImplementor container, IBeanScope scope) {
        return field.getGetter().getProperty(null, field.getName(), DisabledEvalScope.INSTANCE);
    }
}
