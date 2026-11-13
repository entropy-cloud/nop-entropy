package io.nop.code.service.graph;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.code.dao.entity.NopCodeGraphMetric;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2532 组A 红②（6.3-W-01 findByMetric 上界面，TP-001 上界面承载）：共享 finder 一处封口的
 * 行数上限 + orderBy 下推语义锚。
 *
 * <p>驱动器形态（设计裁定④）：直接构造 GraphMetricStore（TestGraphMetricMaterialization 先例）
 * + DAO 播种 &gt; 注入上限的 metric 行——rank 序 family 用 loadEntryPoints 驱动（断言返回行数
 * ≤ 上限且首行为最小 rankNo，orderBy 下推保 top-rank 锚）；HUB 用 loadHubs 驱动（断言 top-degree
 * 行存活——score 即 totalDegree，评审 R1 Major-1 保序锚）；COMMUNITY membership 面用
 * loadCommunities 驱动（断言行数 ≤ 上限，缺失型降级锚）。
 *
 * <p>红证形态（设计裁定④）：pre-fix 编译期构造红——注入 setter（{@code setMaxRowsPerFamily}）
 * 不存在；post-fix 语义红 = 返回行数超上限、top-rank/top-degree 行丢失。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.FALSE)
public class TestGraphMetricFinderCap_W_01 extends JunitAutoTestCase {

    @Inject
    IDaoProvider daoProvider;

    private String unique(String prefix) {
        // hex (no sign char, <= 16 chars) keeps id/indexId within the 36-char codeId domain
        return prefix + Long.toHexString(System.nanoTime());
    }

    private NopCodeGraphMetric newRow(IEntityDao<NopCodeGraphMetric> dao, String indexId,
                                      String metricType, String symbolId) {
        NopCodeGraphMetric row = dao.newEntity();
        row.setId(indexId + "-" + symbolId);
        row.setIndexId(indexId);
        row.setMetricType(metricType);
        row.setSymbolId(symbolId);
        return row;
    }

    /** rankNo/shuffled：symbol-i 携带 shuffled[i] 的 rankNo——DB 返回序不确定，orderBy 下推语义锚。 */
    private void seedEntryPoints(String indexId, int[] shuffled) {
        IEntityDao<NopCodeGraphMetric> dao = daoProvider.daoFor(NopCodeGraphMetric.class);
        for (int i = 0; i < shuffled.length; i++) {
            NopCodeGraphMetric row = newRow(dao, indexId, GraphMetricStore.METRIC_ENTRY_POINT, "sym-ep-" + i);
            row.setScore((double) (shuffled.length - i));
            row.setRankNo(shuffled[i]);
            dao.saveEntity(row);
        }
        dao.flushSession();
    }

    /** HUB 行 score 即 totalDegree（materializer 写入形态同型）。 */
    private void seedHubs(String indexId, int[] totalDegrees) {
        IEntityDao<NopCodeGraphMetric> dao = daoProvider.daoFor(NopCodeGraphMetric.class);
        for (int i = 0; i < totalDegrees.length; i++) {
            NopCodeGraphMetric row = newRow(dao, indexId, GraphMetricStore.METRIC_HUB, "sym-hub-" + i);
            row.setScore((double) totalDegrees[i]);
            dao.saveEntity(row);
        }
        dao.flushSession();
    }

    private void seedCommunities(String indexId, int count) {
        IEntityDao<NopCodeGraphMetric> dao = daoProvider.daoFor(NopCodeGraphMetric.class);
        for (int i = 0; i < count; i++) {
            NopCodeGraphMetric row = newRow(dao, indexId, GraphMetricStore.METRIC_COMMUNITY, "sym-comm-" + i);
            row.setCommunityId(i % 2);
            dao.saveEntity(row);
        }
        dao.flushSession();
    }

    @Test
    void testW_01_entryPointCapKeepsTopRankRowsInRankOrder() {
        String indexId = unique("fce");
        seedEntryPoints(indexId, new int[]{5, 3, 1, 4, 2});

        GraphMetricStore store = new GraphMetricStore(daoProvider);
        store.setMaxRowsPerFamily(3);

        List<NopCodeGraphMetric> rows = store.loadEntryPoints(indexId);
        assertEquals(3, rows.size(), "rows must respect the injected cap");
        List<Integer> rankNos = rows.stream()
                .map(NopCodeGraphMetric::getRankNo)
                .collect(Collectors.toList());
        assertEquals(List.of(1, 2, 3), rankNos,
                "top-rank rows must survive truncation in ascending rank order (orderBy pushdown)");
    }

    @Test
    void testW_01_hubCapKeepsTopDegreeRows() {
        String indexId = unique("fch");
        seedHubs(indexId, new int[]{1, 5, 2, 4, 3});

        GraphMetricStore store = new GraphMetricStore(daoProvider);
        store.setMaxRowsPerFamily(3);

        Map<String, int[]> hubs = store.loadHubs(indexId);
        assertEquals(3, hubs.size(), "hub rows must respect the injected cap");
        assertTrue(hubs.containsKey("sym-hub-1"), "top-degree symbol (score=5) must survive truncation");
        assertTrue(hubs.containsKey("sym-hub-3"), "second-degree symbol (score=4) must survive truncation");
        assertTrue(hubs.containsKey("sym-hub-4"), "third-degree symbol (score=3) must survive truncation");
        assertFalse(hubs.containsKey("sym-hub-0"), "lowest-degree symbol (score=1) must be truncated");
        assertFalse(hubs.containsKey("sym-hub-2"), "second-lowest-degree symbol (score=2) must be truncated");
    }

    @Test
    void testW_01_communityCapTruncatesMembership() {
        String indexId = unique("fcc");
        seedCommunities(indexId, 5);

        GraphMetricStore store = new GraphMetricStore(daoProvider);
        store.setMaxRowsPerFamily(3);

        Map<String, Integer> communities = store.loadCommunities(indexId);
        assertEquals(3, communities.size(),
                "membership rows must respect the injected cap (missing-type degradation)");
    }

    @Test
    void testW_01_belowCapFindersReturnAllRows() {
        String indexId = unique("fcb");
        seedCommunities(indexId, 2);

        GraphMetricStore store = new GraphMetricStore(daoProvider);
        assertEquals(2, store.loadCommunities(indexId).size(),
                "no truncation below the default cap");
    }
}
