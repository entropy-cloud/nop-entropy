/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.ast;

import io.nop.core.type.IGenericType;
import io.nop.graphql.core.ast._gen._GraphQLArgumentDefinition;
import io.nop.xlang.xmeta.ISchema;

public class GraphQLArgumentDefinition extends _GraphQLArgumentDefinition {
    private int propId;

    private boolean mandatory;
    private ISchema schema;

    private IGenericType javaType;

    public int getPropId() {
        return propId;
    }

    public void setPropId(int propId) {
        this.propId = propId;
    }

    public boolean isMandatory() {
        return mandatory;
    }

    public void setMandatory(boolean mandatory) {
        this.mandatory = mandatory;
    }

    public ISchema getSchema() {
        return schema;
    }

    public void setSchema(ISchema schema) {
        this.schema = schema;
    }

    public IGenericType getJavaType() {
        return javaType;
    }

    public void setJavaType(IGenericType javaType) {
        this.javaType = javaType;
    }

    @Override
    public GraphQLArgumentDefinition deepClone() {
        GraphQLArgumentDefinition ret = super.deepClone();
        ret.setPropId(propId);
        ret.setMandatory(mandatory);
        ret.setSchema(schema);
        ret.setJavaType(javaType);
        return ret;
    }
}