/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dyn.service.codegen;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.util.FutureHelper;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.commons.type.StdSqlType;
import io.nop.core.lang.sql.SQL;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dyn.dao.NopDynDaoConstants;
import io.nop.dyn.dao.entity.NopDynEntityMeta;
import io.nop.dyn.dao.entity.NopDynEntityRelationMeta;
import io.nop.dyn.dao.entity.NopDynModule;
import io.nop.dyn.dao.entity.NopDynPropMeta;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.model.OrmRelationType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;


@Disabled
@NopTestConfig(localDb = true, initDatabaseSchema = true)
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

    @Override
    public void init(TestInfo testInfo) {
        super.init(testInfo);
        initDb();
    }

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

    @Test
    public void testManyToManyRelation() {

        testGen(OrmRelationType.m2m);

        ApiRequest<?> request = new ApiRequest<>();
        request.setSelection(FieldSelectionBean.fromProp("roleKey", "roleName", "roleUsers.userId"));
        IGraphQLExecutionContext gqlContext = graphQLEngine.newRpcContext(GraphQLOperationType.query,
                "RoleEntity__findList",
                request);
        @SuppressWarnings("unchecked")
        ApiResponse<?> response = FutureHelper.syncGet(graphQLEngine.executeRpcAsync(gqlContext));
        assertEquals(true, response.isOk());
        List<?> items = BeanTool.castBeanToType(response.getData(), List.class);
        assertEquals(1, items.size());
    }

    private void initDb() {

        if (jdbcTemplate.existsTable("", "USER_ENTITY")) {
            return;
        }

        String userSql = "CREATE TABLE USER_ENTITY(\n"
                + "                  SID VARCHAR(32)   COMMENT '主键ID' ,\n"
                + "                  USER_NAME VARCHAR(100)   COMMENT '用户名' ,\n"
                + "                  USER_AGE INTEGER   default '1'  COMMENT '用户年龄' ,\n"
                + "                  ROLE_ID VARCHAR(200)   COMMENT '角色 ID' ,\n"
                + "                  STATUS INTEGER   default '1'  COMMENT '状态' ,\n"
                + "                  VERSION INTEGER   COMMENT '数据版本' ,\n"
                + "                  CREATED_BY VARCHAR(50)   COMMENT '创建人' ,\n"
                + "                  CREATE_TIME TIMESTAMP   COMMENT '创建时间' ,\n"
                + "                  UPDATED_BY VARCHAR(50)   COMMENT '修改人' ,\n"
                + "                  UPDATE_TIME TIMESTAMP   COMMENT '修改时间' ,\n"
                + "                  constraint PK_USER_ENTITY_ID primary key (sid)\n"
                + "                )";

        String insertSql = "INSERT INTO USER_ENTITY (sid, user_name, user_age, role_id, STATUS, VERSION, CREATED_BY, CREATE_TIME, UPDATED_BY, UPDATE_TIME) VALUES "
                + "('1', '小明', 1, '123', 1, 1, '小明', '2021-09-01 00:00:00', '小明', '2021-09-01 00:00:00'),"
                + "('2', '小李', 200, '123', 1, 1, '小李', '2021-09-01 00:00:00', '小李', '2021-09-01 00:00:00')";


        String roleSql = "CREATE TABLE ROLE_ENTITY(\n"
                + "                  SID VARCHAR(32)   COMMENT '主键ID' ,\n"
                + "                  ROLE_NAME VARCHAR(100)   COMMENT '角色名称' ,\n"
                + "                  ROLE_KEY VARCHAR(100)   COMMENT '角色 key' ,\n"
                + "                  STATUS INTEGER   default '1'  COMMENT '状态' ,\n"
                + "                  VERSION INTEGER   COMMENT '数据版本' ,\n"
                + "                  CREATED_BY VARCHAR(50)   COMMENT '创建人' ,\n"
                + "                  CREATE_TIME TIMESTAMP   COMMENT '创建时间' ,\n"
                + "                  UPDATED_BY VARCHAR(50)   COMMENT '修改人' ,\n"
                + "                  UPDATE_TIME TIMESTAMP   COMMENT '修改时间' ,\n"
                + "                  constraint PK_ROLE_ENTITY_ID primary key (sid)\n"
                + "                )";

        String insertRoleSql = "INSERT INTO ROLE_ENTITY (sid, role_name, role_key, STATUS, VERSION, CREATED_BY, CREATE_TIME, UPDATED_BY, UPDATE_TIME) VALUES "
                + " ('123', '开发角色2', '1', 1, 1, 'development', '2021-09-01 00:00:00', 'development', '2021-09-01 00:00:00')";
        // + " ('12356', '测试角色', '2', 1, 1, 'test', '2021-09-01 00:00:00', 'test', '2021-09-01 00:00:00') ";

        String manySql = "CREATE TABLE USER_MANY_ROLE(\n"
                + "                  SID VARCHAR(32)   COMMENT '主键ID' ,\n"
                + "                  USER_ID VARCHAR(32)   COMMENT '用户 ID' ,\n"
                + "                  ROLE_ID VARCHAR(32)   COMMENT '角色 ID' ,\n"
                + "                  STATUS INTEGER   default '1'  COMMENT '状态' ,\n"
                + "                  VERSION INTEGER   COMMENT '数据版本' ,\n"
                + "                  CREATED_BY VARCHAR(50)   COMMENT '创建人' ,\n"
                + "                  CREATE_TIME TIMESTAMP   COMMENT '创建时间' ,\n"
                + "                  UPDATED_BY VARCHAR(50)   COMMENT '修改人' ,\n"
                + "                  UPDATE_TIME TIMESTAMP   COMMENT '修改时间' ,\n"
                + "                  constraint PK_USER_MANY_ROLE_ID primary key (user_id, role_id)\n"
                + "                )";
        String insertManySql = "INSERT INTO USER_MANY_ROLE (sid, user_id, role_id, STATUS, VERSION, CREATED_BY, CREATE_TIME, UPDATED_BY, UPDATE_TIME) VALUES "
                + "('1233123', '1', '123', 1, 1, '小明', '2021-09-01 00:00:00', '小明', '2021-09-01 00:00:00'),"
                + "('1412414', '255', '123', 1, 1, '小明', '2021-09-01 00:00:00', '小明', '2021-09-01 00:00:00')";

        System.out.println(userSql + "\n" + insertSql + "\n" + roleSql + "\n" + insertRoleSql + "\n" + manySql + "\n" + insertManySql);
        jdbcTemplate.executeMultiSql(new SQL(userSql));
        jdbcTemplate.executeUpdate(new SQL(insertSql));
        jdbcTemplate.executeMultiSql(new SQL(roleSql));
        jdbcTemplate.executeUpdate(new SQL(insertRoleSql));
        jdbcTemplate.executeMultiSql(new SQL(manySql));
        jdbcTemplate.executeUpdate(new SQL(insertManySql));
    }

    private void saveRelModule(OrmRelationType ormRelationType) {
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

        addRelation(OrmRelationType.m2m, "userRoles", "测试用户对多对关联",
                "sid", "userId", userEntity, middleEntity, "USER_MANY_ROLE");

        addRelation(OrmRelationType.m2m, "roleUsers", "测试角色对多对关联",
                "sid", "roleId", roleEntity, middleEntity, "USER_MANY_ROLE");
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
