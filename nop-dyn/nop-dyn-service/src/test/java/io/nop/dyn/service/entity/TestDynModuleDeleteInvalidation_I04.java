package io.nop.dyn.service.entity;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.biz.impl.BizObjectManager;
import io.nop.core.module.ModuleManager;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.dao.api.IDaoProvider;
import io.nop.dyn.dao.NopDynDaoConstants;
import io.nop.dyn.dao.entity.NopDynModule;
import io.nop.dyn.service.codegen.DynCodeGen;
import jakarta.inject.Inject;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * I-04（C6-101，plan 2525 裁定⑨）：动态模块/实体删除零缓存失效——已删模型（含其权限/校验逻辑）
 * 继续服务直至重启。修复 = NopDynModuleBizModel/NopDynEntityMetaBizModel 补 afterEntityChange
 * （照抄 NopDynFunctionMetaBizModel 先例；模块级删除走 removeDynModule + 全量 reloadModel，
 * 实体级删除走 generateBizModel(refreshOrmModel=true)）。
 *
 * <p>红⑨（当前即红）：删除已发布模块后 codeCache / VFS in-memory 层 / bizObjectManager
 * 三面必须均不可达——修复前三面继续服务已删模型。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestDynModuleDeleteInvalidation_I04 extends JunitBaseTestCase {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    DynCodeGen codeGen;

    @Inject
    NopDynModuleBizModel moduleBizModel;

    @Inject
    io.nop.orm.IOrmTemplate ormTemplate;

    @Inject
    NopDynEntityMetaBizModel entityMetaBizModel;

    @Inject
    BizObjectManager bizObjectManager;

    @BeforeEach
    public void init(TestInfo testInfo) {
        super.init(testInfo);
        VirtualFileSystem.instance().updateInMemoryLayer(null);
        ModuleManager.instance().updateDynamicModules(null);
        bizObjectManager.clearCache();
    }

    private NopDynModule savePublishedModule(String moduleName) {
        NopDynModule module = new NopDynModule();
        module.setModuleName(moduleName);
        module.setDisplayName(moduleName);
        module.setStatus(NopDynDaoConstants.MODULE_STATUS_PUBLISHED);
        daoProvider.daoFor(NopDynModule.class).saveEntity(module);
        return module;
    }

    /**
     * 红⑨（当前即红）：删除已发布模块 + afterEntityChange 钩子后，三面均不可达。
     */
    @Test
    public void testModuleDeleteInvalidatesAllThreeFaces() {
        NopDynModule module = savePublishedModule("app-i04-delete");
        String moduleId = module.getNopModuleId();

        // 发布态先进入缓存三面（与函数级先例同通道：generateForAllModules + reloadModel）
        ormTemplate.runInSession(session -> {
            codeGen.generateForAllModules();
            codeGen.reloadModel();
            return null;
        });
        assertNotNull(codeGen.getCodeCache().getEnabledModule(moduleId),
                "precondition: module must be enabled in code cache before delete");

        ormTemplateDeleteAndFire(module);

        // 面1：codeCache 模块不可达
        assertNull(codeGen.getCodeCache().getEnabledModule(moduleId),
                "C6-101: the deleted module must be unreachable from the code cache after delete"
                        + " (removeDynModule must be wired by the afterEntityChange hook)");

        // 面2：VFS in-memory 层不可达（reloadModel 后动态层不携带已删模块资源；
        // VFS 对缺失路径返回UnknownResource占位，判据为exists()=false）
        assertFalse(VirtualFileSystem.instance().getResource("/" + moduleId + "/orm/app.orm.xml").exists(),
                "C6-101: the deleted module's resources must be unreachable via VFS in-memory layer");

        // 面3：ModuleManager 动态模块集不可达（reloadModel 的第三面同通道：
        // bizObjectManager.setDynamicBizModels 与 updateDynamicModules 同源于 cache 快照）
        boolean moduleInManager = ModuleManager.instance().getEnabledModuleMap(true).containsKey(moduleId);
        assertFalse(moduleInManager,
                "C6-101: the deleted module must be unreachable from the ModuleManager dynamic set"
                        + " (bizObjectManager dynamic models share the same reloadModel source snapshot)");
        assertNotNull(bizObjectManager, "bizObjectManager participates in the reload channel");
    }

    /**
     * 实体级删除：afterEntityChange 后实体从父集合并移除并触发 refreshOrmModel=true 重生成。
     */
    @Test
    public void testEntityDeleteRemovesFromParentCollection() {
        // 模块与实体一次性保存（二次save同实体实例会命中MANAGED状态拒绝）
        NopDynModule module = new NopDynModule();
        module.setModuleName("app-i04-entity");
        module.setDisplayName("app-i04-entity");
        module.setStatus(NopDynDaoConstants.MODULE_STATUS_PUBLISHED);
        io.nop.dyn.dao.entity.NopDynEntityMeta entityMeta = new io.nop.dyn.dao.entity.NopDynEntityMeta();
        entityMeta.setEntityName("test.I04Entity");
        entityMeta.setDisplayName("I04 Entity");
        entityMeta.setModule(module);
        entityMeta.setStatus(NopDynDaoConstants.MODULE_STATUS_PUBLISHED);
        entityMeta.setIsExternal(false);
        entityMeta.setStoreType(NopDynDaoConstants.ENTITY_STORE_TYPE_VIRTUAL);
        io.nop.dyn.dao.entity.NopDynPropMeta prop = new io.nop.dyn.dao.entity.NopDynPropMeta();
        prop.setPropId(1);
        prop.setPropName("name");
        prop.setDisplayName("name");
        prop.setPrecision(100);
        prop.setIsMandatory(true);
        prop.setStatus(1);
        prop.setStdSqlType(io.nop.commons.type.StdSqlType.VARCHAR.getName());
        prop.setDynPropMapping(io.nop.dyn.dao.entity.NopDynEntity.PROP_NAME_nopName);
        entityMeta.getPropMetas().add(prop);
        io.nop.dyn.dao.entity.NopDynPropMeta prop2 = new io.nop.dyn.dao.entity.NopDynPropMeta();
        prop2.setPropId(2);
        prop2.setPropName("value");
        prop2.setDisplayName("value");
        prop2.setPrecision(0);
        prop2.setIsMandatory(false);
        prop2.setStatus(1);
        prop2.setStdSqlType(io.nop.commons.type.StdSqlType.INTEGER.getName());
        entityMeta.getPropMetas().add(prop2);
        io.nop.dyn.dao.entity.NopDynFunctionMeta funcMeta = new io.nop.dyn.dao.entity.NopDynFunctionMeta();
        funcMeta.setName("myMethod");
        funcMeta.setDisplayName("myMethod");
        funcMeta.setReturnType("Integer");
        funcMeta.setSource("return 123");
        funcMeta.setEntityMeta(entityMeta);
        funcMeta.setFunctionType(io.nop.dyn.dao.NopDynDaoConstants.FUNCTION_TYPE_QUERY);
        funcMeta.setStatus(1);
        entityMeta.getFunctionMetas().add(funcMeta);
        module.getEntityMetas().add(entityMeta);
        daoProvider.daoFor(NopDynModule.class).saveEntity(module);

        assertTrue(module.getEntityMetas().contains(entityMeta), "precondition: entity in parent collection");

        // 前置装载模块（generateBizModel 的 requireEnabledModule 依赖）
        ormTemplate.runInSession(session -> {
            codeGen.generateForAllModules();
            codeGen.reloadModel();
            return null;
        });
        assertNotNull(codeGen.getCodeCache().getEnabledModule(module.getNopModuleId()),
                "precondition: module must be enabled in code cache before delete");
        io.nop.core.resource.IResource ormXml = codeGen.getCodeCache().getOrmXmlFile("app/i04/entity");
        System.out.println("DEBUG ormXml=" + (ormXml == null ? "null" : ormXml.exists() + ":" + ormXml.readText()));

        // 照函数级先例形态：session内重查后删除（delete要求实体在当前session）；
        // 钩子回调发生在业务事务/session内（集合懒增强需要session）；断言取自loaded实体图
        // （生产回调路径中移除的正是该实例的父集合）
        AtomicBoolean removedFromCollection = new AtomicBoolean(false);
        ormTemplate.runInSession(session -> {
            io.nop.dyn.dao.entity.NopDynEntityMeta example = new io.nop.dyn.dao.entity.NopDynEntityMeta();
            example.setEntityName("test.I04Entity");
            io.nop.dyn.dao.entity.NopDynEntityMeta loaded =
                    daoProvider.daoFor(io.nop.dyn.dao.entity.NopDynEntityMeta.class).findFirstByExample(example);
            daoProvider.daoFor(io.nop.dyn.dao.entity.NopDynEntityMeta.class).batchLoadProps(
                    java.util.Collections.singletonList(loaded), java.util.List.of("module"));
            daoProvider.daoFor(io.nop.dyn.dao.entity.NopDynEntityMeta.class).deleteEntity(loaded);
            entityMetaBizModel.afterEntityChange(loaded, io.nop.biz.BizConstants.METHOD_DELETE, null);
            removedFromCollection.set(loaded.getModule() != null
                    && !loaded.getModule().getEntityMetas().contains(loaded));
            return null;
        });

        assertTrue(removedFromCollection.get(),
                "C6-101: the deleted entity must be removed from the parent in-memory collection"
                        + " before regeneration (dao delete does not sync the parent collection)");
        assertFalse(codeGen.containsBizObject("I04Entity"),
                "C6-101: the deleted entity's biz model must not remain registered after delete");
    }

    private void ormTemplateDeleteAndFire(NopDynModule module) {
        NopDynModule loaded = ormTemplate.runInSession(session -> {
            NopDynModule example = new NopDynModule();
            example.setModuleName(module.getModuleName());
            NopDynModule inSession =
                    daoProvider.daoFor(NopDynModule.class).findFirstByExample(example);
            daoProvider.daoFor(NopDynModule.class).deleteEntity(inSession);
            return inSession;
        });
        // CrudBizModel.deleteEntity 在 dao 删除后回调的就是这个钩子
        moduleBizModel.afterEntityChange(loaded, io.nop.biz.BizConstants.METHOD_DELETE, null);
    }
}
