package io.nop.api.core.beans.query;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.beans.TreeBean;

import java.util.List;

@DataBean
public class QuerySourceBean {
    private String sourceName;

    private String alias;

    private TreeBean filter;
    private List<String> dimFields;

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public TreeBean getFilter() {
        return filter;
    }

    public void setFilter(TreeBean filter) {
        this.filter = filter;
    }

    public List<String> getDimFields() {
        return dimFields;
    }

    public void setDimFields(List<String> dimFields) {
        this.dimFields = dimFields;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }
}