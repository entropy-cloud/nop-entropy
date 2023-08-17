/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.beans.query;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.annotations.graphql.GraphQLObject;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.ITreeBean;
import io.nop.api.core.beans.TreeBean;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

@DataBean
@GraphQLObject
public class QueryBean implements Serializable {
    private static final long serialVersionUID = 6756041836462853393L;

    private String name;
    private long offset;
    private int limit;

    private String cursor;
    private boolean findPrev;

    private List<QueryFieldBean> fields;

    private String sourceName;

    private List<String> dimFields;

    private List<QuerySourceBean> joins;

    private TreeBean filter;

    private List<OrderFieldBean> orderBy;

    private List<GroupFieldBean> groupBy;

    private Integer timeout;

    private boolean disableLogicalDelete;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public boolean isDisableLogicalDelete() {
        return disableLogicalDelete;
    }

    public void setDisableLogicalDelete(boolean disableLogicalDelete) {
        this.disableLogicalDelete = disableLogicalDelete;
    }

    public List<QueryFieldBean> getFields() {
        return fields;
    }

    public void setFields(List<QueryFieldBean> fields) {
        this.fields = fields;
    }

    public long getOffset() {
        return offset;
    }

    public void setOffset(long offset) {
        this.offset = offset;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public TreeBean getFilter() {
        return filter;
    }

    public void setFilter(TreeBean filter) {
        this.filter = filter;
    }

    public void addFilter(ITreeBean filter) {
        if (filter == null)
            return;

        TreeBean tree = FilterBeans.normalizeFilterBean(filter);

        if (this.filter == null) {
            this.filter = tree;
        } else {
            this.filter = FilterBeans.and(this.filter, tree);
        }
    }

    public TreeBean getPropFilter(String propName) {
        if (filter == null)
            return null;

        if (Objects.equals(propName, filter.getAttr("name")))
            return filter;

        return filter.childWithAttr("name", propName);
    }

    public boolean transformFilter(Function<TreeBean, ?> fn) {
        if (filter == null)
            return false;

        TreeBean node = new TreeBean();
        node.addChild(filter);
        boolean b = node.transformChild(null, fn, true);
        if (node.getChildCount() == 1) {
            filter = node.getChildren().get(0);
        } else {
            filter = node;
        }
        return b;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Integer getTimeout() {
        return timeout;
    }

    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    public List<OrderFieldBean> getOrderBy() {
        return orderBy;
    }

    public void setOrderBy(List<OrderFieldBean> orderBy) {
        this.orderBy = orderBy;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getCursor() {
        return cursor;
    }

    public void setCursor(String cursor) {
        this.cursor = cursor;
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<String> getDimFields() {
        return dimFields;
    }

    public void setDimFields(List<String> dimFields) {
        this.dimFields = dimFields;
    }

    public List<QuerySourceBean> getJoins() {
        return joins;
    }

    public void setJoins(List<QuerySourceBean> joins) {
        this.joins = joins;
    }

    public List<GroupFieldBean> getGroupBy() {
        return groupBy;
    }

    public void setGroupBy(List<GroupFieldBean> groupBy) {
        this.groupBy = groupBy;
    }

    public boolean hasGroupBy() {
        return groupBy != null && !groupBy.isEmpty();
    }

    public boolean hasOrderBy() {
        return orderBy != null && !orderBy.isEmpty();
    }

    public GroupFieldBean getGroupField(String name) {
        if (groupBy != null) {
            for (GroupFieldBean field : groupBy) {
                if (name.equals(field.getName()))
                    return field;
            }
        }
        return null;
    }

    public boolean hasGroupField(String name) {
        return getGroupField(name) != null;
    }

    public void addGroupField(String name) {
        if (!hasGroupField(name)) {
            if (groupBy == null)
                groupBy = new ArrayList<>();
            groupBy.add(GroupFieldBean.forField(name));
        }
    }

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public boolean isFindPrev() {
        return findPrev;
    }

    public void setFindPrev(boolean findPrev) {
        this.findPrev = findPrev;
    }

    public OrderFieldBean getOrderField(String name) {
        if (orderBy == null)
            return null;
        for (OrderFieldBean field : orderBy) {
            if (field.getName().equals(name))
                return field;
        }
        return null;
    }

    public boolean hasOrderField(String name) {
        return getOrderField(name) != null;
    }

    public void addOrderField(String name, boolean desc) {
        if (!hasOrderField(name)) {
            if (orderBy == null)
                orderBy = new ArrayList<>();
            orderBy.add(OrderFieldBean.forField(name, desc));
        }
    }

    public void addOrderField(OrderFieldBean field) {
        if (!hasOrderField(field.getName())) {
            if (orderBy == null)
                orderBy = new ArrayList<>();
            orderBy.add(field);
        }
    }

    public void addOrderBy(List<OrderFieldBean> orderBy) {
        if (orderBy == null)
            return;
        for (OrderFieldBean orderField : orderBy) {
            if (!hasOrderField(orderField.getName())) {
                if (this.orderBy == null)
                    this.orderBy = new ArrayList<>();
                this.orderBy.add(orderField);
            }
        }
    }

    public void addOrderByNode(ITreeBean orderBy) {
        if (orderBy != null) {
            List<? extends ITreeBean> children = orderBy.getChildren();
            if (children != null) {
                for (ITreeBean child : children) {
                    addOrderField(OrderFieldBean.fromTreeBean(child));
                }
            }
        }
    }
}