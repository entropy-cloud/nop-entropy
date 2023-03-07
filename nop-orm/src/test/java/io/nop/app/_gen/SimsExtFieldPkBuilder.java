package io.nop.app._gen;

import io.nop.app.SimsExtField;
import io.nop.orm.support.OrmCompositePk;

/**
 * 用于生成复合主键的帮助类
 */
public class SimsExtFieldPkBuilder {
    private Object[] values = new Object[3];

    public SimsExtFieldPkBuilder setEntityName(java.lang.String value) {
        this.values[0] = value;
        return this;
    }

    public SimsExtFieldPkBuilder setEntityId(java.lang.String value) {
        this.values[1] = value;
        return this;
    }

    public SimsExtFieldPkBuilder setFieldName(java.lang.String value) {
        this.values[2] = value;
        return this;
    }

    public OrmCompositePk build() {
        return OrmCompositePk.buildNotNull(SimsExtField.PK_PROP_NAMES, values);
    }
}
