package test.entity._gen;

import io.nop.orm.support.OrmCompositePk;
import test.entity.TestCompositeOneToOneMain;

/**
 * 用于生成复合主键的帮助类
 */
@SuppressWarnings({"PMD.UnnecessaryFullyQualifiedName"})
public class TestCompositeOneToOneMainPkBuilder{
    private Object[] values = new Object[2];

   
    public TestCompositeOneToOneMainPkBuilder setFldA(java.lang.String value){
        this.values[0] = value;
        return this;
    }
   
    public TestCompositeOneToOneMainPkBuilder setFldB(java.lang.String value){
        this.values[1] = value;
        return this;
    }
   

    public OrmCompositePk build(){
        return OrmCompositePk.buildNotNull(TestCompositeOneToOneMain.PK_PROP_NAMES,values);
    }
}
