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

    @Test
    public void testImportProducesUniqueKeys() {
        execute("mutation { NopMetaModule__importOrmModel(path: \"/nop/metadata/orm/app.orm.xml\")" +
                " { metaModuleId } }");

        GraphQLResponseBean ukResp = execute(
                "query { NopMetaEntityUniqueKey__findPage { total items { ukName columns } } }");
        assertFalse(ukResp.hasError(), "unique key query should not error: " + ukResp);

        String data = String.valueOf(ukResp.getData());
        assertTrue(data.matches(".*\"total\"\\s*:\\s*[1-9].*") || data.matches(".*total.*[1-9].*"),
                "Imported unique keys count should be > 0: " + data);
        assertTrue(data.contains("columns"),
                "Imported unique key records should contain a columns field: " + data);
    }

    @Test
    public void testImportProducesIndexes() {
        execute("mutation { NopMetaModule__importOrmModel(path: \"/nop/metadata/orm/app.orm.xml\")" +
                " { metaModuleId } }");

        GraphQLResponseBean idxResp = execute(
                "query { NopMetaEntityIndex__findPage { total items { indexName indexColumns } } }");
        assertFalse(idxResp.hasError(), "index query should not error: " + idxResp);

        String data = String.valueOf(idxResp.getData());
        assertTrue(data.matches(".*\"total\"\\s*:\\s*[1-9].*") || data.matches(".*total.*[1-9].*"),
                "Imported indexes count should be > 0: " + data);
        assertTrue(data.contains("fieldName"),
                "Imported index columns JSON should contain fieldName entries: " + data);
    }

    @Test
    public void testImportOrmModelsBatch() {
        // 批量导入：[有效路径, 无效路径]。有效路径成功导入；
        // 无效路径在资源查找阶段抛 ERR_RESOURCE_NOT_FOUND（早于任何持久化），
        // 验证批量逻辑返回 2 个结果条目，且单次失败不中断整批（不静默跳过，
        // 失败信息显式记录在结果中）。
        // importOrmModels 返回 List<Map>，GraphQL 视为标量 JSON，不做字段选择。
        GraphQLResponseBean response = execute(
                "mutation { NopMetaModule__importOrmModels(paths:" +
                        " [\"/nop/metadata/orm/app.orm.xml\"," +
                        " \"/nop/metadata/orm/__not_exist__.orm.xml\"]) }");
        assertFalse(response.hasError(),
                "batch importOrmModels should not abort the whole batch: " + response);

        String data = String.valueOf(response.getData());
        long entryCount = countOccurrences(data, "success=");
        assertTrue(entryCount >= 2,
                "Batch import should return at least 2 result entries: " + data);
        assertTrue(data.contains("success=true"),
                "The valid path entry should be marked success=true: " + data);
        assertTrue(data.contains("success=false"),
                "The invalid path entry should be marked success=false: " + data);
        assertTrue(data.contains("error="),
                "The failed entry should carry an error message: " + data);
    }

    private static int countOccurrences(String text, String token) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(token, idx)) >= 0) {
            count++;
            idx += token.length();
        }
        return count;
    }

    private GraphQLResponseBean execute(String query) {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        IGraphQLExecutionContext context = graphQLEngine.newGraphQLContext(request);
        return graphQLEngine.executeGraphQL(context);
    }
}
