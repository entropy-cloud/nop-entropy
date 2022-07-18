package io.nop.api.core.beans.query;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.TreeBean;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@DataBean
public class QueryBean implements Serializable {
    private static final long serialVersionUID = 6756041836462853393L;

    private String name;
    private long offset;
    private int limit;

    private String cursor;

    private List<QueryFieldBean> fields;

    private String sourceName;

    private List<String> dimFields;

    private List<QuerySourceBean> joins;

    private TreeBean filter;

    private List<OrderFieldBean> orderBy;

    private List<GroupFieldBean> groupBy;

    private Integer timeout;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public void addFilter(TreeBean filter) {
        if (filter == null)
            return;
        if (this.filter == null) {
            this.filter = filter;
        } else {
            this.filter = FilterBeans.and(this.filter, filter);
        }
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
}