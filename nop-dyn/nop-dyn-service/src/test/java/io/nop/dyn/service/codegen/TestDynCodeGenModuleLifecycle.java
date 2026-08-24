/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dyn.service.codegen;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.biz.impl.BizObjectManager;
import io.nop.commons.type.StdSqlType;
import io.nop.core.module.ModuleManager;
import io.nop.core.module.ModuleModel;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dyn.dao.NopDynDaoConstants;
import io.nop.dyn.dao.entity.NopDynApp;
import io.nop.dyn.dao.entity.NopDynAppModule;
import io.nop.dyn.dao.entity.NopDynEntityMeta;
import io.nop.dyn.dao.entity.NopDynModule;
import io.nop.dyn.dao.entity.NopDynPropMeta;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.model.IOrmModel;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模块发布/下架在 codeCache 中的 key 语义（key是nopModuleId而非moduleName），以及未填precision的动态列缺省精度
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestDynCodeGenModuleLifecycle extends JunitBaseTestCase {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    DynCodeGen codeGen;

    @Inject
    IOrmTemplate ormTemplate;

    @Inject
    DynOrmModelProvider ormModelHolder;

    @Inject
    BizObjectManager bizObjectManager;

    @BeforeEach
    public void init(TestInfo testInfo) {
        super.init(testInfo);
        VirtualFileSystem.instance().updateInMemoryLayer(null);
        ModuleManager.instance().updateDynamicModules(null);
        bizObjectManager.clearCache();
    }

    /**
     * moduleName=app-demo 时缓存key是 nopModuleId=app/demo（'-'转'/'）。
     * generateForApp 对未发布模块必须用 nopModuleId 移除缓存，传 moduleName 是 no-op，下架形同虚设
     */
    @Test
    public void testGenerateForAppRemovesUnpublishedModule() {
        // 整个流程放在同一个session中：reloadModel会重建session factory，跨session复用实体会报session已关闭
        ormTemplate.runInSession(() -> {
            NopDynModule module = saveModule("app-demo", false);
            codeGen.generateForAllModules();

            String nopModuleId = module.getNopModuleId();
            assertEquals("app/demo", nopModuleId, "moduleName的'-'会被替换为'/'得到缓存key");
            assertTrue(codeGen.getCodeCache().getEnabledModules().containsKey(nopModuleId),
                    "发布后模块在缓存中");

            // 组装 app→module 映射，模块转为未发布状态
            NopDynApp app = daoProvider.daoFor(NopDynApp.class).newEntity();
            app.setAppName("test-app");
            app.setDisplayName("Test App");
            app.setAppVersion(1);
            app.setStatus(NopDynDaoConstants.APP_STATUS_PUBLISHED);

            NopDynAppModule mapping = daoProvider.daoFor(NopDynAppModule.class).newEntity();
            mapping.setApp(app);
            mapping.setModule(module);
            app.getModuleMappings().add(mapping);
            daoProvider.daoFor(NopDynApp.class).saveEntity(app);
            daoProvider.daoFor(NopDynAppModule.class).saveEntity(mapping);

            module.setStatus(NopDynDaoConstants.MODULE_STATUS_UNPUBLISHED);

            codeGen.generateForApp(app);

            assertFalse(codeGen.getCodeCache().getEnabledModules().containsKey(nopModuleId),
                    "未发布模块必须从缓存中移除（修复前错误使用moduleName作为key，removeModule静默no-op）");
        });
    }

    /**
     * 真实表实体未填precision的VARCHAR列，生成的ORM列精度必须是安全缺省值100，而不是VARCHAR(1)
     */
    @Test
    public void testRealTableColumnDefaultPrecision() {
        ormTemplate.runInSession(() -> {
            saveModule("prec-demo", true);
            codeGen.generateForAllModules();

            ModuleModel moduleModel = codeGen.getCodeCache().requireEnabledModule("prec/demo");
            IOrmModel ormModel = codeGen.getCodeCache().getOrmModel(moduleModel, false);
            assertEquals(100, ormModel.getEntityModel("test.MyRealEntity").getColumn("title", true).getPrecision(),
                    "未填precision的VARCHAR列必须使用安全缺省精度，修复前为1（VARCHAR(1)截断数据）");
            assertEquals(38, ormModel.getEntityModel("test.MyRealEntity").getColumn("amount", true).getPrecision(),
                    "未填precision的DECIMAL列缺省精度38，修复前为1");
            assertEquals(50, ormModel.getEntityModel("test.MyRealEntity").getColumn("code", true).getPrecision(),
                    "显式指定的precision必须保留");
        });
    }

    private NopDynModule saveModule(String moduleName, boolean realTable) {
        NopDynModule module = new NopDynModule();
        module.setModuleName(moduleName);
        module.setDisplayName("Demo Module");
        module.setStatus(NopDynDaoConstants.MODULE_STATUS_PUBLISHED);

        NopDynEntityMeta entityMeta = new NopDynEntityMeta();
        entityMeta.setEntityName("test.MyRealEntity");
        entityMeta.setDisplayName("My Real Entity");
        entityMeta.setModule(module);
        entityMeta.setStatus(NopDynDaoConstants.MODULE_STATUS_PUBLISHED);
        entityMeta.setIsExternal(false);
        entityMeta.setStoreType(realTable ? NopDynDaoConstants.ENTITY_STORE_TYPE_REAL
                : NopDynDaoConstants.ENTITY_STORE_TYPE_VIRTUAL);

        if (realTable) {
            // title 不填 precision
            addProp(entityMeta, "title", StdSqlType.VARCHAR, null);
            // amount 不填 precision 的 DECIMAL
            addProp(entityMeta, "amount", StdSqlType.DECIMAL, null);
            // 显式指定 precision=50 的对照列
            addProp(entityMeta, "code", StdSqlType.VARCHAR, 50);
            addProp(entityMeta, "value", StdSqlType.INTEGER, 0);
        } else {
            NopDynPropMeta prop = addProp(entityMeta, "name", StdSqlType.VARCHAR, 100);
            prop.setDynPropMapping(io.nop.dyn.dao.entity.NopDynEntity.PROP_NAME_nopName);
            addProp(entityMeta, "value", StdSqlType.INTEGER, 0);
        }

        module.getEntityMetas().add(entityMeta);

        IEntityDao<NopDynModule> dao = daoProvider.daoFor(NopDynModule.class);
        dao.saveEntity(module);
        // 同session内的查询需要先flush才能看到新增数据
        dao.flushSession();
        return module;
    }

    private NopDynPropMeta addProp(NopDynEntityMeta entityMeta, String propName, StdSqlType sqlType, Integer precision) {
        NopDynPropMeta propMeta = new NopDynPropMeta();
        propMeta.setPropId(1);
        propMeta.setPropName(propName);
        propMeta.setDisplayName(propName + " Display");
        propMeta.setPrecision(precision);
        propMeta.setIsMandatory(true);
        propMeta.setStatus(1);
        propMeta.setStdSqlType(sqlType.getName());
        entityMeta.getPropMetas().add(propMeta);
        return propMeta;
    }
}
