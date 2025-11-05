package test.entity._gen;

import io.nop.orm.support.OrmCompositePk;
import test.entity.TestCompositeTable;

/**
 * 用于生成复合主键的帮助类
 */
@SuppressWarnings({"PMD.UnnecessaryFullyQualifiedName"})
public class TestCompositeTablePkBuilder{
    private Object[] values = new Object[2];

   
    public TestCompositeTablePkBuilder setPartitionId(java.lang.String value){
        this.values[0] = value;
        return this;
    }
   
    public TestCompositeTablePkBuilder setSid(java.lang.String value){
        this.values[1] = value;
        return this;
    }
   

    public OrmCompositePk build(){
        return OrmCompositePk.buildNotNull(TestCompositeTable.PK_PROP_NAMES,values);
    }
}
