/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.sql;

import io.nop.commons.text.marker.Marker;

public class SyntaxMarker extends Marker {
    private static final long serialVersionUID = -2666946667607117556L;

    /**
     * 在SQL语句中用以下文本来占位
     */
    public static final String TAG_WHERE = "/*where*/";

    public static final String TAG_AND = "/*and*/";

    public static final String TAG_FILTER = "/*filter*/";

    public enum SyntaxMarkerType {
        BEGIN_SELECT_FIELDS, ENTITY_FIELDS, END_SELECT_FIELDS, // select部分的字段列表
        TABLE, // 表名
        FILTER,
        LOGICAL_DELETE_FILTER // where部分针对指定表的过滤条件
        ;

        public boolean is(Marker marker) {
            if (marker instanceof SyntaxMarker) {
                return ((SyntaxMarker) marker).getType() == this;
            }
            return false;
        }
    }

    private final SyntaxMarkerType type;
    private final String entityName;
    private final String alias;
    private final int shardParamIndex;
    private final Object shardValue;

    public SyntaxMarker(int textBegin, int textEnd, SyntaxMarkerType type, String entityName, String alias) {
        this(textBegin, textEnd, type, entityName, alias, -1, null);
    }

    public SyntaxMarker(int textBegin, int textEnd, SyntaxMarkerType type, String entityName, String alias,
                        int shardParamIndex, Object shardValue) {
        super(textBegin, textEnd);
        this.type = type;
        this.entityName = entityName;
        this.alias = alias;
        this.shardParamIndex = shardParamIndex;
        this.shardValue = shardValue;
    }

    public SyntaxMarker(int pos, SyntaxMarkerType type) {
        this(pos, pos + 1, type, null, null, -1, null);
    }

    public SyntaxMarkerType getType() {
        return type;
    }

    public String getEntityName() {
        return entityName;
    }

    public String getAlias() {
        return alias;
    }

    @Override
    public Marker offset(int offset) {
        if (offset == 0)
            return this;
        return new SyntaxMarker(offset + getBegin(), offset + getEnd(), type, entityName, alias, shardParamIndex,
                shardValue);
    }

    public int getShardParamIndex() {
        return shardParamIndex;
    }

    public Object getShardValue() {
        return shardValue;
    }

    public boolean hasShardParam() {
        return shardParamIndex >= 0 || shardValue != null;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        appendPos(sb).append(":{");
        sb.append("type=").append(type);
        if (entityName != null)
            sb.append(",entityName=").append(entityName);
        if (alias != null)
            sb.append(",alias=").append(alias);
        if (shardParamIndex >= 0)
            sb.append(",shardParamIndex=").append(shardParamIndex);
        if (shardValue != null)
            sb.append(",shardValue=").append(shardValue);
        return sb.append('}').toString();
    }
}