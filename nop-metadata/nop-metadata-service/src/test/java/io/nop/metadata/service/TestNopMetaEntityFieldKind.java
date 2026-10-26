package io.nop.metadata.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.metadata.dao.entity.NopMetaDict;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M2（plan 2026-10-26-2047-1 Phase 2）：计算属性/别名字段端到端 + kind 互斥守卫族。
 *
 * <p>覆盖：computed（XLang 行上求值，SIMPLE 特性集）/alias（别名列名）出现在 queryData
 * 返回行、storage 字段行为零变化、save-time 标识符白名单与可解析校验、kind 互斥守卫
 * 拒绝路径、computed 求值失败显式报错（不静默置 null）。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopMetaEntityFieldKind extends JunitBaseTestCase {

    private static final String ERR_PREFIX = "nop.err.metadata.";

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    private final Timestamp now = new Timestamp(System.currentTimeMillis());

    private String newHostEntity(String id) {
        IEntityDao<NopMetaEntity> dao = daoProvider.daoFor(NopMetaEntity.class);
        NopMetaEntity entity = dao.newEntity();
        entity.setMetaEntityId(id);
        entity.setOrmModelId("orm-m2-test");
        entity.setIsDelta((byte) 0);
        entity.setEntityName("io.nop.metadata.dao.entity.NopMetaDict");
        entity.setEntityKind("PHYSICAL");
        entity.setMetaModuleId("mod-m2-test");
        entity.setVersion(1L);
        entity.setCreatedBy("autotest");
        entity.setUpdatedBy("autotest");
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        dao.saveEntity(entity);
        return id;
    }

    private void newDict(String dictId, String dictName, String label) {
        IEntityDao<NopMetaDict> dao = daoProvider.daoFor(NopMetaDict.class);
        NopMetaDict dict = dao.newEntity();
        dict.setMetaDictId(dictId);
        dict.setOrmModelId("orm-m2-test");
        dict.setIsDelta((byte) 0);
        dict.setDictName(dictName);
        dict.setLabel(label);
        dict.setVersion(1L);
        dict.setCreatedBy("autotest");
        dict.setUpdatedBy("autotest");
        dict.setCreateTime(now);
        dict.setUpdateTime(now);
        dao.saveEntity(dict);
    }

    /** 登记 storage 字段行（别名目标/白名单基准）。 */
    private void saveStorageField(String hostEntityId, String fieldName) {
        Map<String, Object> data = new HashMap<>();
        data.put("entityFieldId", "fld-" + hostEntityId + "-" + fieldName);
        data.put("metaEntityId", hostEntityId);
        data.put("fieldName", fieldName);
        data.put("stdDataType", "string");
        data.put("version", 1);
        saveField(data);
    }

    private GraphQLResponseBean saveField(Map<String, Object> data) {
        return executeWithVars("mutation($data:Map) { NopMetaEntityField__save(data:$data) "
                + "{ entityFieldId fieldName fieldKind } }", Map.of("data", data));
    }

    private GraphQLResponseBean updateField(Map<String, Object> data) {
        return executeWithVars("mutation($data:Map) { NopMetaEntityField__update(data:$data) "
                + "{ entityFieldId fieldName fieldKind } }", Map.of("data", data));
    }

    private GraphQLResponseBean queryData(String hostEntityId) {
        return execute("query { NopMetaEntity__queryData(metaEntityId: \"" + hostEntityId
                + "\") { entityKind items } }");
    }

    private String firstErrorCode(GraphQLResponseBean response) {
        assertTrue(response.hasError(), "expected error but got: " + response);
        return response.getErrorCode();
    }

    @Test
    public void testComputedAndAliasFieldsInQueryData() {
        String hostId = newHostEntity("ent-m2-1");
        newDict("dict-m2-a", "alpha", "Alpha Label");
        newDict("dict-m2-b", "beta", "Beta Label");

        saveStorageField(hostId, "dictName");
        saveStorageField(hostId, "label");

        // computed：XLang 行上求值（label + '!'）
        Map<String, Object> computed = new HashMap<>();
        computed.put("entityFieldId", "fld-m2-mark");
        computed.put("metaEntityId", hostId);
        computed.put("fieldName", "labelMark");
        computed.put("fieldKind", "computed");
        computed.put("computeExpr", "label + '!'");
        computed.put("version", 1);
        assertFalse(saveField(computed).hasError(), "computed field must save: ");

        // alias：dictName 的别名列名
        Map<String, Object> alias = new HashMap<>();
        alias.put("entityFieldId", "fld-m2-alias");
        alias.put("metaEntityId", hostId);
        alias.put("fieldName", "nameAlias");
        alias.put("fieldKind", "alias");
        alias.put("mapToProp", "dictName");
        alias.put("version", 1);
        assertFalse(saveField(alias).hasError(), "alias field must save");

        GraphQLResponseBean resp = queryData(hostId);
        assertFalse(resp.hasError(), "queryData should not error: " + resp);
        String items = resp.getData().toString();
        // computed 求值结果
        assertTrue(items.contains("Alpha Label!"), "computed projection should evaluate per row: " + items);
        // alias 以别名列名出现
        assertTrue(items.contains("nameAlias=alpha"), "alias should expose target value under alias name: " + items);
        // storage 字段行为零变化（原列仍在）
        assertTrue(items.contains("dictName=alpha"), "storage column must remain unchanged: " + items);
    }

    @Test
    public void testComputedSaveTimeValidation() {
        String hostId = newHostEntity("ent-m2-2");
        saveStorageField(hostId, "dictName");

        // 标识符不在实体字段集
        Map<String, Object> unknownRef = new HashMap<>();
        unknownRef.put("entityFieldId", "fld-m2-unknown");
        unknownRef.put("metaEntityId", hostId);
        unknownRef.put("fieldName", "badRef");
        unknownRef.put("fieldKind", "computed");
        unknownRef.put("computeExpr", "nonExistentField + 1");
        unknownRef.put("version", 1);
        assertEquals(ERR_PREFIX + "field-computed-identifier-unknown", firstErrorCode(saveField(unknownRef)));

        // SIMPLE 特性集外（import 语句）→ 不可解析
        Map<String, Object> unparseable = new HashMap<>();
        unparseable.put("entityFieldId", "fld-m2-import");
        unparseable.put("metaEntityId", hostId);
        unparseable.put("fieldName", "badSyntax");
        unparseable.put("fieldKind", "computed");
        unparseable.put("computeExpr", "import java.io.File");
        unparseable.put("version", 1);
        assertEquals(ERR_PREFIX + "field-computed-expr-unparseable", firstErrorCode(saveField(unparseable)));
    }

    @Test
    public void testKindMutexGuards() {
        String hostId = newHostEntity("ent-m2-3");
        saveStorageField(hostId, "dictName");

        // storage 带 computeExpr
        Map<String, Object> storageExpr = new HashMap<>();
        storageExpr.put("entityFieldId", "fld-m2-se");
        storageExpr.put("metaEntityId", hostId);
        storageExpr.put("fieldName", "plain");
        storageExpr.put("computeExpr", "dictName");
        storageExpr.put("version", 1);
        assertEquals(ERR_PREFIX + "field-storage-expr-forbidden", firstErrorCode(saveField(storageExpr)));

        // computed 无表达式
        Map<String, Object> noExpr = new HashMap<>();
        noExpr.put("entityFieldId", "fld-m2-ne");
        noExpr.put("metaEntityId", hostId);
        noExpr.put("fieldName", "noExpr");
        noExpr.put("fieldKind", "computed");
        noExpr.put("version", 1);
        assertEquals(ERR_PREFIX + "field-computed-expr-required", firstErrorCode(saveField(noExpr)));

        // alias 目标不存在
        Map<String, Object> aliasMissing = new HashMap<>();
        aliasMissing.put("entityFieldId", "fld-m2-am");
        aliasMissing.put("metaEntityId", hostId);
        aliasMissing.put("fieldName", "aliasMissing");
        aliasMissing.put("fieldKind", "alias");
        aliasMissing.put("mapToProp", "notThere");
        aliasMissing.put("version", 1);
        assertEquals(ERR_PREFIX + "field-alias-target-unknown", firstErrorCode(saveField(aliasMissing)));

        // alias 自引用
        Map<String, Object> aliasSelf = new HashMap<>(aliasMissing);
        aliasSelf.put("entityFieldId", "fld-m2-as");
        aliasSelf.put("fieldName", "selfAlias");
        aliasSelf.put("mapToProp", "selfAlias");
        assertEquals(ERR_PREFIX + "field-alias-target-unknown", firstErrorCode(saveField(aliasSelf)));

        // alias 目标非 storage（目标为 computed）
        Map<String, Object> computedTarget = new HashMap<>();
        computedTarget.put("entityFieldId", "fld-m2-ct");
        computedTarget.put("metaEntityId", hostId);
        computedTarget.put("fieldName", "computedField");
        computedTarget.put("fieldKind", "computed");
        computedTarget.put("computeExpr", "dictName");
        computedTarget.put("version", 1);
        assertFalse(saveField(computedTarget).hasError(), "computed target must save");

        Map<String, Object> aliasToComputed = new HashMap<>();
        aliasToComputed.put("entityFieldId", "fld-m2-ac");
        aliasToComputed.put("metaEntityId", hostId);
        aliasToComputed.put("fieldName", "aliasToComputed");
        aliasToComputed.put("fieldKind", "alias");
        aliasToComputed.put("mapToProp", "computedField");
        aliasToComputed.put("version", 1);
        assertEquals(ERR_PREFIX + "field-alias-target-unknown", firstErrorCode(saveField(aliasToComputed)));
    }

    @Test
    public void testComputedEvalFailedExplicit() {
        String hostId = newHostEntity("ent-m2-4");
        // label 为 null 的行：label.length() 求值 NPE → 显式报错不静默置 null
        newDict("dict-m2-null", "gamma", null);
        saveStorageField(hostId, "dictName");
        saveStorageField(hostId, "label");

        Map<String, Object> computed = new HashMap<>();
        computed.put("entityFieldId", "fld-m2-len");
        computed.put("metaEntityId", hostId);
        computed.put("fieldName", "labelLen");
        computed.put("fieldKind", "computed");
        computed.put("computeExpr", "label.length()");
        computed.put("version", 1);
        assertFalse(saveField(computed).hasError(), "computed field must save");

        GraphQLResponseBean resp = queryData(hostId);
        assertEquals(ERR_PREFIX + "field-computed-eval-failed", firstErrorCode(resp),
                "eval failure must surface as explicit error: " + resp);
    }

    @Test
    public void testUpdateGuardSymmetricAndStorageDefault() {
        String hostId = newHostEntity("ent-m2-5");
        saveStorageField(hostId, "dictName");

        // storage 缺省注入：不带 fieldKind 保存 → fieldKind=storage
        Map<String, Object> plain = new HashMap<>();
        plain.put("entityFieldId", "fld-m2-plain");
        plain.put("metaEntityId", hostId);
        plain.put("fieldName", "plainField");
        plain.put("version", 1);
        GraphQLResponseBean saved = saveField(plain);
        assertFalse(saved.hasError(), "plain field must save: " + saved);
        assertTrue(saved.getData().toString().contains("storage"),
                "fieldKind default injection should yield storage: " + saved.getData());

        // update 把 storage 字段改为带 mapToProp → 守卫拒绝（对称写面）
        Map<String, Object> badUpdate = new HashMap<>();
        badUpdate.put("id", "fld-m2-plain");
        badUpdate.put("mapToProp", "dictName");
        assertEquals(ERR_PREFIX + "field-storage-expr-forbidden", firstErrorCode(updateField(badUpdate)));

        // update 改为合法 alias → 通过
        Map<String, Object> goodUpdate = new HashMap<>();
        goodUpdate.put("id", "fld-m2-plain");
        goodUpdate.put("fieldKind", "alias");
        goodUpdate.put("mapToProp", "dictName");
        assertFalse(updateField(goodUpdate).hasError(), "legal alias conversion must pass");
    }

    private GraphQLResponseBean execute(String query) {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        IGraphQLExecutionContext context = graphQLEngine.newGraphQLContext(request);
        return graphQLEngine.executeGraphQL(context);
    }

    private GraphQLResponseBean executeWithVars(String query, Map<String, Object> vars) {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        request.setVariables(vars);
        IGraphQLExecutionContext context = graphQLEngine.newGraphQLContext(request);
        return graphQLEngine.executeGraphQL(context);
    }
}
