/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.ast;

import io.nop.api.core.annotations.meta.PropMeta;
import io.nop.core.type.IGenericType;
import io.nop.graphql.core.ast._gen._GraphQLInputFieldDefinition;
import io.nop.xlang.xmeta.IObjPropMeta;

public class GraphQLInputFieldDefinition extends _GraphQLInputFieldDefinition implements IGraphQLFieldDefinition {
    private int propId;

    private IObjPropMeta propMeta;

    private PropMeta beanPropMeta;

    private IGenericType javaType;

    public IObjPropMeta getPropMeta() {
        return propMeta;
    }

    public void setPropMeta(IObjPropMeta propMeta) {
        this.propMeta = propMeta;
    }

    public PropMeta getBeanPropMeta() {
        return beanPropMeta;
    }

    public void setBeanPropMeta(PropMeta beanPropMeta) {
        this.beanPropMeta = beanPropMeta;
    }

    public int getPropId() {
        return propId;
    }

    public void setPropId(int propId) {
        this.propId = propId;
    }

    public IGenericType getJavaType() {
        return javaType;
    }

    public void setJavaType(IGenericType javaType) {
        this.javaType = javaType;
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

    @Override
    public GraphQLInputFieldDefinition deepClone() {
        GraphQLInputFieldDefinition ret = super.deepClone();
        ret.setPropId(propId);
        ret.setJavaType(javaType);
        ret.setPropMeta(propMeta);
        ret.setBeanPropMeta(beanPropMeta);
        return ret;
    }
}
