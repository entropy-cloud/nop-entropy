package io.nop.metadata.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.dao.entity.NopMetaTableJoin;
import io.nop.metadata.service.entity.NopMetaTableBizModel;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证跨表 JOIN 执行 queryJoinData（架构基线 §4.4.1 D3/D4/D5）：同库 SQL JOIN / 跨库应用层拼接 /
 * joinType=right 显式失败。Anti-Hollow：用真实 importOrmModel 后的 nop_meta_* 实体表，
 * 断言 JOIN 后真实关联行 + 具体列值（非空壳）。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopMetaJoinBizModel extends JunitBaseTestCase {

    public TestNopMetaJoinBizModel() {
        setTestConfig("nop.orm.init-database-schema", true);
    }

    @Inject
    IGraphQLEngine graphQLEngine;
    @Inject
    IDaoProvider daoProvider;
    @Inject
    NopMetaTableBizModel nopMetaTableBizModel;
    @Inject
    io.nop.orm.IOrmTemplate ormTemplate;

    /** 同库 JOIN（同 querySpace）：nop_meta_entity ⋈ nop_meta_entity_field on META_ENTITY_ID → 真实关联行。 */
    @Test
    @SuppressWarnings("unchecked")
    public void testSameDbJoinReturnsRealRows() {
        importModel();
        NopMetaEntity leftEntity = findMetaEntityByTable("nop_meta_entity");
        NopMetaEntity rightEntity = findMetaEntityByTable("nop_meta_entity_field");
        String leftTableId = findEntityTableId("nop_meta_entity");
        String joinId = createJoin(leftTableId, "inner", leftEntity.getMetaEntityId(),
                rightEntity.getMetaEntityId(), "metaEntityId", "metaEntityId", "fld");

        Map<String, Object> result = nopMetaTableBizModel.queryJoinData(leftTableId, joinId, null, null, null, null);
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertNotNull(items, "items must not be null");
        // inner join：每个 field 行匹配其所属 entity 行，结果非空（导入后存在大量字段）
        assertFalse(items.isEmpty(), "same-DB inner join must return real associated rows (not empty stub): " + items);
        // 接线验证：左 join key（META_ENTITY_ID）+ 右 join key（fld_ 前缀）同时存在且相等
        Map<String, Object> row0 = items.get(0);
        Object leftKey = getIgnoreCase(row0, "META_ENTITY_ID");
        assertNotNull(leftKey, "left join key META_ENTITY_ID must be present: " + row0.keySet());
        assertTrue(row0.keySet().stream().anyMatch(k -> k.toUpperCase().startsWith("FLD_")),
                "right join key must be alias-prefixed: " + row0.keySet());
    }

    /** 跨库 JOIN（不同 querySpace）：覆盖右实体 querySpace 元数据 → 触发应用层拼接，断言合并结果 + 列前缀。 */
    @Test
    @SuppressWarnings("unchecked")
    public void testCrossDbJoinAppLayerMerge() {
        importModel();
        NopMetaEntity leftEntity = findMetaEntityByTable("nop_meta_entity");
        NopMetaEntity rightEntity = findMetaEntityByTable("nop_meta_entity_field");
        // 覆盖右实体 querySpace 元数据为不同值，触发跨库路由（D3）。直接 SQL UPDATE 写库 + 清缓存，使
        // executor 的 getEntityById 重读到新值（经实测 getEntityById 每次重读 DB，flushSession 不生效）。
        // 两实体仍注册于同一 ORM，取数照常工作。
        updateQuerySpaceSql(rightEntity.getMetaEntityId(), "qs_cross_db_b");
        try {
            String leftTableId = findEntityTableId("nop_meta_entity");
            String joinId = createJoin(leftTableId, "inner", leftEntity.getMetaEntityId(),
                    rightEntity.getMetaEntityId(), "metaEntityId", "metaEntityId", "fld");

            Map<String, Object> result = nopMetaTableBizModel.queryJoinData(leftTableId, joinId, null, null, null, null);
            List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
            assertNotNull(items, "items must not be null");
            assertFalse(items.isEmpty(), "cross-DB app-layer merge must return real merged rows: " + items);
            // 跨库合并：左列 + 右列均为属性名（camelCase），冲突列加 fld_ 前缀（D5）
            Map<String, Object> row0 = items.get(0);
            Object leftVal = getIgnoreCase(row0, "metaEntityId");
            assertNotNull(leftVal, "left join key metaEntityId must be present: " + row0.keySet());
            assertTrue(row0.keySet().stream().anyMatch(k -> k.startsWith("fld_") || k.startsWith("FLD_")),
                    "right columns must be alias-prefixed on collision: " + row0.keySet());
        } finally {
            // 还原 querySpace，避免污染其他测试
            updateQuerySpaceSql(rightEntity.getMetaEntityId(), null);
        }
    }

    /** joinType=right 显式失败（首版不支持，不静默降级）。 */
    @Test
    public void testJoinTypeRightExplicitlyFails() {
        importModel();
        NopMetaEntity leftEntity = findMetaEntityByTable("nop_meta_entity");
        NopMetaEntity rightEntity = findMetaEntityByTable("nop_meta_entity_field");
        String leftTableId = findEntityTableId("nop_meta_entity");
        String joinId = createJoin(leftTableId, "right", leftEntity.getMetaEntityId(),
                rightEntity.getMetaEntityId(), "metaEntityId", "metaEntityId", "fld");

        assertTrue(queryJoinDataHasError(leftTableId, joinId),
                "joinType=right must explicitly fail (not silently degrade)");
    }

    /** 无 join 定义（joinId 不存在）显式失败。 */
    @Test
    public void testJoinNotFoundFails() {
        importModel();
        String leftTableId = findEntityTableId("nop_meta_entity");
        assertTrue(queryJoinDataHasError(leftTableId, "__no_such_join__"),
                "non-existent join must explicitly fail");
    }

    /** join 字段无法解析为物理列 → 显式失败（不静默空集）。 */
    @Test
    public void testJoinFieldNotResolvedFails() {
        importModel();
        NopMetaEntity leftEntity = findMetaEntityByTable("nop_meta_entity");
        NopMetaEntity rightEntity = findMetaEntityByTable("nop_meta_entity_field");
        String leftTableId = findEntityTableId("nop_meta_entity");
        String joinId = createJoin(leftTableId, "inner", leftEntity.getMetaEntityId(),
                rightEntity.getMetaEntityId(), "__not_a_real_field__", "metaEntityId", "fld");
        assertTrue(queryJoinDataHasError(leftTableId, joinId),
                "unresolvable join field must explicitly fail");
    }

    // ===== helpers =====

    private void importModel() {
        io.nop.api.core.beans.graphql.GraphQLRequestBean request = new io.nop.api.core.beans.graphql.GraphQLRequestBean();
        request.setQuery("mutation { NopMetaModule__importOrmModel(path: \"/nop/metadata/orm/app.orm.xml\") { metaModuleId } }");
        graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(request));
    }

    /** 直接 SQL UPDATE NOP_META_ENTITY.QUERY_SPACE 并清缓存，使后续 getEntityById 重读新值。 */
    private void updateQuerySpaceSql(String metaEntityId, String querySpace) {
        io.nop.core.lang.sql.SQL upd = io.nop.core.lang.sql.SQL.begin().allowUnderscoreName(true)
                .sql("update NOP_META_ENTITY set QUERY_SPACE=? where META_ENTITY_ID=?",
                        querySpace == null ? "" : querySpace, metaEntityId)
                .end();
        ormTemplate.executeUpdate(upd);
        ormTemplate.evictAll(io.nop.metadata.dao.entity.NopMetaEntity.class.getName());
    }

    private NopMetaEntity findMetaEntityByTable(String tableName) {
        IEntityDao<NopMetaEntity> dao = daoProvider.daoFor(NopMetaEntity.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaEntity.PROP_NAME_tableName, tableName));
        NopMetaEntity e = dao.findFirstByQuery(q);
        assertNotNull(e, "MetaEntity for table " + tableName + " must exist after import");
        return e;
    }

    private String findEntityTableId(String tableName) {
        IEntityDao<NopMetaTable> dao = daoProvider.daoFor(NopMetaTable.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaTable.PROP_NAME_tableName, tableName));
        q.addFilter(FilterBeans.eq("tableType", "entity"));
        NopMetaTable t = dao.findFirstByQuery(q);
        assertNotNull(t, "entity table " + tableName + " must be created by importOrmModel");
        return t.getMetaTableId();
    }

    private String createJoin(String metaTableId, String joinType, String leftEntityId, String rightEntityId,
                              String leftField, String rightField, String alias) {
        IEntityDao<NopMetaTableJoin> dao = daoProvider.daoFor(NopMetaTableJoin.class);
        NopMetaTableJoin join = dao.newEntity();
        join.setMetaTableId(metaTableId);
        join.setJoinType(joinType);
        join.setLeftEntityId(leftEntityId);
        join.setRightEntityId(rightEntityId);
        join.setLeftField(leftField);
        join.setRightField(rightField);
        join.setAlias(alias);
        join.setVersion(1L);
        Timestamp now = new Timestamp(System.currentTimeMillis());
        join.setCreatedBy("autotest");
        join.setCreateTime(now);
        dao.saveEntity(join);
        return join.getJoinId();
    }

    private boolean queryJoinDataHasError(String tableId, String joinId) {
        try {
            nopMetaTableBizModel.queryJoinData(tableId, joinId, null, null, null, null);
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    private static boolean hasKeyIgnoreCase(Map<String, Object> map, String key) {
        return map.keySet().stream().anyMatch(k -> k.equalsIgnoreCase(key));
    }

    private static Object getIgnoreCase(Map<String, Object> map, String key) {
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (e.getKey().equalsIgnoreCase(key)) {
                return e.getValue();
            }
        }
        return null;
    }
}
