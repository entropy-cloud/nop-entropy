package io.nop.biz.crud;

import io.nop.xlang.xmeta.IObjPropMeta;

public class ManyToManyPropMeta {
    private final IObjPropMeta propMeta;
    private final String relatedEntityName;
    private final String joinLeftProp;

    private final String joinRightProp;

    private final String joinRefProp;

    public ManyToManyPropMeta(IObjPropMeta propMeta, String relatedEntityName,
                              String joinLeftProp, String joinRightProp,
                              String joinRefProp) {
        this.propMeta = propMeta;
        this.relatedEntityName = relatedEntityName;
        this.joinLeftProp = joinLeftProp;
        this.joinRightProp = joinRightProp;
        this.joinRefProp = joinRefProp;
    }

    public IObjPropMeta getPropMeta() {
        return propMeta;
    }

    public String getRelatedEntityName() {
        return relatedEntityName;
    }

    public String getJoinLeftProp() {
        return joinLeftProp;
    }

    public String getJoinRightProp() {
        return joinRightProp;
    }

    public String getJoinRefProp() {
        return joinRefProp;
    }
}
