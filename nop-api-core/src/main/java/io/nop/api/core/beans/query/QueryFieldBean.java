package io.nop.api.core.beans.query;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.util.ICloneable;

@DataBean
public class QueryFieldBean implements ICloneable {
    private String owner;
    private String name;

    /**
     * 选择出数据后对应的别名
     */
    private String alias;
    private String aggFunc;

    private boolean internal;

    public static QueryFieldBean forField(String name) {
        QueryFieldBean field = new QueryFieldBean();
        field.setName(name);
        return field;
    }

    public QueryFieldBean cloneInstance() {
        QueryFieldBean field = new QueryFieldBean();
        field.setOwner(owner);
        field.setName(name);
        field.setAlias(alias);
        field.setAggFunc(aggFunc);
        return field;
    }

    public QueryFieldBean cloneExceptOwner() {
        QueryFieldBean field = cloneInstance();
        field.setOwner(null);
        return field;
    }

    public boolean isInternal() {
        return internal;
    }

    public void setInternal(boolean internal) {
        this.internal = internal;
    }

    @JsonIgnore
    public String getLabel() {
        if (alias != null)
            return alias;
        if (aggFunc != null) {
            return aggFunc + '(' + name + ')';
        }
        return name;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getAggFunc() {
        return aggFunc;
    }

    public void setAggFunc(String aggFunc) {
        this.aggFunc = aggFunc;
    }
}