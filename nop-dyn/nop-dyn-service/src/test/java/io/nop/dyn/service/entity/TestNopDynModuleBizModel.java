package io.nop.dyn.service.entity;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dyn.dao.entity.NopDynEntity;
import io.nop.orm.IOrmEntitySet;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.support.DynamicOrmKeyValueTable;
import io.nop.sys.dao.entity.NopSysExtField;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopDynModuleBizModel extends JunitBaseTestCase {

    @Inject
    IDaoProvider daoProvider;
    @Inject
    NopDynModuleBizModel bizModel;

    @Inject
    IOrmTemplate ormTemplate;

    @Test
    public void testGenerateByAI() {
        String ormText = attachmentText("test.orm.xml");

        ormTemplate.runInSession(() -> {
            bizModel.generateByAI(ormText);
        });
    }


    /**
     * 复现栈溢出
     */
    @Test
    public void testExtQuery_error() {
        IEntityDao<NopDynEntity> dao = daoProvider.daoFor(NopDynEntity.class);

        NopDynEntity entity = dao.newEntity();
        entity.setSid("1");
        entity.setNopObjType("test");
        entity.getExtFields().prop_set("fldA", "123");
        // 别名的作用
        assertEquals("123", entity.prop_get("extFldA"));


        dao.saveEntity(entity);
    }

    @Test
    public void testExtQuery() {
        IEntityDao<NopDynEntity> dao = daoProvider.daoFor(NopDynEntity.class);

        NopDynEntity entity = dao.newEntity();
        entity.setSid("1");
        entity.setNopObjType("test");
        BeanTool.setComplexProperty(entity, "extFields.fldA.string", "test");


        dao.saveEntity(entity);

        ormTemplate.runInSession(() -> {
            NopDynEntity dynEntity = dao.getEntityById("1");
            IOrmEntitySet<DynamicOrmKeyValueTable> extFields = dynEntity.getExtFields();
            assertTrue(extFields.size() == 1, "有一条加的");
        });

//        NopDynEntity queryEntity = new NopDynEntity();
//        queryEntity.getExtFields().prop_set("fldA", "test");
//        NopDynEntity firstByExample = dao.findFirstByExample(queryEntity);
//        assertTrue(firstByExample != null, "一定能查到");

        QueryBean query = new QueryBean();
        query.setFilter(FilterBeans.eq("extFields.fldA.string", "test"));
        NopDynEntity entity2 = dao.findFirstByQuery(query);
        System.out.println(entity2);
    }
}