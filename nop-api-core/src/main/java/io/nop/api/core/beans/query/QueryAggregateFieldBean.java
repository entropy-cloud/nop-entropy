package io.nop.api.core.beans.query;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.util.ICloneable;

@DataBean
public class QueryAggregateFieldBean implements ICloneable {
    private String aggFunc;
    private String name;
    private String sourceField;
    private String formula;
    private TreeBean filter;

    @Override
    public QueryAggregateFieldBean cloneInstance() {
        QueryAggregateFieldBean ret = new QueryAggregateFieldBean();
        ret.setFormula(getFormula());
        ret.setName(getName());
        ret.setSourceField(getSourceField());
        ret.setAggFunc(getAggFunc());
        ret.setFilter(filter == null ? null : filter.cloneInstance());
        return ret;
    }

    public TreeBean getFilter() {
        return filter;
    }

    public void setFilter(TreeBean filter) {
        this.filter = filter;
    }

    public String getAggFunc() {
        return aggFunc;
    }

    public void setAggFunc(String aggFunc) {
        this.aggFunc = aggFunc;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSourceField() {
        return sourceField;
    }

    public void setSourceField(String sourceField) {
        this.sourceField = sourceField;
    }

    public String getFormula() {
        return formula;
    }

    public void setFormula(String formula) {
        this.formula = formula;
    }
}
