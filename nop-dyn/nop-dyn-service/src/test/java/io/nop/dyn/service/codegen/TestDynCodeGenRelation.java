/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dyn.service.codegen;

import io.nop.api.core.ApiConfigs;
import io.nop.api.core.annotations.autotest.EnableSnapshot;
import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.autotest.SnapshotTest;
import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.util.FutureHelper;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.commons.type.StdSqlType;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.unittest.BaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dyn.dao.NopDynDaoConstants;
import io.nop.dyn.dao.entity.NopDynEntityMeta;
import io.nop.dyn.dao.entity.NopDynEntityRelationMeta;
import io.nop.dyn.dao.entity.NopDynModule;
import io.nop.dyn.dao.entity.NopDynPropMeta;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLDocument;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.model.OrmRelationType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;


@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestDynCodeGenRelation extends JunitAutoTestCase {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    DynCodeGen codeGen;

    @Inject
    IOrmTemplate ormTemplate;

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IJdbcTemplate jdbcTemplate;

    public void testGen(OrmRelationType relationType) {
        ormTemplate.runInSession(() -> {
            saveRelModule(relationType);
            codeGen.generateForAllModules();
            codeGen.reloadModel();
        });
    }

    @Test
    @Description("一对一是一种特殊形式的多对一，可以通过设置唯一约束来实现")
    public void testOneToOneRelation() {
        this.testManyToOneRelation();
    }

    /**
     * 自动录制的input实体RoleEntity实际上是动态创建的，所以录制完成后需要删除input目录下的role-entity.csv等，否则重放的时候会报实体不存在。
     */
    @Test
    public void testManyToOneRelation() {

        testGen(OrmRelationType.m2o);

        ApiRequest<?> request = new ApiRequest<>();
        request.setSelection(FieldSelectionBean.fromProp("userName", "userAge", "userRole.roleName"));
        IGraphQLExecutionContext gqlContext = graphQLEngine.newRpcContext(GraphQLOperationType.query,
                "UserEntity__findList",
                request);
        @SuppressWarnings("unchecked")
        ApiResponse<?> response = FutureHelper.syncGet(graphQLEngine.executeRpcAsync(gqlContext));
        assertEquals(true, response.isOk());
        List<?> items = BeanTool.castBeanToType(response.getData(), List.class);
        assertEquals(2, items.size());
    }

    @Test
    public void testOneToManyRelation() {

        testGen(OrmRelationType.o2m);

        traceGraphQL();

        ApiRequest<?> request = new ApiRequest<>();
        request.setSelection(FieldSelectionBean.fromProp("roleKey", "roleName", "roleUsers.userName"));
        IGraphQLExecutionContext gqlContext = graphQLEngine.newRpcContext(GraphQLOperationType.query,
                "RoleEntity__findList",
                request);
        @SuppressWarnings("unchecked")
        ApiResponse<?> response = FutureHelper.syncGet(graphQLEngine.executeRpcAsync(gqlContext));
        assertEquals(true, response.isOk());
        List<?> items = BeanTool.castBeanToType(response.getData(), List.class);
        assertEquals(1, items.size());
    }

    void traceGraphQL() {
        GraphQLDocument doc = graphQLEngine.getSchemaLoader().getGraphQLDocument();
        System.out.println("graphql=\n" + doc.toSource());
    }

    @Test
    public void testManyToManyRelation() {
        BaseTestCase.forceStackTrace();
        BaseTestCase.setTestConfig(ApiConfigs.CFG_DEBUG, true);

        testGen(OrmRelationType.m2m);
        traceGraphQL();

        ApiRequest<?> request = new ApiRequest<>();
        request.setSelection(FieldSelectionBean.fromProp("roleKey", "roleName", "roleUsers.sid"));
        IGraphQLExecutionContext gqlContext = graphQLEngine.newRpcContext(GraphQLOperationType.query,
                "RoleEntity__findList",
                request);
        @SuppressWarnings("unchecked")
        ApiResponse<?> response = FutureHelper.syncGet(graphQLEngine.executeRpcAsync(gqlContext));
        assertEquals(true, response.isOk());
        List<?> items = BeanTool.castBeanToType(response.getData(), List.class);
        assertEquals(1, items.size());
    }

    private void deleteTable() {
        NopDynModule example = new NopDynModule();
        example.setModuleName("relation-demo");
        daoProvider.daoFor(NopDynModule.class).deleteByExample(example);
    }

    private void saveRelModule(OrmRelationType ormRelationType) {
        deleteTable();

        NopDynModule module = new NopDynModule();
        module.setModuleName("relation-demo");
        module.setDisplayName("Demo Module");
        module.setStatus(NopDynDaoConstants.MODULE_STATUS_PUBLISHED);

        userEntityMeta(module);
        roleEntityMeta(module);
        middleEntityMeta(module);

        switch (ormRelationType) {
            case o2m:
                addOneToManyRelation(module);
                break;
            case o2o:
            case m2o:
                addManyToOneRelation(module, ormRelationType);
                break;
            case m2m:
                addManyToManyRelation(module);
                break;
        }
        daoProvider.daoFor(NopDynModule.class).flushSession();
    }

    private void middleEntityMeta(NopDynModule module) {
        NopDynEntityMeta entityMeta = new NopDynEntityMeta();
        entityMeta.setEntityName("UserManyRole");
        entityMeta.setDisplayName("User Many Role");
        entityMeta.setModule(module);
        entityMeta.setStatus(1);
        entityMeta.setIsExternal(false);
        entityMeta.setStoreType(NopDynDaoConstants.ENTITY_STORE_TYPE_REAL);

        addProp(entityMeta, "userId", StdSqlType.VARCHAR, 32);
        addProp(entityMeta, "roleId", StdSqlType.VARCHAR, 32);

        daoProvider.daoFor(NopDynEntityMeta.class).saveEntity(entityMeta);
        module.getEntityMetas().add(entityMeta);
    }

    private void userEntityMeta(NopDynModule module) {

        NopDynEntityMeta entityMeta = new NopDynEntityMeta();
        entityMeta.setEntityName("UserEntity");
        entityMeta.setDisplayName("User Entity");
        entityMeta.setModule(module);
        entityMeta.setStatus(1);
        entityMeta.setIsExternal(false);
        entityMeta.setStoreType(NopDynDaoConstants.ENTITY_STORE_TYPE_REAL);

        addProp(entityMeta, "userName", StdSqlType.VARCHAR, 100);
        addProp(entityMeta, "userAge", StdSqlType.INTEGER, 0);
        addProp(entityMeta, "roleId", StdSqlType.VARCHAR, 100);

        daoProvider.daoFor(NopDynEntityMeta.class).saveEntity(entityMeta);
        module.getEntityMetas().add(entityMeta);
    }


    private void roleEntityMeta(NopDynModule module) {
        NopDynEntityMeta entityMeta = new NopDynEntityMeta();
        entityMeta.setEntityName("RoleEntity");
        entityMeta.setDisplayName("Role Entity");
        entityMeta.setModule(module);
        entityMeta.setStatus(1);
        entityMeta.setIsExternal(false);
        entityMeta.setStoreType(NopDynDaoConstants.ENTITY_STORE_TYPE_REAL);

        addProp(entityMeta, "roleName", StdSqlType.VARCHAR, 100);
        addProp(entityMeta, "roleKey", StdSqlType.VARCHAR, 100);

        daoProvider.daoFor(NopDynEntityMeta.class).saveEntity(entityMeta);
        module.getEntityMetas().add(entityMeta);
    }

    private void addRelation(OrmRelationType relationType, String relationName, String displayName,
                             String leftPropName, String rightPropName, NopDynEntityMeta leftEntity, NopDynEntityMeta rightEntity, String middleTableName) {

        NopDynEntityRelationMeta relationMeta = new NopDynEntityRelationMeta();
        relationMeta.setEntityMetaId(leftEntity.getEntityMetaId());
        relationMeta.setRefEntityMetaId(rightEntity.getEntityMetaId());
        relationMeta.setRelationName(relationName);
        relationMeta.setRelationDisplayName(displayName);
        relationMeta.setRelationType(relationType.name());
        relationMeta.setLeftPropName(leftPropName);
        relationMeta.setRightPropName(rightPropName);
        relationMeta.setStatus(1);
        relationMeta.setTagsText("pub");
        relationMeta.setMiddleTableName(middleTableName);
        leftEntity.getRelationMetasForEntity().add(relationMeta);
    }

    private void addManyToOneRelation(NopDynModule module, OrmRelationType relationType) {
        Iterator<NopDynEntityMeta> iterator = module.getEntityMetas().iterator();
        NopDynEntityMeta userEntity = iterator.next();
        NopDynEntityMeta roleEntity = iterator.next();

        addRelation(relationType, "userRole", "测试" + relationType.name() + "关联",
                "roleId", "sid", userEntity, roleEntity, null);
    }

    private void addOneToManyRelation(NopDynModule module) {
        Iterator<NopDynEntityMeta> iterator = module.getEntityMetas().iterator();
        NopDynEntityMeta userEntity = iterator.next();
        NopDynEntityMeta roleEntity = iterator.next();

        addRelation(OrmRelationType.o2m, "roleUsers", "测试一对多关联",
                "sid", "roleId", roleEntity, userEntity, null);
    }

    private void addManyToManyRelation(NopDynModule module) {
        Iterator<NopDynEntityMeta> iterator = module.getEntityMetas().iterator();
        NopDynEntityMeta userEntity = iterator.next();
        NopDynEntityMeta roleEntity = iterator.next();
        NopDynEntityMeta middleEntity = iterator.next();

        module.getEntityMetas().remove(middleEntity);

        addRelation(OrmRelationType.m2m, "userRoles", "测试用户对多对关联",
                "sid", "userId", userEntity, roleEntity, null);

        addRelation(OrmRelationType.m2m, "roleUsers", "测试角色对多对关联",
                "sid", "roleId", roleEntity, userEntity, null);
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
}
