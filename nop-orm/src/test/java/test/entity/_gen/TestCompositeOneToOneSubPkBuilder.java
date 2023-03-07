package test.entity._gen;

import io.nop.orm.support.OrmCompositePk;
import test.entity.TestCompositeOneToOneSub;

/**
 * 用于生成复合主键的帮助类
 */
public class TestCompositeOneToOneSubPkBuilder {
    private Object[] values = new Object[2];

    public TestCompositeOneToOneSubPkBuilder setFldA(java.lang.String value) {
        this.values[0] = value;
        return this;
    }

    public TestCompositeOneToOneSubPkBuilder setFldB(java.lang.String value) {
        this.values[1] = value;
        return this;
    }

    public OrmCompositePk build() {
        return OrmCompositePk.buildNotNull(TestCompositeOneToOneSub.PK_PROP_NAMES, values);
    }
}
