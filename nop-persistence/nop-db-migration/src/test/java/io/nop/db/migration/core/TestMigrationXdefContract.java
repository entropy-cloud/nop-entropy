/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical-entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.db.migration.core;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.xlang.xdef.IXDefNode;
import io.nop.xlang.xdef.IXDefinition;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Schema-contract regression tests for /nop/schema/db-migration/migration.xdef.
 *
 * <p>The typed parse chain (_DbMigrationModel.setChangeset builds a KeyedList
 * keyed by DbChangeModel::getId) only works when every change element declared
 * in the changeset resolves to a model class extending DbChangeModel and
 * declares an id attribute. A previous defect made 9 of the 17 change types
 * (sql/alterColumn/createIndex/...) parse into classes that only extend
 * AbstractComponentModel, so loading such a migration file threw
 * ClassCastException. These tests pin the xdef-level contract so it cannot
 * silently regress; the generated _gen model classes must be regenerated from
 * this xdef to inherit DbChangeModel accordingly.
 */
class TestMigrationXdefContract {

    private static final String CHANGE_BASE = "io.nop.db.migration.model.DbChangeModel";
    private static final String PRECONDITION_BASE = "io.nop.db.migration.model.DbPreconditionModel";

    private static final String[] CHANGE_TAGS = {
        "createTable", "dropTable", "renameTable", "addColumn", "dropColumn",
        "alterColumn", "createIndex", "dropIndex", "createView", "dropView",
        "sql", "insert", "update", "delete", "customChange", "executeMark", "dbTypeFilter"
    };

    private static final String[] PRECONDITION_TAGS = {
        "tableExists", "columnExists", "indexExists", "foreignKeyExists", "customCondition"
    };

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private IXDefNode changesetNode() {
        IXDefinition def = (IXDefinition) ResourceComponentManager.instance()
            .loadComponentModel("/nop/schema/db-migration/migration.xdef");
        IXDefNode changeset = def.getRootNode().getChild("changeset");
        assertNotNull(changeset, "migration.xdef must declare a <changeset> element");
        return changeset;
    }

    @Test
    void testChangesetDeclaresKeyAttr() {
        // A stray 'xdef:key-attr="id">' after the changeset start tag once
        // broke the key-attr declaration: the parsed body list took the
        // ArrayList branch and setChangeset then applied DbChangeModel::getId
        // to every element, which is where the ClassCastException surfaced
        assertEquals("id", changesetNode().getXdefKeyAttr(),
            "changeset must declare xdef:key-attr=\"id\" as a real attribute");
    }

    @Test
    void testEveryChangeElementExtendsDbChangeModelAndDeclaresId() {
        IXDefNode changeset = changesetNode();
        for (String tag : CHANGE_TAGS) {
            IXDefNode node = changeset.getChild(tag);
            assertNotNull(node, "changeset must declare <" + tag + ">");
            assertNotNull(node.getAttribute("id"),
                "<" + tag + "> must declare an id attribute (changeset is keyed by id)");
            assertNotNull(node.getXdefBeanExtendsType(),
                "<" + tag + "> must declare xdef:bean-extends-type, otherwise the generated"
                    + " model class does not extend DbChangeModel and parsing a <" + tag
                    + "> change fails with ClassCastException");
            assertEquals(CHANGE_BASE, node.getXdefBeanExtendsType().getTypeName(),
                "<" + tag + "> must extend DbChangeModel");
        }
    }

    @Test
    void testEveryPreconditionElementExtendsDbPreconditionModelAndDeclaresId() {
        IXDefinition def = (IXDefinition) ResourceComponentManager.instance()
            .loadComponentModel("/nop/schema/db-migration/migration.xdef");
        IXDefNode preconditions = def.getRootNode().getChild("preconditions");
        assertNotNull(preconditions, "migration.xdef must declare a <preconditions> element");
        for (String tag : PRECONDITION_TAGS) {
            IXDefNode node = preconditions.getChild(tag);
            assertNotNull(node, "preconditions must declare <" + tag + ">");
            assertNotNull(node.getAttribute("id"),
                "<" + tag + "> must declare an id attribute (preconditions is keyed by id)");
            assertNotNull(node.getXdefBeanExtendsType(),
                "<" + tag + "> must declare xdef:bean-extends-type, otherwise the generated"
                    + " model class does not extend DbPreconditionModel");
            assertEquals(PRECONDITION_BASE, node.getXdefBeanExtendsType().getTypeName(),
                "<" + tag + "> must extend DbPreconditionModel");
        }
    }

    @Test
    void testDataChangeColumnsResolveToTypedModels() {
        IXDefNode changeset = changesetNode();
        IXDefNode insertColumn = changeset.getChild("insert").getChild("columns").getChild("column");
        assertNotNull(insertColumn, "<insert> must declare <columns><column>");
        assertEquals("io.nop.db.migration.model.InsertColumnModel", insertColumn.getXdefBeanClass(),
            "<insert><column> must declare xdef:name=InsertColumnModel; without it columns parse"
                + " to DynamicObject and InsertDataExecutor fails with ClassCastException");

        IXDefNode updateColumn = changeset.getChild("update").getChild("columns").getChild("column");
        assertNotNull(updateColumn, "<update> must declare <columns><column>");
        assertEquals("io.nop.db.migration.model.UpdateColumnModel", updateColumn.getXdefBeanClass(),
            "<update><column> must declare xdef:name=UpdateColumnModel");
    }

    @Test
    void testRollbackAndDbTypeFilterChangesMatchChangesetContract() {
        // rollback/<changes> and dbTypeFilter/<changes> declare the same
        // List<DbChangeModel> body as the changeset, so every element they
        // reference must satisfy the same DbChangeModel contract. The xdef:ref
        // elements resolve to the same named definitions asserted above.
        IXDefinition def = (IXDefinition) ResourceComponentManager.instance()
            .loadComponentModel("/nop/schema/db-migration/migration.xdef");

        IXDefNode rollbackChanges = def.getRootNode().getChild("rollback").getChild("changes");
        assertNotNull(rollbackChanges);
        assertTrue(rollbackChanges.getXdefBeanBodyType().getTypeName().contains("DbChangeModel"));

        IXDefNode filterChanges = changesetNode().getChild("dbTypeFilter").getChild("changes");
        assertNotNull(filterChanges);
        assertTrue(filterChanges.getXdefBeanBodyType().getTypeName().contains("DbChangeModel"));
    }
}
