/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.beans.query;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.annotations.graphql.GraphQLObject;
import io.nop.api.core.annotations.meta.PropMeta;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.util.ICloneable;

import java.util.ArrayList;
import java.util.List;

@DataBean
@GraphQLObject
public class QuerySourceBean implements ICloneable {
    private String sourceName;

    private String alias;

    private TreeBean filter;
    private List<String> dimFields;

    @Override
    public QuerySourceBean cloneInstance() {
        QuerySourceBean bean = new QuerySourceBean();
        bean.setSourceName(sourceName);
        bean.setAlias(alias);
        if (filter != null)
            bean.setFilter(filter.cloneInstance());
        if (dimFields != null)
            bean.setDimFields(new ArrayList<>(dimFields));
        return bean;
    }

    @PropMeta(propId = 1)
    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    @PropMeta(propId = 2)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public TreeBean getFilter() {
        return filter;
    }

    public void setFilter(TreeBean filter) {
        this.filter = filter;
    }

    @PropMeta(propId = 3)
    public List<String> getDimFields() {
        return dimFields;
    }

    public void setDimFields(List<String> dimFields) {
        this.dimFields = dimFields;
    }

    @PropMeta(propId = 4)
    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }
}