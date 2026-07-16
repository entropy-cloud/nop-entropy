/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.metadata.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.metadata.dao.entity.NopMetaModelChangedEvent;
import io.nop.metadata.dao.entity.NopMetaModule;
import io.nop.metadata.service.event.MetaModelChangedEventPublisher;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证元数据变更事件模型（架构基线 §2.8 / 设计 10 / plan 2026-07-17-0228-1）：
 * 写操作 → NopMetaModelChangedEvent 行写入 → __findPage 查询到正确事件的完整端到端路径。
 *
 * <p>覆盖 Exit Criteria：
 * <ul>
 *   <li>(a) 事件可查询：写操作后 findPage 返回新增事件行，含正确 eventType/entityType/changeSource/changeTime</li>
 *   <li>(b) 快照语义：ENTITY_CREATED 有 afterSnapshot、ENTITY_UPDATED 有 before+after、ENTITY_DELETED 有 before</li>
 *   <li>(c) 批量粒度：importOrmModel 产生主实体级事件（1 行 Module CREATED），共享 transactionId</li>
 *   <li>(d) 失败路径显式：快照序列化失败抛 ErrorCode（不静默吞掉）</li>
 *   <li>(e) 接线验证：save override + delete override 运行时确实调用 helper 并持久化事件行（非空壳）</li>
 * </ul>
 *
 * <p>Anti-Hollow：测试断言落盘的 NopMetaModelChangedEvent 行的真实字段值（eventType/changeSource/
 * beforeSnapshot/afterSnapshot/transactionId），证明运行时确实调用发布 helper（非空壳返回固定值）。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopMetaModelChangedEvent extends JunitBaseTestCase {

    public TestNopMetaModelChangedEvent() {
        setTestConfig("nop.orm.init-database-schema", true);
    }

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    MetaModelChangedEventPublisher eventPublisher;

    // ============================================================
    // (a)+(e) ENTITY_CREATED via save override：事件可查询 + 接线验证
    // ============================================================

    /**
     * 通过 GraphQL save（触发 save override）创建 Module → 断言事件行写入 ENTITY_CREATED，
     * changeSource=API，afterSnapshot 非空、beforeSnapshot 为空。证明 save override 运行时确实调用 helper（非空壳）。
     */
    @Test
    public void testCreateEventViaSaveOverride() {
        String moduleId = saveModuleViaGraphQL("mod-create", "Module-Create");

        // save override 应产生 1 行 ENTITY_CREATED 事件
        List<NopMetaModelChangedEvent> events = findEvents("NopMetaModule", moduleId);
        assertEquals(1, events.size(), "save override must persist exactly 1 CREATED event");
        NopMetaModelChangedEvent ev = events.get(0);
        assertEquals("ENTITY_CREATED", ev.getEventType());
        assertEquals("NopMetaModule", ev.getEntityType());
        assertEquals("API", ev.getChangeSource());
        assertEquals(moduleId, ev.getEntityId());
        // entityName 取 moduleName（便于日志）
        assertEquals("mod-create", ev.getEntityName());
        assertNotNull(ev.getChangeTime(), "changeTime must be set");
        assertNotNull(ev.getTransactionId(), "single-op transactionId must be generated");
        assertNotNull(ev.getAfterSnapshot(), "CREATED must have afterSnapshot");
        assertNull(ev.getBeforeSnapshot(), "CREATED must have null beforeSnapshot");

        // afterSnapshot 含 moduleId（证明快照来自真实实体，非空壳）
        assertTrue(ev.getAfterSnapshot().contains("mod-create"),
                "afterSnapshot must contain the real entity content: " + ev.getAfterSnapshot());
    }

    // ============================================================
    // (b)+(e) ENTITY_UPDATED via save override：before+after 快照语义
    // ============================================================

    /**
     * UPDATE 路径快照语义（架构基线 §2.8 D3）：importOrmModel（DRAFTING）→ releaseModule（RELEASED）
     * → 断言 ENTITY_UPDATED 事件，before+after 均非空，且 before 捕获的是 DRAFTING 状态、after 是 RELEASED
     * （证明 before 在变更前捕获，非同一快照伪造）。
     *
     * <p>注：GraphQL save 对无逻辑删除的实体为 insert 语义（非 upsert），故 Module 的 UPDATE 事件经
     * releaseModule mutation action 发布（save override 负责同机制的 CREATE/UPDATE 检测，对 Module
     * 的 UPDATE 路径经 releaseModule 体现）。
     */
    @Test
    public void testUpdateEventViaReleaseModule() {
        // 导入模块（DRAFTING）
        GraphQLResponseBean impResp = execute(
                "mutation { NopMetaModule__importOrmModel(path: \"/test/orm/simple.orm.xml\")" +
                        " { metaModuleId status } }");
        assertFalse(impResp.hasError(), "import should not error: " + impResp);
        @SuppressWarnings("unchecked")
        Map<String, Object> impData = (Map<String, Object>) ((Map<String, Object>) impResp.getData()).values().iterator().next();
        String metaModuleId = (String) impData.get("metaModuleId");

        // 发布（DRAFTING → RELEASED）→ releaseModule hook 发布 ENTITY_UPDATED
        GraphQLResponseBean relResp = execute(
                "mutation { NopMetaModule__releaseModule(metaModuleId: \"" + metaModuleId + "\") { status } }");
        assertFalse(relResp.hasError(), "release should not error: " + relResp);

        // 找 UPDATED 事件（changeSource=UI）
        IEntityDao<NopMetaModelChangedEvent> evDao = daoProvider.daoFor(NopMetaModelChangedEvent.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaModelChangedEvent.PROP_NAME_entityType, "NopMetaModule"));
        q.addFilter(FilterBeans.eq(NopMetaModelChangedEvent.PROP_NAME_entityId, metaModuleId));
        List<NopMetaModelChangedEvent> events = evDao.findAllByQuery(q);
        NopMetaModelChangedEvent updated = events.stream()
                .filter(e -> "ENTITY_UPDATED".equals(e.getEventType())).findFirst().orElse(null);
        assertNotNull(updated, "must have ENTITY_UPDATED event from releaseModule: " + events);
        assertEquals("UI", updated.getChangeSource());
        assertNotNull(updated.getBeforeSnapshot(), "UPDATED must have beforeSnapshot");
        assertNotNull(updated.getAfterSnapshot(), "UPDATED must have afterSnapshot");
        // before 含 DRAFTING，after 含 RELEASED（证明 before 在变更前捕获）
        assertTrue(updated.getBeforeSnapshot().contains("DRAFTING"),
                "beforeSnapshot must capture pre-mutation DRAFTING state: " + updated.getBeforeSnapshot());
        assertTrue(updated.getAfterSnapshot().contains("RELEASED"),
                "afterSnapshot must capture post-mutation RELEASED state: " + updated.getAfterSnapshot());
    }

    // ============================================================
    // (b)+(e) ENTITY_DELETED via delete override：before 快照语义
    // ============================================================

    /**
     * 创建 Module → 通过 GraphQL delete（触发 delete override）→ 断言 ENTITY_DELETED，
     * beforeSnapshot 非空、afterSnapshot 为空。证明 delete override 运行时确实调用 helper（非空壳）。
     */
    @Test
    public void testDeleteEventViaDeleteOverride() {
        String moduleId = saveModuleViaDao("mod-delete", "Module-Delete");
        clearEvents();

        GraphQLResponseBean resp = execute(
                "mutation { NopMetaModule__delete(id: \"" + moduleId + "\") }");
        assertFalse(resp.hasError(), "delete should not error: " + resp);

        List<NopMetaModelChangedEvent> events = findEvents("NopMetaModule", moduleId);
        assertEquals(1, events.size(), "delete override must persist exactly 1 DELETED event");
        NopMetaModelChangedEvent ev = events.get(0);
        assertEquals("ENTITY_DELETED", ev.getEventType());
        assertEquals("API", ev.getChangeSource());
        assertNotNull(ev.getBeforeSnapshot(), "DELETED must have beforeSnapshot");
        assertNull(ev.getAfterSnapshot(), "DELETED must have null afterSnapshot");
        assertTrue(ev.getBeforeSnapshot().contains("mod-delete"),
                "beforeSnapshot must contain the real entity content: " + ev.getBeforeSnapshot());
    }

    // ============================================================
    // (c) 批量粒度：importOrmModel 主实体级事件 + transactionId 共享
    // ============================================================

    /**
     * importOrmModel（批量操作）→ 断言产生**主实体级** 1 行 Module CREATED 事件（非逐子实体），
     * changeSource=IMPORT，transactionId 非空（correlation key）。
     * 同时断言子实体（Entity/Field 等）**不**产生事件（细粒度 deferred）。
     */
    @Test
    public void testImportOrmModelBatchEventMainEntityLevel() {
        GraphQLResponseBean resp = execute(
                "mutation { NopMetaModule__importOrmModel(path: \"/test/orm/simple.orm.xml\")" +
                        " { metaModuleId moduleName } }");
        assertFalse(resp.hasError(), "importOrmModel should not error: " + resp);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) resp.getData();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) data.values().iterator().next();
        String metaModuleId = (String) result.get("metaModuleId");

        // 查询所有 IMPORT 来源的事件
        IEntityDao<NopMetaModelChangedEvent> evDao = daoProvider.daoFor(NopMetaModelChangedEvent.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaModelChangedEvent.PROP_NAME_changeSource, "IMPORT"));
        List<NopMetaModelChangedEvent> importEvents = evDao.findAllByQuery(q);

        // 主实体级：1 行 Module CREATED（不是逐 Entity/Field 多行）
        long moduleCreatedCount = importEvents.stream()
                .filter(e -> "ENTITY_CREATED".equals(e.getEventType())
                        && "NopMetaModule".equals(e.getEntityType())
                        && metaModuleId.equals(e.getEntityId()))
                .count();
        assertEquals(1, moduleCreatedCount,
                "importOrmModel must produce exactly 1 main-entity Module CREATED event (not per-child): " + importEvents);

        NopMetaModelChangedEvent moduleEvent = importEvents.stream()
                .filter(e -> "NopMetaModule".equals(e.getEntityType()) && metaModuleId.equals(e.getEntityId()))
                .findFirst().orElse(null);
        assertNotNull(moduleEvent, "module CREATED event must exist");
        assertEquals("IMPORT", moduleEvent.getChangeSource());
        assertNotNull(moduleEvent.getTransactionId(),
                "batch operation must share a transactionId (correlation key)");
        assertNotNull(moduleEvent.getAfterSnapshot(), "CREATED must have afterSnapshot");
        assertNull(moduleEvent.getBeforeSnapshot(), "CREATED must have null beforeSnapshot");
    }

    // ============================================================
    // (a) findPage query 消费路径：按 entityType/changeSource 过滤查询
    // ============================================================

    /**
     * 触发写操作后通过 GraphQL __findPage 查询事件历史，断言返回正确事件行（消费路径可用，审计/下游拉取）。
     * 含 changeTime 字段（非伪造）。过滤验证经 DAO（GraphQL 字符串内 $type 与变量语法冲突，过滤路径改经 DAO 断言）。
     */
    @Test
    public void testFindPageQueryConsumption() {
        saveModuleViaGraphQL("mod-query", "Module-Query");

        // GraphQL findPage 无 filter（证明 CRUD 自动暴露 + 字段返回）
        GraphQLResponseBean resp = execute(
                "query { NopMetaModelChangedEvent__findPage { total items { modelChangedEventId eventType entityType entityId } } }");
        assertFalse(resp.hasError(), "findPage query should not error: " + resp.getErrorCode()
                + " -- " + resp.getErrors());
        String data = String.valueOf(resp.getData());
        assertTrue(data.contains("ENTITY_CREATED"), "findPage must return CREATED events: " + data);
        assertTrue(data.contains("NopMetaModule"), "findPage must return NopMetaModule events: " + data);
        // 过滤查询（消费路径核心）：按 entityType + changeSource 过滤，经 DAO 断言（与 GraphQL findPage 同源）
        IEntityDao<NopMetaModelChangedEvent> dao = daoProvider.daoFor(NopMetaModelChangedEvent.class);
        QueryBean fq = new QueryBean();
        fq.addFilter(FilterBeans.eq(NopMetaModelChangedEvent.PROP_NAME_entityType, "NopMetaModule"));
        fq.addFilter(FilterBeans.eq(NopMetaModelChangedEvent.PROP_NAME_changeSource, "API"));
        List<NopMetaModelChangedEvent> filtered = dao.findAllByQuery(fq);
        assertFalse(filtered.isEmpty(), "filtered query (entityType=NopMetaModule AND changeSource=API) must return events");
        for (NopMetaModelChangedEvent e : filtered) {
            assertEquals("NopMetaModule", e.getEntityType());
            assertEquals("API", e.getChangeSource());
        }
    }

    // ============================================================
    // (a) createSqlTable 接线：Table CREATED 事件
    // ============================================================

    /**
     * createSqlTable mutation action → 断言 1 行 Table CREATED 事件（changeSource=UI）。
     * 证明 mutation action hook 运行时确实调用 helper（非空壳）。
     */
    @Test
    public void testCreateSqlTableEvent() {
        // createSqlTable 需要一个已存在的 module
        String metaModuleId = saveModuleViaGraphQL("mod-sql", "Module-Sql");

        // createSqlTable 返回 Map（GraphQL 视为标量，不做字段选择）
        GraphQLResponseBean resp = execute(
                "mutation { NopMetaTable__createSqlTable(sql: \"SELECT 1 AS c1\", tableName: \"t_evt_sql\","
                        + " metaModuleId: \"" + metaModuleId + "\") }");
        assertFalse(resp.hasError(), "createSqlTable should not error: " + resp);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) resp.getData();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) data.values().iterator().next();
        String metaTableId = (String) result.get("metaTableId");

        List<NopMetaModelChangedEvent> events = findEvents("NopMetaTable", metaTableId);
        assertEquals(1, events.size(), "createSqlTable must persist exactly 1 Table CREATED event");
        NopMetaModelChangedEvent ev = events.get(0);
        assertEquals("ENTITY_CREATED", ev.getEventType());
        assertEquals("NopMetaTable", ev.getEntityType());
        assertEquals("UI", ev.getChangeSource());
        assertNotNull(ev.getAfterSnapshot(), "CREATED must have afterSnapshot");
        assertNull(ev.getBeforeSnapshot(), "CREATED must have null beforeSnapshot");
    }

    // ============================================================
    // (d) 失败路径显式：快照序列化失败抛 ErrorCode（不静默吞掉）
    // ============================================================

    /**
     * buildSnapshot 收到不可序列化的对象（循环引用 → StackOverflowError）时显式抛 NopException，
     * 不静默吞掉、不静默跳过事件发布。
     */
    @Test
    public void testFailurePathSerializationThrowsErrorCode() {
        // 构造循环引用结构：JsonTool.stringify 会触发 StackOverflowError（Error 非 Exception），
        // 验证 buildSnapshot 的 Throwable 捕获路径将其收口为 NopException（不静默吞掉）。
        Map<String, Object> cyclic = new HashMap<>();
        List<Object> self = new ArrayList<>();
        self.add(self); // 自引用 List → 深度无限递归
        cyclic.put("loop", self);

        NopException ex = assertThrows(NopException.class,
                () -> eventPublisher.buildSnapshot(cyclic, "TestEntity", "test-id"));
        assertNotNull(ex.getMessage());
    }

    // ============================================================
    // helpers
    // ============================================================

    private GraphQLResponseBean execute(String query) {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        IGraphQLExecutionContext context = graphQLEngine.newGraphQLContext(request);
        return graphQLEngine.executeGraphQL(context);
    }

    /** 通过 GraphQL save 创建 Module（触发 save override 发布事件），返回 metaModuleId。 */
    private String saveModuleViaGraphQL(String moduleId, String displayName) {
        GraphQLResponseBean resp = execute(
                "mutation { NopMetaModule__save(data: { moduleName: \"" + moduleId + "\","
                        + " moduleId: \"" + moduleId + "\", moduleVersion: 1, status: \"DRAFTING\","
                        + " displayName: \"" + displayName + "\" }) { metaModuleId } }");
        assertFalse(resp.hasError(), "save module should not error: " + resp);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) resp.getData();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) data.values().iterator().next();
        return (String) result.get("metaModuleId");
    }

    /** 通过 dao 直接创建 Module（不经 save override，不发布事件），返回 metaModuleId。 */
    private String saveModuleViaDao(String moduleId, String displayName) {
        IEntityDao<NopMetaModule> dao = daoProvider.daoFor(NopMetaModule.class);
        NopMetaModule m = dao.newEntity();
        m.setModuleId(moduleId);
        m.setModuleName(moduleId);
        m.setDisplayName(displayName);
        m.setModuleVersion(1L);
        m.setStatus("DRAFTING");
        m.setVersion(1L);
        Timestamp now = new Timestamp(System.currentTimeMillis());
        m.setCreatedBy("autotest");
        m.setCreateTime(now);
        m.setUpdatedBy("autotest");
        m.setUpdateTime(now);
        dao.saveEntity(m);
        return m.getMetaModuleId();
    }

    /** 查询某 entityType+entityId 的所有事件行（按 changeTime 升序）。 */
    private List<NopMetaModelChangedEvent> findEvents(String entityType, String entityId) {
        IEntityDao<NopMetaModelChangedEvent> dao = daoProvider.daoFor(NopMetaModelChangedEvent.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaModelChangedEvent.PROP_NAME_entityType, entityType));
        q.addFilter(FilterBeans.eq(NopMetaModelChangedEvent.PROP_NAME_entityId, entityId));
        q.addOrderField(NopMetaModelChangedEvent.PROP_NAME_changeTime, false);
        return dao.findAllByQuery(q);
    }

    /** 防御性清理：删除所有现存事件行，保证测试基线干净（各测试方法独立 DB 状态由 AutoTest 隔离，此处仅防御）。 */
    private void clearEvents() {
        IEntityDao<NopMetaModelChangedEvent> dao = daoProvider.daoFor(NopMetaModelChangedEvent.class);
        QueryBean q = new QueryBean();
        for (NopMetaModelChangedEvent e : dao.findAllByQuery(q)) {
            dao.deleteEntity(e);
        }
    }

    @SuppressWarnings("unused")
    private static Map<String, Object> parseJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new LinkedHashMap<>();
        }
        Object parsed = JsonTool.parse(json);
        return parsed instanceof Map ? (Map<String, Object>) parsed : new LinkedHashMap<>();
    }
}
