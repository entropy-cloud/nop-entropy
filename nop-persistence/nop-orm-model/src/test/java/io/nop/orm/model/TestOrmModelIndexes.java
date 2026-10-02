/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.model;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.type.StdSqlType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static io.nop.orm.model.OrmModelErrors.ERR_ORM_UNKNOWN_ENTITY_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI4 覆盖补强：OrmModel 初始化后的各类索引与查询语义（纯 JUnit 手工建模）。
 */
public class TestOrmModelIndexes {

    static OrmColumnModel column(String name, String code, int propId, boolean primary) {
        OrmColumnModel col = new OrmColumnModel();
        col.setName(name);
        col.setCode(code);
        col.setPropId(propId);
        col.setStdSqlType(StdSqlType.VARCHAR);
        col.setPrimary(primary);
        return col;
    }

    static OrmEntityModel entity(String name, String tableName, OrmColumnModel... cols) {
        OrmEntityModel entityModel = new OrmEntityModel();
        entityModel.setName(name);
        entityModel.setTableName(tableName);
        entityModel.setColumns(new ArrayList<>(Arrays.asList(cols)));
        return entityModel;
    }

    static OrmToOneReferenceModel toOneRef(String name, String refEntityName, String leftProp, String rightProp) {
        OrmToOneReferenceModel ref = new OrmToOneReferenceModel();
        ref.setName(name);
        ref.setRefEntityName(refEntityName);
        OrmJoinOnModel join = new OrmJoinOnModel();
        join.setLeftProp(leftProp);
        join.setRightProp(rightProp);
        ref.setJoin(new ArrayList<>(Collections.singletonList(join)));
        return ref;
    }

    static OrmToManyReferenceModel toManyRef(String name, String refEntityName, String leftProp, String rightProp) {
        OrmToManyReferenceModel ref = new OrmToManyReferenceModel();
        ref.setName(name);
        ref.setRefEntityName(refEntityName);
        OrmJoinOnModel join = new OrmJoinOnModel();
        join.setLeftProp(leftProp);
        join.setRightProp(rightProp);
        ref.setJoin(new ArrayList<>(Collections.singletonList(join)));
        return ref;
    }

    /**
     * Order(id) -> to-one ref Customer(id)；Customer(id) -> to-many ref orders(id)
     */
    static OrmModel orderModel() {
        OrmEntityModel order = entity("OrmTestOrder", "orm_test_order_tbl",
                column("id", "ID", 1, true),
                column("customerId", "CUSTOMER_ID", 2, false));
        OrmEntityModel customer = entity("OrmTestCustomer", "orm_test_customer_tbl",
                column("id", "ID", 1, true));

        order.setRelations(new ArrayList<>(Collections.singletonList(toOneRef("customer", "OrmTestCustomer",
                "customerId", "id"))));
        OrmToManyReferenceModel orders = toManyRef("orders", "OrmTestOrder", "id", "customerId");
        // 集合注册名与实体初始化器派生的 Entity@prop 命名契约一致
        orders.setCollectionName("OrmTestCustomer@orders");
        customer.setRelations(new ArrayList<>(Collections.singletonList(orders)));

        order.setRegisterShortName(true);
        customer.setRegisterShortName(true);

        OrmModel model = new OrmModel();
        model.setEntities(new ArrayList<>(Arrays.asList(order, customer)));
        model.init();
        return model;
    }

    @Test
    public void testGetEntityModelByTableName() {
        OrmModel model = orderModel();
        assertEquals("OrmTestOrder", model.getEntityModelByTableName("orm_test_order_tbl").getName());
        assertEquals("OrmTestCustomer", model.getEntityModelByTableName("orm_test_customer_tbl").getName());
        assertNull(model.getEntityModelByTableName("no_such_table"));
    }

    @Test
    public void testGetEntityModelBySnakeCaseName() {
        // 注册了短名的实体可经驼峰转下划线名（含全小写与全大写）查询
        OrmModel model = orderModel();
        assertEquals("OrmTestOrder", model.getEntityModelBySnakeCaseName("orm_test_order").getName());
        assertEquals("OrmTestOrder", model.getEntityModelBySnakeCaseName("ORM_TEST_ORDER").getName());
        assertNull(model.getEntityModelBySnakeCaseName("no_such_entity"));
    }

    @Test
    public void testGetEntityModelByName() {
        OrmModel model = orderModel();
        assertNotNull(model.getEntityModel("OrmTestOrder"));
        assertNull(model.getEntityModel("NoEntity"));
        assertTrue(model.getEntityNames().contains("OrmTestOrder"));
        assertTrue(model.getEntityNames().contains("OrmTestCustomer"));
    }

