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
import io.nop.core.lang.json.JsonTool;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.IGraphQLEngine;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    @Test
    public void testReleaseModuleStatusTransition() {
        // 导入模块（首次）：version=1, status=DRAFTING
        GraphQLResponseBean impResp = execute(
                "mutation { NopMetaModule__importOrmModel(path: \"/nop/metadata/orm/app.orm.xml\")" +
                        " { metaModuleId moduleVersion status } }");
        assertFalse(impResp.hasError(), "import should not error: " + impResp);

        Map<String, Object> impData = mutationResult(impResp);
        String metaModuleId = (String) impData.get("metaModuleId");
        assertEquals(1L, impData.get("moduleVersion"), "first import version should be 1: " + impData);
        assertEquals("DRAFTING", impData.get("status"), "status should be DRAFTING after import: " + impData);

        // releaseModule：status → RELEASED，version 不变
        GraphQLResponseBean relResp = execute(
                "mutation { NopMetaModule__releaseModule(metaModuleId: \"" + metaModuleId + "\")" +
                        " { metaModuleId moduleVersion status } }");
        assertFalse(relResp.hasError(), "release should not error: " + relResp);

        Map<String, Object> relData = mutationResult(relResp);
        assertEquals("RELEASED", relData.get("status"), "status should be RELEASED after release: " + relData);
        assertEquals(1L, relData.get("moduleVersion"), "version should remain 1 after release: " + relData);

        // 接线验证：查询确认 status 确实在运行时被修改
        GraphQLResponseBean getResp = execute(
                "query { NopMetaModule__get(id: \"" + metaModuleId + "\") { moduleVersion status } }");
        assertFalse(getResp.hasError(), "get should not error: " + getResp);
        String getData = String.valueOf(getResp.getData());
        assertTrue(getData.contains("status=RELEASED"), "persisted status should be RELEASED: " + getData);
    }

    @Test
    public void testReleaseModuleImmutableAlreadyReleased() {
        // 导入 + 发布
        GraphQLResponseBean impResp = execute(
                "mutation { NopMetaModule__importOrmModel(path: \"/nop/metadata/orm/app.orm.xml\")" +
                        " { metaModuleId } }");
        assertFalse(impResp.hasError(), "import should not error: " + impResp);
        String metaModuleId = (String) mutationResult(impResp).get("metaModuleId");

        GraphQLResponseBean relResp = execute(
                "mutation { NopMetaModule__releaseModule(metaModuleId: \"" + metaModuleId + "\") { status } }");
        assertFalse(relResp.hasError(), "first release should not error: " + relResp);

        // 再次发布已 RELEASED 的模块 → 必须抛异常（不可变，无静默跳过）
        GraphQLResponseBean reRelResp = execute(
                "mutation { NopMetaModule__releaseModule(metaModuleId: \"" + metaModuleId + "\") { status } }");
        assertTrue(reRelResp.hasError(),
                "re-release of RELEASED module must error (immutability): " + reRelResp);
    }

    @Test
    public void testVersionIncrementOnReimport() {
        // 首次导入同一 moduleId → version=1
        GraphQLResponseBean imp1 = execute(
                "mutation { NopMetaModule__importOrmModel(path: \"/nop/metadata/orm/app.orm.xml\")" +
                        " { metaModuleId moduleVersion } }");
        assertFalse(imp1.hasError(), "first import should not error: " + imp1);
        assertEquals(1L, mutationResult(imp1).get("moduleVersion"),
                "first import version should be 1: " + imp1);

        // 再次导入同一 moduleId → version=2（import 时递增）
        GraphQLResponseBean imp2 = execute(
                "mutation { NopMetaModule__importOrmModel(path: \"/nop/metadata/orm/app.orm.xml\")" +
                        " { metaModuleId moduleVersion status } }");
        assertFalse(imp2.hasError(), "second import should not error: " + imp2);

        Map<String, Object> imp2Data = mutationResult(imp2);
        assertEquals(2L, imp2Data.get("moduleVersion"),
                "second import version should be 2 (import-time increment): " + imp2Data);
        assertEquals("DRAFTING", imp2Data.get("status"),
                "second import status should be DRAFTING: " + imp2Data);

        // release 第二个版本 → version 保持 2
        String metaModuleId2 = (String) imp2Data.get("metaModuleId");
        GraphQLResponseBean relResp = execute(
                "mutation { NopMetaModule__releaseModule(metaModuleId: \"" + metaModuleId2 + "\")" +
                        " { moduleVersion status } }");
        assertFalse(relResp.hasError(), "release of v2 should not error: " + relResp);
        Map<String, Object> relData = mutationResult(relResp);
        assertEquals(2L, relData.get("moduleVersion"), "version should remain 2 after release: " + relData);
        assertEquals("RELEASED", relData.get("status"), "status should be RELEASED: " + relData);
    }

    @Test
    public void testDeltaDualStorageNoExtends() {
        // 导入专用测试 fixture（无 x:extends）：isDelta 双重存储
        GraphQLResponseBean impResp = execute(
                "mutation { NopMetaModule__importOrmModel(path: \"/test/orm/simple.orm.xml\")" +
                        " { metaModuleId } }");
        assertFalse(impResp.hasError(), "import simple fixture should not error: " + impResp);

        // 查询所有 NopMetaOrmModel 记录，双重存储应产生 2 条（isDelta=true + isDelta=false）
        GraphQLResponseBean ormResp = execute(
                "query { NopMetaOrmModel__findPage { total items { ormModelId isDelta modelName } } }");
        assertFalse(ormResp.hasError(), "orm model query should not error: " + ormResp);
        String ormData = String.valueOf(ormResp.getData());
        assertTrue(ormData.contains("total=2"), "dual storage should produce 2 orm model records: " + ormData);
        assertTrue(ormData.contains("isDelta=1"), "should have isDelta=true record: " + ormData);
        assertTrue(ormData.contains("isDelta=0"), "should have isDelta=false record: " + ormData);

        // 端到端验证：双重存储后 NopMetaEntity 记录数 = 2 实体 × 2（delta+full）= 4
        GraphQLResponseBean entityResp = execute(
                "query { NopMetaEntity__findPage { total } }");
        assertFalse(entityResp.hasError(), "entity query should not error: " + entityResp);
        String entityData = String.valueOf(entityResp.getData());
        assertTrue(entityData.contains("total=4"),
                "dual storage should produce 4 entity records (2 entities x 2 delta/full): " + entityData);
    }

    @Test
    public void testGenerateManifest() {
        // 端到端：导入 nop-metadata 自身 orm.xml → generateManifest → content JSON 含 nodes + 依赖图
        GraphQLResponseBean impResp = execute(
                "mutation { NopMetaModule__importOrmModel(path: \"/nop/metadata/orm/app.orm.xml\")" +
                        " { metaModuleId } }");
        assertFalse(impResp.hasError(), "import should not error: " + impResp);
        String metaModuleId = (String) mutationResult(impResp).get("metaModuleId");

        GraphQLResponseBean genResp = execute(
                "mutation { NopMetaModule__generateManifest(metaModuleId: \"" + metaModuleId + "\")" +
                        " { manifestId manifestVersion content } }");
        assertFalse(genResp.hasError(), "generateManifest should not error: " + genResp
                + " errors=" + genResp.getErrors());

        Map<String, Object> genData = mutationResult(genResp);
        assertNotNull(genData.get("manifestId"), "manifestId should be set: " + genData);
        assertEquals(1L, genData.get("manifestVersion"), "first manifestVersion should be 1: " + genData);

        // content 为 JSON 字符串，解析后断言结构
        Object contentObj = genData.get("content");
        assertNotNull(contentObj, "content must not be null: " + genData);
        // content 可能是 String 或已被 GraphQL 反序列化为 Map，统一转 Map
        @SuppressWarnings("unchecked")
        Map<String, Object> content = contentObj instanceof Map
                ? (Map<String, Object>) contentObj
                : (Map<String, Object>) JsonTool.parseNonStrict(String.valueOf(contentObj));
        assertNotNull(content, "content must be a JSON object: " + contentObj);

        // metadata
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) content.get("metadata");
        assertNotNull(metadata, "metadata section required: " + content);
        assertEquals("nop/metadata", metadata.get("moduleId"),
                "moduleId should be the business id (slash form): " + metadata);

        // nodes：含 entity 节点，uniqueId 形如 entity.<归一化moduleId>.<简单类名>
        @SuppressWarnings("unchecked")
        Map<String, Object> nodes = (Map<String, Object>) content.get("nodes");
        assertNotNull(nodes, "nodes section required: " + content);
        assertFalse(nodes.isEmpty(), "nodes must not be empty: " + content);
        assertTrue(nodes.containsKey("entity.nop.metadata.NopMetaModule"),
                "nodes should contain entity.nop.metadata.NopMetaModule: " + nodes.keySet());

        // 钉死的预期样例边：NopMetaOrmModel.metaModule to-one relation → NopMetaModule。
        // 解析后两端 uniqueId：
        //   owner (NopMetaOrmModel) -> entity.nop.metadata.NopMetaOrmModel
        //   target (NopMetaModule)  -> entity.nop.metadata.NopMetaModule
        // owner 依赖 target：parentMap[owner] 含 target；childMap[target] 含 owner
        @SuppressWarnings("unchecked")
        Map<String, List<Object>> parentMap = (Map<String, List<Object>>) content.get("parentMap");
        @SuppressWarnings("unchecked")
        Map<String, List<Object>> childMap = (Map<String, List<Object>>) content.get("childMap");
        assertNotNull(parentMap, "parentMap section required: " + content);
        assertNotNull(childMap, "childMap section required: " + content);

        String ownerUid = "entity.nop.metadata.NopMetaOrmModel";
        String targetUid = "entity.nop.metadata.NopMetaModule";
        List<Object> ownerParents = parentMap.get(ownerUid);
        assertNotNull(ownerParents, "parentMap must have entry for NopMetaOrmModel: " + parentMap.keySet());
        assertTrue(ownerParents.contains(targetUid),
                "parentMap[entity.nop.metadata.NopMetaOrmModel] must contain entity.nop.metadata.NopMetaModule: "
                        + ownerParents);
        List<Object> targetChildren = childMap.get(targetUid);
        assertNotNull(targetChildren, "childMap must have entry for NopMetaModule: " + childMap.keySet());
        assertTrue(targetChildren.contains(ownerUid),
                "childMap[entity.nop.metadata.NopMetaModule] must contain entity.nop.metadata.NopMetaOrmModel: "
                        + targetChildren);

        // 无关系的节点显式空数组（不静默跳过）：
        // (a) 每个节点在 parentMap/childMap 中都有条目（无静默跳过）；
        // (b) 至少存在一个 parentMap 为空数组的节点（证明 relation-less 节点显式置空，而非省略）。
        for (String uid : nodes.keySet()) {
            assertTrue(parentMap.containsKey(uid),
                    "parentMap must have explicit entry for every node (no silent skip): " + uid);
            assertTrue(childMap.containsKey(uid),
                    "childMap must have explicit entry for every node (no silent skip): " + uid);
        }
        boolean hasEmptyParent = false;
        for (List<Object> parents : parentMap.values()) {
            if (parents.isEmpty()) {
                hasEmptyParent = true;
                break;
            }
        }
        assertTrue(hasEmptyParent,
                "at least one node should have empty parentMap (relation-less nodes explicit empty, not skipped): "
                        + parentMap);

        // unresolved 边（若有）必须带 unresolved: 标记（不静默丢弃）——此处仅校验格式约定
        for (Map.Entry<String, List<Object>> e : parentMap.entrySet()) {
            for (Object v : e.getValue()) {
                String s = String.valueOf(v);
                // 目标端要么是 entity.<...> 节点，要么是 unresolved:<className>
                assertTrue(s.startsWith("entity.") || s.startsWith("unresolved:"),
                        "edge target must be entity node or unresolved:<className>, got: " + s);
            }
        }
    }

    @Test
    public void testGenerateManifestModuleNotFound() {
        // 缺失 moduleId 必须快速失败（不静默返回空 Manifest / 不 NPE）
        GraphQLResponseBean resp = execute(
                "mutation { NopMetaModule__generateManifest(metaModuleId: \"__not_exist__\") { manifestId } }");
        assertTrue(resp.hasError(),
                "generateManifest with non-existent metaModuleId must error (fast fail): " + resp);
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mutationResult(GraphQLResponseBean response) {
        Map<String, Object> data = (Map<String, Object>) response.getData();
        // data 形如 {NopMetaModule__importOrmModel={...}}，取第一个 mutation 字段的结果
        return (Map<String, Object>) data.values().iterator().next();
    }
}
