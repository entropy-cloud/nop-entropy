package io.nop.dbtool.exp.config;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.beans.TreeBean;

@DataBean
public class SqlTableConfig {
    private String name;

    private String sql;

    private TreeBean filter;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public TreeBean getFilter() {
        return filter;
    }

    public void setFilter(TreeBean filter) {
        this.filter = filter;
    }
}
