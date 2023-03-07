package test.entity._gen;

import io.nop.orm.support.OrmCompositePk;
import test.entity.TestCompositeSub;

/**
 * 用于生成复合主键的帮助类
 */
public class TestCompositeSubPkBuilder {
    private Object[] values = new Object[2];

    public TestCompositeSubPkBuilder setPartitionId(java.lang.String value) {
        this.values[0] = value;
        return this;
    }

    public TestCompositeSubPkBuilder setSid(java.lang.String value) {
        this.values[1] = value;
        return this;
    }

    public OrmCompositePk build() {
        return OrmCompositePk.buildNotNull(TestCompositeSub.PK_PROP_NAMES, values);
    }
}
