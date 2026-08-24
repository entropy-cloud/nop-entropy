package io.nop.dyn.service.entity;

import io.nop.api.core.beans.DictOptionBean;
import io.nop.commons.type.StdSqlType;
import io.nop.dyn.dao.entity.NopDynEntityMeta;
import io.nop.dyn.dao.entity.NopDynPropMeta;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 主键属性下拉选项的value必须是ORM模型中的主键别名"id"，label为展示名"ID"
 */
public class TestNopDynEntityMetaBizModel {

    @Test
    public void testAllPropsPrimaryKeyOption() {
        NopDynEntityMetaBizModel bizModel = new NopDynEntityMetaBizModel();

        NopDynEntityMeta entityMeta = new NopDynEntityMeta();
        entityMeta.setEntityName("test.MyEntity");

        NopDynPropMeta propMeta = new NopDynPropMeta();
        propMeta.setPropName("name");
        propMeta.setDisplayName("名称 Display");
        propMeta.setStdSqlType(StdSqlType.VARCHAR.getName());
        entityMeta.getPropMetas().add(propMeta);

        List<DictOptionBean> options = bizModel.allProps(entityMeta);

        assertEquals(2, options.size());
        DictOptionBean idOption = options.get(0);
        // value是提交给后端的过滤/映射属性名，必须是主键别名"id"；修复前第二次setValue("ID")会覆盖value且label缺失
        assertEquals("id", idOption.getValue());
        assertEquals("ID", idOption.getLabel());

        DictOptionBean propOption = options.get(1);
        assertEquals("name", propOption.getValue());
        assertEquals("名称 Display", propOption.getLabel());
    }
}
