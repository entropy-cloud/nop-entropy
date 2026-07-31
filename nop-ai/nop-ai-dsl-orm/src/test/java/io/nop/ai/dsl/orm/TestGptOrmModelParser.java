/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.dsl.orm;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.core.unittest.BaseTestCase;
import io.nop.ai.core.response.XmlResponseParser;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.OrmModel;
import io.nop.orm.model.OrmModelConstants;
import io.nop.xlang.xdsl.DslModelHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestGptOrmModelParser extends BaseTestCase {
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testParse() {
        String response = classpathResource("orm-response1.txt").readText();
        XNode node = XmlResponseParser.instance().parseResponse(response);
        OrmModel ormModel = new GptOrmModelParser().parseOrmModel(node);
        ormModel.init();

        XNode ormNode = DslModelHelper.dslModelToXNode(OrmModelConstants.XDSL_SCHEMA_ORM, ormModel);
        ormNode.dump();

        assertNotNull(ormModel);
        assertEquals(3, ormModel.getEntities().size());

        IEntityModel product = ormModel.getEntityModel("Product");
        assertNotNull(product);
        assertEquals("product", product.getTableName());
        assertEquals("商品", product.getDisplayName());
        assertEquals("app.demo.Product", product.getClassName());

        assertEquals(4, product.getColumns().size());
        IColumnModel idCol = product.getColumnByCode("ID", true);
        assertNotNull(idCol);
        assertTrue(idCol.isPrimary());
        assertEquals(1, idCol.getPropId());

        IColumnModel priceCol = product.getColumnByCode("PRICE", true);
        assertNotNull(priceCol);
        assertEquals(2, priceCol.getScale());
        assertEquals(3, priceCol.getPropId());

        IEntityModel customer = ormModel.getEntityModel("Customer");
        assertNotNull(customer);
        assertEquals("customer", customer.getTableName());
        assertEquals("app.demo.Customer", customer.getClassName());

        IEntityModel order = ormModel.getEntityModel("Order");
        assertNotNull(order);
        assertEquals("order", order.getTableName());
        assertEquals(4, order.getColumns().size());

        IColumnModel customerId = order.getColumnByCode("CUSTOMER_ID", true);
        assertNotNull(customerId);
        assertEquals("customerId", customerId.getName());
        assertEquals(2, customerId.getPropId());
    }
}
