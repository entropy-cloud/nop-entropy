package io.nop.metadata.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.graphql.core.engine.IGraphQLEngine;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestBiSemanticFilterSave extends JunitBaseTestCase {

    public TestBiSemanticFilterSave() {
        setTestConfig("nop.orm.init-database-schema", true);
    }

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    private BiSemanticTestHelper helper;

    @BeforeEach
    public void setUp() {
        helper = new BiSemanticTestHelper(graphQLEngine, daoProvider);
    }

    @Test
    public void testFilterSaveValidTreeBean() {
        String moduleId = helper.ensureModule("mod-filter-ok");
        String tableId = helper.saveEntityTable(moduleId, "T_FILTER_OK", null);
        String def = BiSemanticTestHelper.escapeGraphQL(JsonTool.stringify(
                FilterBeans.eq("status", "active")));

        GraphQLResponseBean resp = helper.runGraphQL(
                "mutation { NopMetaTableFilter__save(data: {"
                        + "metaTableId: \"" + tableId + "\", filterName: \"f_active\", "
                        + "definition: \"" + def + "\"}) { filterId } }");
        assertFalse(resp.hasError(), "valid TreeBean filter must succeed: " + resp);
    }

    @Test
    public void testFilterSaveValidComposite() {
        String moduleId = helper.ensureModule("mod-filter-comp");
        String tableId = helper.saveEntityTable(moduleId, "T_FILTER_COMP", null);
        String def = BiSemanticTestHelper.escapeGraphQL(JsonTool.stringify(
                FilterBeans.and(FilterBeans.eq("status", "active"), FilterBeans.gt("amount", 100))));

        GraphQLResponseBean resp = helper.runGraphQL(
                "mutation { NopMetaTableFilter__save(data: {"
                        + "metaTableId: \"" + tableId + "\", filterName: \"f_comp\", "
                        + "definition: \"" + def + "\"}) { filterId } }");
        assertFalse(resp.hasError(), "valid composite filter must succeed: " + resp);
    }

    @Test
    public void testFilterSaveInvalidJsonFails() {
        String moduleId = helper.ensureModule("mod-filter-bad");
        String tableId = helper.saveEntityTable(moduleId, "T_FILTER_BAD", null);

        GraphQLResponseBean resp = helper.runGraphQL(
                "mutation { NopMetaTableFilter__save(data: {"
                        + "metaTableId: \"" + tableId + "\", filterName: \"f_bad\", "
                        + "definition: \"this is not json {{{\"}) { filterId } }");
        assertTrue(resp.hasError(), "invalid JSON filter must be rejected: " + resp);
    }

    @Test
    public void testFilterSaveEmptyDefinitionFails() {
        String moduleId = helper.ensureModule("mod-filter-empty");
        String tableId = helper.saveEntityTable(moduleId, "T_FILTER_EMPTY", null);

        GraphQLResponseBean resp = helper.runGraphQL(
                "mutation { NopMetaTableFilter__save(data: {"
                        + "metaTableId: \"" + tableId + "\", filterName: \"f_empty\", "
                        + "definition: \"   \"}) { filterId } }");
        assertTrue(resp.hasError(), "empty definition must be rejected: " + resp);
    }

    @Test
    public void testFilterIsDefaultUniquenessFails() {
        String moduleId = helper.ensureModule("mod-filter-default");
        String tableId = helper.saveEntityTable(moduleId, "T_FILTER_DEF", null);
        String def = BiSemanticTestHelper.escapeGraphQL(JsonTool.stringify(FilterBeans.eq("a", "b")));

        GraphQLResponseBean first = helper.runGraphQL(
                "mutation { NopMetaTableFilter__save(data: {"
                        + "metaTableId: \"" + tableId + "\", filterName: \"f_default1\", "
                        + "definition: \"" + def + "\", isDefault: true}) { filterId } }");
        assertFalse(first.hasError(), "first default filter must succeed: " + first);

        GraphQLResponseBean second = helper.runGraphQL(
                "mutation { NopMetaTableFilter__save(data: {"
                        + "metaTableId: \"" + tableId + "\", filterName: \"f_default2\", "
                        + "definition: \"" + def + "\", isDefault: true}) { filterId } }");
        assertTrue(second.hasError(),
                "second default filter (isDefault=true) for same table must be rejected: " + second);
    }
}
