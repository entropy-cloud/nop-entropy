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
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaModule;
import io.nop.metadata.dao.entity.NopMetaOrmModel;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.service.mock.ThrowingSearchProcessor;
import io.nop.metadata.service.search.NopMetaSearchProcessor;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * check2 P2-06（2026-08-23 审计）回归：NopMetaTable/NopMetaEntity delete 主实体 removeFromIndex
 * 无异常保护。
 *
 * <p>缺陷机制：delete 在 BizMutation 事务内直接调 {@code searchService.removeFromIndex}
 * （fail-closed 默认抛 {@code ERR_SEARCH_INDEX_REMOVE_FAILED}）——引擎瞬时故障（文件锁/磁盘满）
 * 与 delete 相交时：异常传播 → 事务回滚 → DB 行仍存在，但索引文档已被 removeDocs 删除，
 * 出现"实体存在却搜不到"的持久索引缺失。子实体清理已有 safeRemoveFromIndex 保护
 * （NopMetaEntityBizModel 字段级），主实体级遗漏。
 *
 * <p>修复：主实体清理统一走 safeRemoveFromIndex 形态（best-effort + WARN，对齐
 * NopMetaModuleBizModel 先例）。
 *
 * <p>经 {@link ThrowingSearchProcessor}（primary 测试 bean，removeFromIndex 恒抛 fail-closed
 * 异常）注入。mutate-fail：若回退为无保护直调，delete mutation 返回
 * ERR_SEARCH_INDEX_REMOVE_FAILED 错误且 DB 行回滚留存 → assertFalse(hasError)/assertNull 断言失败。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        testBeansFile = "/nop/metadata/beans/test-search-fail.beans.xml")
public class TestDeleteIndexFailureIsolation extends JunitBaseTestCase {

    public TestDeleteIndexFailureIsolation() {
        setTestConfig("nop.orm.init-database-schema", true);
    }

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    NopMetaSearchProcessor searchService;

    @BeforeEach
    void resetMock() {
        ((ThrowingSearchProcessor) searchService).reset();
    }

    /** NopMetaTable delete：索引清理失败（fail-closed 抛异常）不得回滚 DB 删除。 */
    @Test
    public void testTableDeleteSurvivesIndexRemovalFailure() {
        String tableId = seedTable("del_idx_tbl");
        GraphQLResponseBean resp = execute(
                "mutation { NopMetaTable__delete(id: \"" + tableId + "\") }");
        assertFalse(resp.hasError(),
                "delete must succeed despite index removal failure (best-effort cleanup): " + resp);
        assertNull(daoProvider.daoFor(NopMetaTable.class).getEntityById(tableId),
                "DB row must be deleted (transaction must not roll back)");
        assertTrue(((ThrowingSearchProcessor) searchService).removeAttempts >= 1,
                "index removal must have been attempted");
    }

    /** NopMetaEntity delete：主实体索引清理失败不得回滚 DB 删除（此前仅字段级有保护）。 */
    @Test
    public void testEntityDeleteSurvivesIndexRemovalFailure() {
        String entityId = seedEntity("del_idx_ent");
        GraphQLResponseBean resp = execute(
                "mutation { NopMetaEntity__delete(id: \"" + entityId + "\") }");
        assertFalse(resp.hasError(),
                "delete must succeed despite index removal failure (best-effort cleanup): " + resp);
        assertNull(daoProvider.daoFor(NopMetaEntity.class).getEntityById(entityId),
                "DB row must be deleted (transaction must not roll back)");
        assertTrue(((ThrowingSearchProcessor) searchService).removeAttempts >= 1,
                "index removal must have been attempted");
    }

    // ===== helpers =====

    private GraphQLResponseBean execute(String query) {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        IGraphQLExecutionContext context = graphQLEngine.newGraphQLContext(request);
        return graphQLEngine.executeGraphQL(context);
    }

    private String seedTable(String tableName) {
        String moduleId = ensureModule("del_idx_mod");
        IEntityDao<NopMetaTable> dao = daoProvider.daoFor(NopMetaTable.class);
        NopMetaTable t = dao.newEntity();
        t.setMetaModuleId(moduleId);
        t.setTableName(tableName);
        t.setDisplayName(tableName);
        t.setTableType("entity");
        dao.saveEntity(t);
        dao.flushSession();
        return t.getMetaTableId();
    }

    private String seedEntity(String entityName) {
        String moduleId = ensureModule("del_idx_mod");
        IEntityDao<NopMetaOrmModel> ormDao = daoProvider.daoFor(NopMetaOrmModel.class);
        NopMetaOrmModel orm = ormDao.newEntity();
        orm.setMetaModuleId(moduleId);
        orm.setModelName(entityName + "_model");
        orm.setIsDelta((byte) 0);
        ormDao.saveEntity(orm);

        IEntityDao<NopMetaEntity> dao = daoProvider.daoFor(NopMetaEntity.class);
        NopMetaEntity e = dao.newEntity();
        e.setOrmModelId(orm.getOrmModelId());
        e.setEntityName(entityName);
        e.setTableName("tbl_" + entityName);
        e.setDisplayName(entityName);
        e.setClassName("io.test." + entityName);
        dao.saveEntity(e);
        dao.flushSession();
        return e.getMetaEntityId();
    }

    private String ensureModule(String moduleName) {
        IEntityDao<NopMetaModule> dao = daoProvider.daoFor(NopMetaModule.class);
        NopMetaModule module = dao.newEntity();
        module.setModuleId("nop/" + moduleName + "_" + System.nanoTime());
        module.setModuleName(moduleName);
        module.setDisplayName(moduleName);
        module.setModuleVersion(1L);
        module.setStatus("released");
        module.setImportedAt(new Timestamp(System.currentTimeMillis()));
        dao.saveEntity(module);
        dao.flushSession();
        return module.getMetaModuleId();
    }
}
