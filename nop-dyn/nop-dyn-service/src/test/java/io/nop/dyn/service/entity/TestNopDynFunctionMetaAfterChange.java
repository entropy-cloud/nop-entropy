/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dyn.service.entity;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.biz.BizConstants;
import io.nop.biz.impl.BizObjectManager;
import io.nop.commons.type.StdSqlType;
import io.nop.core.module.ModuleManager;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dyn.dao.NopDynDaoConstants;
import io.nop.dyn.dao.entity.NopDynEntity;
import io.nop.dyn.dao.entity.NopDynEntityMeta;
import io.nop.dyn.dao.entity.NopDynFunctionMeta;
import io.nop.dyn.dao.entity.NopDynModule;
import io.nop.dyn.dao.entity.NopDynPropMeta;
import io.nop.dyn.service.codegen.DynCodeGen;
import io.nop.graphql.core.GraphQLErrors;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.orm.IOrmTemplate;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 函数元数据的afterEntityChange钩子：delete不能被validateSource阻断且已删函数不能残留在xbiz中；
 * 函数级变更走轻量刷新（不触发ORM全量重载）后函数热更新仍生效
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopDynFunctionMetaAfterChange extends JunitBaseTestCase {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    DynCodeGen codeGen;

    @Inject
    IOrmTemplate ormTemplate;

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    NopDynFunctionMetaBizModel funcBizModel;

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
     * 删除source非法的函数不能被validateSource阻断，且删除后重新生成的xbiz不再暴露该函数
     */
    @Test
    public void testDeleteFunctionWithInvalidSource() {
        saveModuleAndGenerate();

        ormTemplate.runInSession(() -> {
            NopDynFunctionMeta func = getFuncMeta("myMethod");
            // 非法XML片段：修复前delete动作也会执行validateSource，导致函数永远删不掉
            func.setSource("<not-closed");

            daoProvider.daoFor(NopDynFunctionMeta.class).deleteEntity(func);
            // CrudBizModel.deleteEntity在dao删除后回调的就是这个钩子
            funcBizModel.afterEntityChange(func, BizConstants.METHOD_DELETE, null);

            assertTrue(func.getEntityMeta().getFunctionMetas().stream()
                    .noneMatch(f -> "myMethod".equals(f.getName())), "已删除函数必须从集合中移除");
        });

        // 重新生成的xbiz不再包含myMethod：该operation必须整体未知
        NopException ex = assertThrows(NopException.class, () -> {
            IGraphQLExecutionContext gqlContext = graphQLEngine.newRpcContext(null, "MyDynEntity__myMethod",
                    ApiRequest.build(null));
            FutureHelper.syncGet(graphQLEngine.executeRpcAsync(gqlContext));
        });
        assertEquals(GraphQLErrors.ERR_GRAPHQL_UNKNOWN_OPERATION.getErrorCode(), ex.getErrorCode(),
                "已删除函数不应继续对外提供服务（修复前被加回集合并重新生成，下架不生效）");
    }

    /**
     * 函数级变更走轻量刷新（不重建ORM session factory）后，函数源码的热更新仍然生效
     */
    @Test
    public void testUpdateFunctionStaysLiveAfterLightRefresh() {
        saveModuleAndGenerate();

        ormTemplate.runInSession(() -> {
            NopDynFunctionMeta func = getFuncMeta("myMethod");
            func.setSource("return 555");
            funcBizModel.afterEntityChange(func, BizConstants.METHOD_UPDATE, null);
        });

        IGraphQLExecutionContext gqlContext = graphQLEngine.newRpcContext(null, "MyDynEntity__myMethod",
                ApiRequest.build(null));
        ApiResponse<?> response = FutureHelper.syncGet(graphQLEngine.executeRpcAsync(gqlContext));
        assertTrue(response.isOk(), "轻量刷新路径下函数修改必须生效");
        assertEquals(555, response.getData());
    }

    private void saveModuleAndGenerate() {
        NopDynModule module = new NopDynModule();
        module.setModuleName("app-demo");
        module.setDisplayName("Demo Module");
        module.setStatus(NopDynDaoConstants.MODULE_STATUS_PUBLISHED);

        NopDynEntityMeta entityMeta = new NopDynEntityMeta();
        entityMeta.setEntityName("test.MyDynEntity");
        entityMeta.setDisplayName("My Dynamic Entity");
        entityMeta.setModule(module);
        entityMeta.setStatus(NopDynDaoConstants.MODULE_STATUS_PUBLISHED);
        entityMeta.setIsExternal(false);
        entityMeta.setStoreType(NopDynDaoConstants.ENTITY_STORE_TYPE_VIRTUAL);

        NopDynPropMeta prop = addProp(entityMeta, "name", StdSqlType.VARCHAR, 100);
        prop.setDynPropMapping(NopDynEntity.PROP_NAME_nopName);
        addProp(entityMeta, "value", StdSqlType.INTEGER, 0);

        addFunc(entityMeta, "myMethod", "return 123", "Integer");
        addFunc(entityMeta, "myMethod2", "<c:unit/>", "Object");

        module.getEntityMetas().add(entityMeta);
        daoProvider.daoFor(NopDynModule.class).saveEntity(module);

        ormTemplate.runInSession(() -> {
            codeGen.generateForAllModules();
            codeGen.reloadModel();
        });
    }

    private NopDynFunctionMeta getFuncMeta(String name) {
        IEntityDao<NopDynFunctionMeta> dao = daoProvider.daoFor(NopDynFunctionMeta.class);
        NopDynFunctionMeta example = new NopDynFunctionMeta();
        example.setName(name);
        return dao.findFirstByExample(example);
    }

    private NopDynPropMeta addProp(NopDynEntityMeta entityMeta, String propName, StdSqlType sqlType, int precision) {
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

    private void addFunc(NopDynEntityMeta entityMeta, String funcName, String source, String type) {
        NopDynFunctionMeta funcMeta = new NopDynFunctionMeta();
        funcMeta.setName(funcName);
        funcMeta.setDisplayName(funcName);
        funcMeta.setReturnType(type);
        funcMeta.setSource(source);
        funcMeta.setEntityMeta(entityMeta);
        funcMeta.setStatus(1);
        funcMeta.setFunctionType(NopDynDaoConstants.FUNCTION_TYPE_QUERY);
        entityMeta.getFunctionMetas().add(funcMeta);
    }
}
