/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.metadata.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.dao.entity.NopMetaLineageEdge;
import io.nop.metadata.dao.entity.NopMetaModule;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.service.entity.NopMetaLineageEdgeBizModel;
import io.nop.metadata.service.NopMetadataErrors;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AR-09 回归测试：lineage 图遍历内存膨胀 DoS 防护。
 *
 * <p>{@code NopMetaLineageEdgeBizModel.buildLineageGraph}/{@code buildTableNameIndex} 原用 {@code dao().findAll()}
 * 全量加载，边集/表集规模失控时直接 OOM。改为带 limit 的 {@code findAllByQuery}，size > max 时显式抛 ErrorCode。
 *
 * <p>为避免单测插入 100_001 条边（慢），通过 {@code @cfg:nop.metadata.lineage.max-edges}
 * 与 {@code nop.metadata.lineage.max-tables} 把上限降到 5，然后插入 6 条边/表触发越限。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopMetaLineageEdgeSizeLimit extends JunitBaseTestCase {

    public TestNopMetaLineageEdgeSizeLimit() {
        setTestConfig("nop.orm.init-database-schema", true);
        // AR-09：测试时把上限降到 5（避免插入 100_001 条边的成本）
        setTestConfig("nop.metadata.lineage.max-edges", 5);
        setTestConfig("nop.metadata.lineage.max-tables", 5);
    }

    @Inject
    IDaoProvider daoProvider;

    @Inject
    NopMetaLineageEdgeBizModel lineageBiz;

    /** AR-09：边数超过上限 → getUpstream 抛 ERR_LINEAGE_GRAPH_TOO_LARGE（不静默截断、不 OOM）。 */
    @Test
    public void testLineageGraphTooLargeThrowsExplicitly() {
        String moduleId = ensureModule();
        String t1 = saveTable(moduleId, "LIM_T1");
        // 插入 6 条边（max-edges=5，触发越限）。target 都指向不存在的 ID，避免被去重。
        IEntityDao<NopMetaLineageEdge> dao = daoProvider.daoFor(NopMetaLineageEdge.class);
        for (int i = 0; i < 6; i++) {
            NopMetaLineageEdge edge = dao.newEntity();
            edge.setSourceTableId("ghost_src_" + i);
            edge.setTargetTableId(t1);
            edge.setLineageSource(_NopMetadataCoreConstants.LINEAGE_SOURCE_MANUAL);
            edge.setTransformType(_NopMetadataCoreConstants.LINEAGE_TRANSFORM_DIRECT);
            dao.saveEntity(edge);
        }
        dao.flushSession();

        // getUpstream 触发 buildLineageGraph → 抛 ErrorCode
        NopException ex = assertThrows(NopException.class,
                () -> lineageBiz.getUpstream(t1, null),
                "buildLineageGraph must throw ERR_LINEAGE_GRAPH_TOO_LARGE when edge count exceeds configured limit");
        assertEquals(NopMetadataErrors.ERR_LINEAGE_GRAPH_TOO_LARGE.getErrorCode(),
                ex.getErrorCode(),
                "must throw ERR_LINEAGE_GRAPH_TOO_LARGE (not generic OOM/NPE)");
        // 失败响应包含 edges/limit 参数（运维可定位）
        assertEquals(6, ((Number) ex.getParam("edges")).intValue(),
                "edges param must be 6 (actual size)");
        assertEquals(5, ((Number) ex.getParam("limit")).intValue(),
                "limit param must be 5 (configured)");
    }

    /** AR-09：边数等于上限 → 正常返回（不抛错；6 条边时越限，5 条时正常）。 */
    @Test
    public void testLineageGraphAtLimitStillWorks() {
        String moduleId = ensureModule();
        String t1 = saveTable(moduleId, "LIM_OK_T1");
        String t2 = saveTable(moduleId, "LIM_OK_T2");
        IEntityDao<NopMetaLineageEdge> dao = daoProvider.daoFor(NopMetaLineageEdge.class);
        // 插入 5 条边（== max-edges=5，不越限）
        for (int i = 0; i < 5; i++) {
            NopMetaLineageEdge edge = dao.newEntity();
            edge.setSourceTableId(t1);
            edge.setTargetTableId(t2);
            edge.setLineageSource(_NopMetadataCoreConstants.LINEAGE_SOURCE_MANUAL);
            edge.setTransformType(_NopMetadataCoreConstants.LINEAGE_TRANSFORM_DIRECT);
            dao.saveEntity(edge);
        }
        dao.flushSession();

        // 不抛异常：getDownstream 正常执行（虽然图里只有 t1→t2 一种关联）
        List<String> downstream = lineageBiz.getDownstream(t1, null);
        assertTrue(downstream.contains(t2),
                "downstream of t1 must contain t2 when edge count is at limit: " + downstream);
    }

    private String ensureModule() {
        IEntityDao<NopMetaModule> dao = daoProvider.daoFor(NopMetaModule.class);
        NopMetaModule m = dao.newEntity();
        m.setModuleId("mod-lim-test-" + System.nanoTime());
        m.setModuleName("lim-test");
        m.setModuleVersion(1L);
        m.setStatus(_NopMetadataCoreConstants.MODULE_STATUS_RELEASED);
        m.setImportedAt(new Timestamp(System.currentTimeMillis()));
        dao.saveEntity(m);
        dao.flushSession();
        return m.getMetaModuleId();
    }

    private String saveTable(String moduleId, String tableName) {
        IEntityDao<NopMetaTable> dao = daoProvider.daoFor(NopMetaTable.class);
        NopMetaTable t = dao.newEntity();
        t.setMetaModuleId(moduleId);
        t.setTableName(tableName);
        t.setDisplayName(tableName);
        t.setTableType(_NopMetadataCoreConstants.TABLE_TYPE_EXTERNAL);
        dao.saveEntity(t);
        dao.flushSession();
        return t.getMetaTableId();
    }
}
