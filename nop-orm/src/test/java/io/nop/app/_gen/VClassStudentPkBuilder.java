package io.nop.app._gen;

import io.nop.app.VClassStudent;
import io.nop.orm.support.OrmCompositePk;

/**
 * 用于生成复合主键的帮助类
 */
public class VClassStudentPkBuilder {
    private Object[] values = new Object[2];

    public VClassStudentPkBuilder setStudentId(java.lang.String value) {
        this.values[0] = value;
        return this;
    }

    public VClassStudentPkBuilder setClassId(java.lang.String value) {
        this.values[1] = value;
        return this;
    }

    public OrmCompositePk build() {
        return OrmCompositePk.buildNotNull(VClassStudent.PK_PROP_NAMES, values);
    }
}
