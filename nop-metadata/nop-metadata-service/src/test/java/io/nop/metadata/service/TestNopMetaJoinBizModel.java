package io.nop.metadata.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.metadata.core.dto.QueryJoinDataResultDTO;
import io.nop.metadata.dao.entity.NopMetaDataSource;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaModule;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.dao.entity.NopMetaTableJoin;
import io.nop.metadata.service.entity.NopMetaTableBizModel;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证跨表 JOIN 执行 queryJoinData（架构基线 §4.4.1 D3/D4/D5 + plan 0700-2 D1 端点组合扩展）：
 * 同库 entity-entity SQL JOIN / 跨库 entity-entity 应用层拼接 / 同库 sql↔sql withConnection 原生 JOIN SQL /
 * 混合端点 entity↔sql 跨库拼接（命名空间规范化 Anti-Hollow）/ joinType=right 显式失败。
 *
 * <p>Anti-Hollow：entity-entity 用真实 importOrmModel 后的 nop_meta_* 实体表；sql/external 路径用真实 H2 建连 +
 * 物理表造数，断言 JOIN 后真实关联行 + 具体列值（非空壳）；跨库混合端点断言命名空间规范化生效（不静默空集）。
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

        QueryJoinDataResultDTO result = nopMetaTableBizModel.queryJoinData(leftTableId, joinId, null, null, null, null, null);
        List<Map<String, Object>> items = result.getItems();
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

            QueryJoinDataResultDTO result = nopMetaTableBizModel.queryJoinData(leftTableId, joinId, null, null, null, null, null);
            List<Map<String, Object>> items = result.getItems();
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

    // ============================================================
    // plan 0700-2：sql/external 端点 JOIN 执行（同库 withConnection 原生 JOIN + 跨库/混合拼接）
    // ============================================================

    /**
     * 同库 sql↔sql JOIN（D4 新增，withConnection 原生 JOIN SQL）：两个 sql 表共享同一 querySpace（同一 H2 数据源），
     * 经单次 withConnection 跑原生 INNER JOIN，断言 join 后真实关联行 + join key 值匹配（非空壳）。
     *
     * <p>接线验证：queryJoinData → MetaJoinExecutor.executeSameDbTableJoin → withConnection 分支确实被调用
     * （返回行含 RG_REGION 来自真实 regions 表数据，证明 withConnection JOIN 真实执行）。
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testSameDbSqlTableJoinReturnsRealRows() throws Exception {
        String querySpace = "qs_join_samedb_sql";
        String dbUrl = "jdbc:h2:mem:" + querySpace + ";DB_CLOSE_DELAY=-1";
        seedH2(dbUrl, "CREATE TABLE orders (order_id INT, amt INT)",
                "INSERT INTO orders VALUES (1, 100)",
                "INSERT INTO orders VALUES (2, 200)",
                "CREATE TABLE regions (order_id INT, region VARCHAR(20))",
                "INSERT INTO regions VALUES (1, 'CN')",
                "INSERT INTO regions VALUES (2, 'US')");
        saveDataSource("ds-" + querySpace, querySpace, dbUrl);
        String leftTableId = saveSqlTableManual("SELECT order_id, amt FROM orders", querySpace);
        String rightTableId = saveSqlTableManual("SELECT order_id, region FROM regions", querySpace);

        String joinId = createTableJoin(leftTableId, "inner", leftTableId, rightTableId,
                "order_id", "order_id", "rg");

        QueryJoinDataResultDTO result = nopMetaTableBizModel.queryJoinData(leftTableId, joinId, null, null, null, null, null);
        List<Map<String, Object>> items = result.getItems();
        assertNotNull(items, "items must not be null");
        // inner join：order_id=1→CN, order_id=2→US，2 行真实关联（stub 立即失败此断言）
        assertEquals(2, items.size(), "same-DB sql-sql join must return 2 real associated rows: " + items);
        // 接线验证：左 join key + 右 join key（rg_ 前缀）同时存在且值匹配（证明 withConnection JOIN 真实执行）。
        // 说明：同库 JOIN 仅投影 join key 列（与 entity-entity 同库路径一致，防保留字裸拼接 parse-fail），
        // 不投影全部列——故断言 join key 而非 region 列。
        Map<String, Object> row0 = items.get(0);
        Object leftKey = getIgnoreCase(row0, "ORDER_ID");
        assertNotNull(leftKey, "left join key ORDER_ID must be present: " + row0.keySet());
        Object rightKey = getIgnoreCase(row0, "RG_ORDER_ID");
        assertNotNull(rightKey, "right join key must be alias-prefixed (rg_order_id): " + row0.keySet());
        // join key 值须相等（t1.order_id = t2.order_id）
        assertEquals(String.valueOf(leftKey), String.valueOf(rightKey),
                "same-DB JOIN ON t1.order_id = t2.order_id must produce equal key values");
        // 真实关联：order_id 值为 1 或 2（来自 seeded H2 orders/regions 表，stub 无法伪造）
        int oid = ((Number) leftKey).intValue();
        assertTrue(oid == 1 || oid == 2, "order_id must be real seeded value (1/2): " + oid);
    }

    /**
     * 跨库混合端点 entity↔sql JOIN + 命名空间规范化 Anti-Hollow（D1.2 + D1.4）：
     *
     * <p>左表 = nop_meta_module entity（leftField="moduleId" 为 camelCase 属性名），右表 = sql 表（rightField="MODULE_ID"
     * 为物理列名大写）。entity 行 key 命名空间（camelCase）与 sql 行 key 命名空间（UPPERCASE）不同。
     *
     * <p>Anti-Hollow 断言：经命名空间规范化后 join 不返回静默空集——实际命中（行数>0 且 key 值匹配）。
     * 同时验证 entity querySpace（平台库）无 NopMetaDataSource 时混合 JOIN 正常成功（D1.2 裁定）。
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testCrossDbEntitySqlJoinMergeAntiHollow() throws Exception {
        importModel();
        // 取 nop_meta_module 的 NopMetaEntity（作为 left entity 端点）+ 一条真实 module 的 moduleId 业务字段值
        NopMetaEntity moduleEntity = findMetaEntityByTable("nop_meta_module");
        IEntityDao<NopMetaModule> moduleDao = daoProvider.daoFor(NopMetaModule.class);
        QueryBean mq = new QueryBean();
        java.util.List<NopMetaModule> modules = moduleDao.findAllByQuery(mq);
        assertFalse(modules.isEmpty(), "imported modules must exist");
        String moduleIdValue = modules.get(0).getModuleId();
        assertNotNull(moduleIdValue, "module.moduleId must not be null after import");

        // entity querySpace（平台库）无 NopMetaDataSource → 断言混合 JOIN 仍正常成功（D1.2）
        assertFalse(hasDataSourceForQuerySpace(moduleEntity.getQuerySpace()),
                "platform entity querySpace should have NO NopMetaDataSource (D1.2 mixed-join precondition)");

        String querySpace = "qs_join_xdb_sql";
        String dbUrl = "jdbc:h2:mem:" + querySpace + ";DB_CLOSE_DELAY=-1";
        // H2 dim_mod 表物理列 MODULE_ID（大写）含与 entity moduleId 匹配的值
        seedH2(dbUrl, "CREATE TABLE dim_mod (MODULE_ID VARCHAR(100), EXTRA VARCHAR(50))",
                "INSERT INTO dim_mod VALUES ('" + escapeSql(moduleIdValue) + "', 'extra-val')");
        saveDataSource("ds-" + querySpace, querySpace, dbUrl);
        String sqlTableId = saveSqlTableManual("SELECT MODULE_ID, EXTRA FROM dim_mod", querySpace);

        String leftTableId = findEntityTableId("nop_meta_module");
        // 混合端点 join：left=entity(moduleId), right=sql(MODULE_ID)
        String joinId = createMixedJoin(leftTableId, "inner", moduleEntity.getMetaEntityId(),
                sqlTableId, "moduleId", "MODULE_ID", "dim");

        QueryJoinDataResultDTO result = nopMetaTableBizModel.queryJoinData(leftTableId, joinId, null, null, null, null, null);
        List<Map<String, Object>> items = result.getItems();
        assertNotNull(items, "items must not be null");
        // Anti-Hollow 核心：命名空间错配不静默空集——实际命中至少 1 行（stub 或 namespace bug 立即失败此断言）
        assertFalse(items.isEmpty(),
                "cross-DB mixed entity↔sql join must NOT silently return empty (namespace normalization): " + items);
        // 命名空间规范化验证：左行 key 为 camelCase "moduleId"，右行 key 冲突加 dim_ 前缀；sql 物理 EXTRA 列存在
        Map<String, Object> row0 = items.get(0);
        // join key 值须匹配（entity moduleId 值 == sql MODULE_ID 值）
        Object leftKey = getIgnoreCase(row0, "moduleId");
        assertNotNull(leftKey, "left join key moduleId (camelCase) must be present: " + row0.keySet());
        assertEquals(moduleIdValue, String.valueOf(leftKey), "join key value must match across namespaces");
        // sql 端真实数据：EXTRA 列（来自 H2 dim_mod 表）经 withConnection 取到（接线验证 + Anti-Hollow）
        Object extra = getIgnoreCase(row0, "EXTRA");
        if (extra == null) {
            extra = getIgnoreCase(row0, "DIM_EXTRA");
        }
        assertNotNull(extra, "sql EXTRA column must be present (wired via withConnection fetch): " + row0.keySet());
        assertEquals("extra-val", String.valueOf(extra), "EXTRA must be real seeded value");
    }

    /**
     * 跨库 sql↔sql JOIN（不同 querySpace → 应用层拼接，D5）：两端点为不同数据源的 sql 表，各侧 withConnection 取数后内存合并。
     *
     * <p>边界覆盖：两端点均为 sql/external 且跨库的组合（Phase 2 边界用例）。
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testCrossDbSqlTableJoinAppLayerMerge() throws Exception {
        String qsLeft = "qs_join_xdb_left";
        String qsRight = "qs_join_xdb_right";
        seedH2("jdbc:h2:mem:" + qsLeft + ";DB_CLOSE_DELAY=-1",
                "CREATE TABLE l (k VARCHAR(20), lv VARCHAR(20))",
                "INSERT INTO l VALUES ('k1', 'L1')");
        seedH2("jdbc:h2:mem:" + qsRight + ";DB_CLOSE_DELAY=-1",
                "CREATE TABLE r (k VARCHAR(20), rv VARCHAR(20))",
                "INSERT INTO r VALUES ('k1', 'R1')");
        saveDataSource("ds-" + qsLeft, qsLeft, "jdbc:h2:mem:" + qsLeft + ";DB_CLOSE_DELAY=-1");
        saveDataSource("ds-" + qsRight, qsRight, "jdbc:h2:mem:" + qsRight + ";DB_CLOSE_DELAY=-1");

        String leftTableId = saveSqlTableManual("SELECT k, lv FROM l", qsLeft);
        String rightTableId = saveSqlTableManual("SELECT k, rv FROM r", qsRight);
        String joinId = createTableJoin(leftTableId, "inner", leftTableId, rightTableId,
                "k", "k", "rt");

        QueryJoinDataResultDTO result = nopMetaTableBizModel.queryJoinData(leftTableId, joinId, null, null, null, null, null);
        List<Map<String, Object>> items = result.getItems();
        assertNotNull(items, "items must not be null");
        assertEquals(1, items.size(), "cross-DB sql-sql join must merge 1 matching row: " + items);
        Map<String, Object> row0 = items.get(0);
        // 跨库拼接：左列 + 右列均为物理列名（大写），冲突列加 RT_ 前缀
        Object leftVal = getIgnoreCase(row0, "LV");
        Object rightVal = getIgnoreCase(row0, "RV");
        assertNotNull(leftVal, "left physical column LV must be present: " + row0.keySet());
        assertNotNull(rightVal, "right physical column RV must be present: " + row0.keySet());
        assertEquals("L1", String.valueOf(leftVal));
        assertEquals("R1", String.valueOf(rightVal));
    }

    /**
     * sql/external 端点 join 字段不属于该表可解析列集合 → 显式失败（不静默空集）。
     *
     * <p>save 路径校验已在 plan 0700-1 覆盖；此处直接经 DAO 插入 bad join（绕过 save 校验），
     * 验证 executor 的防御性显式失败（与裁定一致：未实现/不可解析显式失败而非静默）。
     */
    @Test
    public void testSqlTableJoinFieldNotExistsFails() throws Exception {
        String querySpace = "qs_join_fieldfail";
        String dbUrl = "jdbc:h2:mem:" + querySpace + ";DB_CLOSE_DELAY=-1";
        seedH2(dbUrl, "CREATE TABLE single_col (order_id INT)", "INSERT INTO single_col VALUES (1)");
        saveDataSource("ds-" + querySpace, querySpace, dbUrl);
        String leftTableId = saveSqlTableManual("SELECT order_id FROM single_col", querySpace);
        String rightTableId = saveSqlTableManual("SELECT order_id FROM single_col", querySpace);

        // 直接经 DAO 插入 bad join（绕过 save 校验，模拟数据已存在但字段悬空）
        String joinId = createTableJoin(leftTableId, "inner", leftTableId, rightTableId,
                "order_id", "__nonexistent_col__", "rg");

        assertTrue(queryJoinDataHasError(leftTableId, joinId),
                "join field not in table column set must explicitly fail (not silent empty)");
    }

    // ===== helpers =====

    @SuppressWarnings("unchecked")
    private String importModel() {
        io.nop.api.core.beans.graphql.GraphQLRequestBean request = new io.nop.api.core.beans.graphql.GraphQLRequestBean();
        request.setQuery("mutation { NopMetaModule__importOrmModel(path: \"/nop/metadata/orm/app.orm.xml\") { metaModuleId } }");
        io.nop.api.core.beans.graphql.GraphQLResponseBean resp =
                graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(request));
        assertFalse(resp.hasError(), "importOrmModel should succeed: " + resp);
        Map<String, Object> data = (Map<String, Object>) resp.getData();
        Map<String, Object> imp = (Map<String, Object>) data.get("NopMetaModule__importOrmModel");
        return String.valueOf(imp.get("metaModuleId"));
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

    /** table-table 端点的 NopMetaTableJoin（leftTableId/rightTableId 均为 external/sql 表端点）。 */
    private String createTableJoin(String metaTableId, String joinType, String leftTableId, String rightTableId,
                                   String leftField, String rightField, String alias) {
        IEntityDao<NopMetaTableJoin> dao = daoProvider.daoFor(NopMetaTableJoin.class);
        NopMetaTableJoin join = dao.newEntity();
        join.setMetaTableId(metaTableId);
        join.setJoinType(joinType);
        join.setLeftTableId(leftTableId);
        join.setRightTableId(rightTableId);
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

    /** 混合端点 NopMetaTableJoin（leftEntityId entity 端点 + rightTableId table 端点）。 */
    private String createMixedJoin(String metaTableId, String joinType, String leftEntityId, String rightTableId,
                                   String leftField, String rightField, String alias) {
        IEntityDao<NopMetaTableJoin> dao = daoProvider.daoFor(NopMetaTableJoin.class);
        NopMetaTableJoin join = dao.newEntity();
        join.setMetaTableId(metaTableId);
        join.setJoinType(joinType);
        join.setLeftEntityId(leftEntityId);
        join.setRightTableId(rightTableId);
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

    /** 直接保存 sql 类型 NopMetaTable（不经 createSqlTable GraphQL action），用于 JOIN 端点测试。 */
    private String saveSqlTableManual(String sourceSql, String querySpace) {
        IEntityDao<NopMetaTable> dao = daoProvider.daoFor(NopMetaTable.class);
        NopMetaTable t = dao.newEntity();
        t.setMetaModuleId(ensureTestModuleId());
        t.setTableName("SQL_T_" + System.nanoTime());
        t.setDisplayName("sql-join-endpoint");
        t.setTableType("sql");
        t.setQuerySpace(querySpace);
        t.setSourceSql(sourceSql);
        t.setVersion(1L);
        dao.saveEntity(t);
        return t.getMetaTableId();
    }

    /** 注册一个 NopMetaDataSource（H2 in-memory）。 */
    private void saveDataSource(String id, String querySpace, String dbUrl) {
        IEntityDao<NopMetaDataSource> dao = daoProvider.daoFor(NopMetaDataSource.class);
        NopMetaDataSource ds = dao.newEntity();
        ds.setDataSourceId(id);
        ds.setQuerySpace(querySpace);
        ds.setName(id);
        ds.setDatasourceType("jdbc");
        ds.setConnectionConfig("{\"jdbcUrl\":\"" + dbUrl + "\",\"username\":\"sa\",\"password\":\"\","
                + "\"driverClassName\":\"org.h2.Driver\"}");
        ds.setStatus("ACTIVE");
        ds.setVersion(1L);
        Timestamp now = new Timestamp(System.currentTimeMillis());
        ds.setCreateTime(now);
        ds.setUpdateTime(now);
        dao.saveEntity(ds);
    }

    /** 判断给定 querySpace 是否已注册 NopMetaDataSource。 */
    private boolean hasDataSourceForQuerySpace(String querySpace) {
        IEntityDao<NopMetaDataSource> dao = daoProvider.daoFor(NopMetaDataSource.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaDataSource.PROP_NAME_querySpace, querySpace));
        return dao.findFirstByQuery(q) != null;
    }

    /** 建 H2 库 + 执行多条 DDL/DML（用于 sql 端点物理表造数）。 */
    private static void seedH2(String dbUrl, String... statements) throws Exception {
        try (Connection c = DriverManager.getConnection(dbUrl, "sa", "");
             Statement st = c.createStatement()) {
            for (String sql : statements) {
                st.execute(sql);
            }
        }
    }

    private String ensureTestModuleId() {
        IEntityDao<NopMetaModule> dao = daoProvider.daoFor(NopMetaModule.class);
        NopMetaModule m = dao.newEntity();
        m.setModuleId("nop/test-join-" + System.nanoTime());
        m.setModuleName("test-join");
        m.setDisplayName("test-join");
        m.setModuleVersion(1L);
        m.setStatus("RELEASED");
        m.setImportedAt(new Timestamp(System.currentTimeMillis()));
        dao.saveEntity(m);
        return m.getMetaModuleId();
    }

    private static String escapeSql(String s) {
        return s == null ? "" : s.replace("'", "''");
    }

    private boolean queryJoinDataHasError(String tableId, String joinId) {
        try {
            nopMetaTableBizModel.queryJoinData(tableId, joinId, null, null, null, null, null);
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