    @Test
    public void testTopoOrderPutsReferencedEntityFirst() {
        // 外键方(OrmTestOrder)依赖被引用方(OrmTestCustomer)：建表序应先被引用实体
        OrmModel model = orderModel();
        Collection<? extends IEntityModel> topo = model.getEntityModelsInTopoOrder();
        assertEquals(2, topo.size());
        assertTrue(topo.iterator().next().getName().equals("OrmTestCustomer"),
                "referenced entity should come first, got " + topo);

        List<IEntityModel> pair = model.getEntityModelInTopoOrder(Arrays.asList("OrmTestOrder", "OrmTestCustomer"));
        assertEquals("OrmTestCustomer", pair.get(0).getName());
        assertEquals("OrmTestOrder", pair.get(1).getName());
    }

    @Test
    public void testUnknownEntityNameInTopoQueryThrows() {
        OrmModel model = orderModel();
        NopException e = assertThrows(NopException.class,
                () -> model.getEntityModelInTopoOrder(Collections.singletonList("NoEntity")));
        assertEquals(ERR_ORM_UNKNOWN_ENTITY_NAME.getErrorCode(), e.getErrorCode());
        assertEquals("NoEntity", e.getParam("entityName"));
    }

    @Test
    public void testToManyCollectionNameDerivedByInitializer() {
        // 未显式设置collectionName时，初始化器按 Entity@prop 命名契约派生
        OrmEntityModel customer = entity("OrmTestCustomer", "orm_test_customer_tbl",
                column("id", "ID", 1, true));
        OrmToManyReferenceModel orders = toManyRef("orders", "OrmTestOrder", "id", "customerId");
        customer.setRelations(new ArrayList<>(Collections.singletonList(orders)));

        OrmEntityModel order = entity("OrmTestOrder", "orm_test_order_tbl",
                column("id", "ID", 1, true),
                column("customerId", "CUSTOMER_ID", 2, false));

        OrmModel model = new OrmModel();
        model.setEntities(new ArrayList<>(Arrays.asList(customer, order)));
        model.init();

        OrmToManyReferenceModel resolved =
                (OrmToManyReferenceModel) model.getEntityModel("OrmTestCustomer").getRelation("orders", true);
        assertEquals("OrmTestCustomer@orders", resolved.getCollectionName());
    }

    @Test
    public void testCollectionModelRegisteredByEntityAndPropName() {
        // to-many 集合以 实体名@属性名 注册，可经 getCollectionModel 查询
        OrmModel model = orderModel();
        IEntityRelationModel collection = model.getCollectionModel("OrmTestCustomer@orders");
        assertNotNull(collection, "collection OrmTestCustomer@orders should be registered");
        assertEquals("OrmTestOrder", collection.getRefEntityName());

        assertNull(model.getCollectionModel("OrmTestCustomer@noProp"));
    }

    @Test
    public void testAnyEntityUseTenantFlagAggregated() {
        OrmColumnModel tenantCol = column("tenantId", "TENANT_ID", 2, false);
        OrmEntityModel withTenant = entity("TenEntity", "ten_tbl",
                column("id", "ID", 1, true), tenantCol);
        withTenant.setUseTenant(true);

        OrmEntityModel plain = entity("PlainEntity", "plain_tbl", column("id", "ID", 1, true));

        OrmModel mixed = new OrmModel();
        mixed.setEntities(new ArrayList<>(Arrays.asList(withTenant, plain)));
        mixed.init();
        assertTrue(mixed.isAnyEntityUseTenant());

        OrmModel clean = new OrmModel();
        clean.setEntities(new ArrayList<>(Collections.singletonList(plain)));
        clean.init();
        assertEquals(false, clean.isAnyEntityUseTenant());
    }

    @Test
    public void testMergedModelSkipsDomainSync() {
        // merged 模型（多 app.orm.xml 合并产物）不检查domain有效性，init仍应成功建立索引
        OrmColumnModel col = column("id", "ID", 1, true);
        col.setDomain("unknown-domain-x");
        OrmEntityModel entity = entity("MergedEntity", "merged_tbl", col);

        OrmModel model = new OrmModel();
        model.setEntities(new ArrayList<>(Collections.singletonList(entity)));
        model.setMerged(true);
        model.init();

        assertNotNull(model.getEntityModel("MergedEntity"));
        assertTrue(model.isMerged());
    }
}
