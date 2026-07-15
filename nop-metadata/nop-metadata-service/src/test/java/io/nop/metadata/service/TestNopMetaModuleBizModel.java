/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.metadata.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.IGraphQLEngine;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopMetaModuleBizModel extends JunitBaseTestCase {

    public TestNopMetaModuleBizModel() {
        setTestConfig("nop.orm.init-database-schema", true);
    }

    @Inject
    IGraphQLEngine graphQLEngine;

    @Test
    public void testImportOrmModel() {
        GraphQLResponseBean response = execute(
                "mutation { NopMetaModule__importOrmModel(path: \"/nop/metadata/orm/app.orm.xml\")" +
                        " { metaModuleId moduleName moduleVersion } }");
        assertFalse(response.hasError(), "importOrmModel should not error: " + response);

        GraphQLResponseBean entityResp = execute(
                "query { NopMetaEntity__findPage { total items { entityName tableName } } }");
        assertFalse(entityResp.hasError(), "findPage should not error: " + entityResp);

        String data = String.valueOf(entityResp.getData());
        assertTrue(data.contains("NopMetaModule") || data.contains("nop_meta_module"),
                "Imported entities should include NopMetaModule: " + data);
    }

    @Test
    public void testImportProducesFields() {
        execute("mutation { NopMetaModule__importOrmModel(path: \"/nop/metadata/orm/app.orm.xml\")" +
                " { metaModuleId } }");

        GraphQLResponseBean fieldResp = execute(
                "query { NopMetaEntityField__findPage { total } }");
        assertFalse(fieldResp.hasError(), "field query should not error: " + fieldResp);

        String data = String.valueOf(fieldResp.getData());
        assertTrue(data.matches(".*\"total\"\\s*:\\s*[1-9].*") || data.matches(".*total.*[1-9].*"),
                "Imported fields count should be > 0: " + data);
    }

    private GraphQLResponseBean execute(String query) {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        IGraphQLExecutionContext context = graphQLEngine.newGraphQLContext(request);
        return graphQLEngine.executeGraphQL(context);
    }
}
