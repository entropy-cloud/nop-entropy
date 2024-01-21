/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.graphql.core.ast;

import io.nop.api.core.annotations.meta.PropMeta;
import io.nop.graphql.core.ast._gen._GraphQLInputFieldDefinition;
import io.nop.xlang.xmeta.IObjPropMeta;

public class GraphQLInputFieldDefinition extends _GraphQLInputFieldDefinition implements IGraphQLFieldDefinition {
    private int propId;

    private IObjPropMeta propMeta;

    private PropMeta beanPropMeta;

    public int getPropId() {
        return propId;
    }

    public void setPropId(int propId) {
        this.propId = propId;
    }

    public int getPropIdFromMeta() {
        if (propMeta != null) {
            Integer propId = propMeta.getPropId();
            if (propId == null)
                return 0;
            return propId;
        }
        if (beanPropMeta != null)
            return beanPropMeta.propId();
        return 0;
    }
}
