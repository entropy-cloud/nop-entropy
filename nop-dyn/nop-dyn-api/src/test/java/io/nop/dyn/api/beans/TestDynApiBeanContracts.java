package io.nop.dyn.api.beans;

import io.nop.api.core.api.CrudInputBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 结构性契约测试：nop-dyn-api 的动态建模元数据 bean（InputBean/OutputBean）为 codegen
 * 生成的数据类。固化动态实体元数据的关键语义字段：实体名/表名/存储类型/外部表标记、
 * 属性元数据的 SQL 类型/精度/主键序号。
 */
public class TestDynApiBeanContracts {

    @Test
    public void testEntityMetaInputBeanRoundTrip() {
        NopDynEntityMetaInputBean bean = new NopDynEntityMetaInputBean();
        assertNull(bean.getTableName(), "未赋值属性必须返回 null");
        bean.setEntityName("my.Customer");
        bean.setDisplayName("客户");
        bean.setTableName("my_customer");
        bean.setStoreType(0);
        bean.setIsExternal(Boolean.FALSE);
        bean.setStatus(1);
        bean.setTagsText("dyn");

        assertEquals("my.Customer", bean.getEntityName());
        assertEquals("客户", bean.getDisplayName());
        assertEquals("my_customer", bean.getTableName());
        assertEquals(0, bean.getStoreType());
        assertEquals(Boolean.FALSE, bean.getIsExternal(), "isExternal=false 表示动态建实体而非引用外部表");
        assertEquals(1, bean.getStatus());
    }

    @Test
    public void testEntityMetaOutputBeanCarriesAggregateAndLabels() {
        NopDynEntityMetaOutputBean bean = new NopDynEntityMetaOutputBean();
        bean.setEntityName("my.Customer");
        bean.setStoreType(0);
        bean.setStoreType_label("虚拟表");
        bean.setStatus(1);
        bean.setStatus_label("有效");
        bean.setPropMetas(java.util.Arrays.asList(java.util.Collections.singletonMap("propName", "custName")));

        assertEquals("my.Customer", bean.getEntityName());
        assertEquals("虚拟表", bean.getStoreType_label(), "OutputBean 必须携带字典翻译标签");
        assertEquals("有效", bean.getStatus_label());
        assertEquals(1, bean.getPropMetas().size(),
                "OutputBean 聚合属性元数据集合");
        assertEquals("custName", bean.getPropMetas().get(0).get("propName"));
    }

    @Test
    public void testPropMetaInputBeanCarriesColumnSemantics() {
        NopDynPropMetaInputBean bean = new NopDynPropMetaInputBean();
        bean.setPropName("custName");
        bean.setDisplayName("客户名");
        bean.setStdSqlType("VARCHAR");
        bean.setPrecision(100);
        bean.setScale(null);
        bean.setPropId(2);
        bean.setIsMandatory(Boolean.TRUE);

        assertEquals("custName", bean.getPropName());
        assertEquals("客户名", bean.getDisplayName());
        assertEquals("VARCHAR", bean.getStdSqlType(), "动态属性的标准 SQL 类型决定生成的列类型");
        assertEquals(100, bean.getPrecision());
        assertEquals(2, bean.getPropId());
        assertEquals(Boolean.TRUE, bean.getIsMandatory());
    }

    @Test
    public void testInputBeansExtendCrudInputBase() {
        assertTrue(CrudInputBase.class.isAssignableFrom(NopDynEntityMetaInputBean.class),
                "InputBean 必须继承 CrudInputBase 以携带分页/查询参数");
        assertTrue(CrudInputBase.class.isAssignableFrom(NopDynPropMetaInputBean.class),
                "InputBean 必须继承 CrudInputBase 以携带分页/查询参数");
    }
}
