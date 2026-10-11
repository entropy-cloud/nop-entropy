package io.nop.dyn.service.entity;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.dyn.dao.entity.NopDynModule;
import io.nop.dyn.service.NopDynErrors;
import io.nop.dao.api.IEntityDao;
import io.nop.dyn.dao.entity.NopDynEntity;
import io.nop.orm.IOrmEntitySet;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.support.DynamicOrmKeyValueTable;
import io.nop.sys.dao.entity.NopSysExtField;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static io.nop.dyn.service.NopDynErrors.ERR_DYN_MODULE_NAME_EXISTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
            bizModel.generateByAI(ormText, null, null);
        });
    }

    /**
     * P1-3.7-001（plan 2385）：moduleName 为平台保留前缀（nop-*，即静态模块命名模式）时
     * generateByAI 必须拒绝——否则动态模块资源经 VFS in-memory 第一优先层遮蔽静态模块
     * xbiz/xmeta/orm（口令哈希直读/XPL 注入/ORM 定义替换）。
     */
    @Test
    public void testGenerateByAIReservedPrefixRejected() {
        String ormText = attachmentText("test.orm.xml");

        NopException ex = assertThrows(NopException.class, () -> ormTemplate.runInSession(() -> {
            bizModel.generateByAI(ormText, "nop-auth", null);
        }));
        assertEquals(NopDynErrors.ERR_DYN_MODULE_ID_CONFLICT.getErrorCode(), ex.getErrorCode(),
                "reserved nop- prefix moduleName must be rejected with module-id-conflict");
    }

    /**
     * P1-3.7-001（plan 2385）：save 路径 moduleName="nop-auth"（与已注册静态模块 id 冲突）
     * 必须拒绝——VFS 中已存在 /nop/auth/_module 静态模块标记。
     */
    @Test
    public void testSaveStaticModuleIdConflictRejected() {
        ormTemplate.runInSession(() -> {
            NopDynModule module = daoProvider.daoFor(NopDynModule.class).newEntity();
            module.setModuleName("nop-auth");
            module.setDisplayName("shadow");
            module.setModuleVersion(1);
            module.setStatus(io.nop.dyn.dao.NopDynDaoConstants.MODULE_STATUS_UNPUBLISHED);

            NopException ex = assertThrows(NopException.class,
                    () -> bizModel.saveEntity(module, null, null));
            assertEquals(NopDynErrors.ERR_DYN_MODULE_ID_CONFLICT.getErrorCode(), ex.getErrorCode(),
                    "moduleName conflicting with registered static module must be rejected on save");
        });
    }

    @Test
    public void testGenerateByAIDuplicateModuleNameRejected() {
        String ormText = attachmentText("test.orm.xml");

        ormTemplate.runInSession(() -> {
            bizModel.generateByAI(ormText, null, null);
        });

        // moduleName无唯一索引，重复调用必须显式拒绝，否则会创建并发布多个同名模块
        NopException ex = assertThrows(NopException.class, () -> ormTemplate.runInSession(() -> {
            bizModel.generateByAI(ormText, null, null);
        }));
        assertEquals(ERR_DYN_MODULE_NAME_EXISTS.getErrorCode(), ex.getErrorCode());
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