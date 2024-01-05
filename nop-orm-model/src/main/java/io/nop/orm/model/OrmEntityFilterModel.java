package io.nop.orm.model;

import io.nop.api.core.util.Guard;
import io.nop.orm.model._gen._OrmEntityFilterModel;

public class OrmEntityFilterModel extends _OrmEntityFilterModel {
    private OrmColumnModel column;

    public OrmEntityFilterModel() {

    }

    public static OrmEntityFilterModel of(String colName, Object value) {
        OrmEntityFilterModel ret = new OrmEntityFilterModel();
        ret.setName(colName);
        ret.setValue(value);
        return ret;
    }

    public OrmColumnModel getColumn() {
        return column;
    }

    public void setColumn(OrmColumnModel column) {
        checkAllowChange();
        this.column = column;
    }

    public String getColCode() {
        return Guard.notNull(column, "column").getCode();
    }
}
