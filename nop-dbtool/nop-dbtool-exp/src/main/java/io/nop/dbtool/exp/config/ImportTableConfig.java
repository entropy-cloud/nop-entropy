package io.nop.dbtool.exp.config;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.beans.TreeBean;

@DataBean
public class ImportTableConfig {
    private String name;
    private TreeBean filter;
    private String from;

    private String format;

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public TreeBean getFilter() {
        return filter;
    }

    public void setFilter(TreeBean filter) {
        this.filter = filter;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }
}
