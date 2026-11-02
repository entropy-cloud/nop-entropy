
package io.nop.metadata.service.reconciliation;

import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.dao.entity.NopMetaReconciliationConfig;
import io.nop.metadata.dao.entity.NopMetaReconciliationEntity;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.service.reconciliation.IReconciliationProcessor.ReconciliationCandidate;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * W-08 回归钉（plan 2528 红⑥，CRA 5.5 C7-006+C5-104）：对账候选池 O(1) 复用 + 池加载显式上界。
 *
 * <p>缺陷形态（pre-fix）：{@code ReconciliationExecutor.execute} 行循环逐行
 * {@code reconcile} → 每行 {@code loadCandidates} 全池 {@code findAllByQuery} 无 LIMIT 重载同一
 * 候选池（R×P×O(L²)，C5-104 无界物化同站点）——R 行对账产生 R 次候选池查询且池查询无显式 limit。
 *
 * <p>修复后（裁定⑥）：池加载上提至执行开头一次（记录型 daoProvider 计数断言 O(1)）+
 * 池查询显式 limit（{@code nop.metadata.reconciliation.candidate-pool-limit}，缺省 1000）+
 * 超界显式失败 {@code ERR_RECON_CANDIDATE_POOL_EXCEED_LIMIT}（不静默截断——截断即静默改变
 * 匹配语义）。AR-13 DEFAULT_CANDIDATE_LIMIT=50 候选序列化有界零回退（结果行候选截断维持）。
 */
public class TestReconciliationPoolReuse_W_08 {

    private static final String TARGET_TYPE = "company";
    private static final String SPACE = "default";

    /** 记录型 dao（Mockito）：计数候选池 findAllByQuery 次数并捕获每次的 QueryBean（limit 断言载体）。 */
    static RecordingReconDaoHolder recordingDao(List<NopMetaReconciliationEntity> pool) {
        @SuppressWarnings("unchecked")
        IEntityDao<NopMetaReconciliationEntity> dao = (IEntityDao<NopMetaReconciliationEntity>) mock(IEntityDao.class);
        List<QueryBean> capturedQueries = new ArrayList<>();
        when(dao.findAllByQuery(org.mockito.ArgumentMatchers.any(QueryBean.class)))
                .thenAnswer((InvocationOnMock inv) -> {
                    capturedQueries.add(inv.getArgument(0));
                    return new ArrayList<>(pool);
                });
        return new RecordingReconDaoHolder(dao, capturedQueries);
    }

    /** 记录载体：dao + 捕获列表。 */
    static final class RecordingReconDaoHolder {
        final IEntityDao<NopMetaReconciliationEntity> dao;
        final List<QueryBean> capturedQueries;

        RecordingReconDaoHolder(IEntityDao<NopMetaReconciliationEntity> dao, List<QueryBean> capturedQueries) {
            this.dao = dao;
            this.capturedQueries = capturedQueries;
        }
    }

    private RecordingReconDaoHolder buildDaoWithPool(int poolSize) {
        List<NopMetaReconciliationEntity> pool = new ArrayList<>();
        for (int i = 0; i < poolSize; i++) {
            NopMetaReconciliationEntity entity = new NopMetaReconciliationEntity();
            entity.setEntityId("E" + i);
            entity.setEntityName("Candidate-" + i);
            entity.setEntityType(TARGET_TYPE);
            entity.setIdentifierSpace(SPACE);
            entity.setProperties("{\"k\":" + i + "}");
            pool.add(entity);
        }
        return recordingDao(pool);
    }

    private LocalReconciliationProcessor processorOver(IDaoProvider daoProvider) {
        return new LocalReconciliationProcessor(daoProvider);
    }

    private IDaoProvider providerFor(RecordingReconDaoHolder dao) {
        IDaoProvider daoProvider = mock(IDaoProvider.class);
        when(daoProvider.daoFor(NopMetaReconciliationEntity.class)).thenReturn(dao.dao);
        return daoProvider;
    }

    private NopMetaReconciliationConfig config() {
        NopMetaReconciliationConfig c = new NopMetaReconciliationConfig();
        c.setConfigId("rc-w08-test");
        c.setColumnName("NAME");
        c.setTargetEntityType(TARGET_TYPE);
        c.setIdentifierSpace(SPACE);
        c.setMatchStrategy("fuzzy");
        c.setAutoMatch((byte) 1);
        return c;
    }

    private static List<Map<String, Object>> rows(int n) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            rows.add(Collections.singletonMap("NAME", "Value-" + i));
        }
        return rows;
    }

    /** 红⑥ C7-003 主形态：R=3 行对账 → 候选池 findAllByQuery 次数 == 1（O(1)；pre-fix == 3 → red）。 */
    @Test
    public void testCandidatePoolLoadedOnceForWholeExecution() {
        RecordingReconDaoHolder dao = buildDaoWithPool(4);
        LocalReconciliationProcessor processor = processorOver(providerFor(dao));
        new ReconciliationExecutor(processor).execute(config(), rows(3));

        assertEquals(1, dao.capturedQueries.size(),
                "candidate pool must be loaded exactly once per execution (O(1) reuse, W-08)"
                        + " -- pre-fix reloads per row; found " + dao.capturedQueries.size());
    }

    /** 红⑥ C5-104 面：候选池查询必须携带显式 limit（pre-fix 全池无界 → captured limit == null → red）。 */
    @Test
    public void testPoolQueryCarriesExplicitLimit() {
        RecordingReconDaoHolder dao = buildDaoWithPool(4);
        LocalReconciliationProcessor processor = processorOver(providerFor(dao));
        new ReconciliationExecutor(processor).execute(config(), rows(2));

        // 池查询次数面由 testCandidatePoolLoadedOnceForWholeExecution 独立承载——本钉只看 limit 面
        QueryBean poolQuery = dao.capturedQueries.get(0);
        assertNotNull(poolQuery.getLimit(),
                "candidate pool query must carry an explicit limit"
                        + " (nop.metadata.reconciliation.candidate-pool-limit, C5-104 unbounded materialization)");
    }

    /** 池上界 fail-fast：池行数超 poolLimit → ERR_RECON_CANDIDATE_POOL_EXCEED_LIMIT（不静默截断）。 */
    @Test
    public void testCandidatePoolOverflowFailsFast() {
        RecordingReconDaoHolder dao = buildDaoWithPool(3);
        LocalReconciliationProcessor processor = processorOver(providerFor(dao));

        NopException ex = assertThrows(NopException.class,
                () -> processor.loadCandidatePool(TARGET_TYPE, SPACE, 2),
                "pool exceeding explicit limit must fail fast (not silently truncate)");
        assertEquals(NopMetadataErrors.ERR_RECON_CANDIDATE_POOL_EXCEED_LIMIT.getErrorCode(), ex.getErrorCode());
    }

    /** 行为等价：池上匹配与逐行路径同一判定语义（MATCHED/UNMATCHED/MULTIPLE + 候选细节）。 */
    @Test
    public void testPoolPathKeepsJudgmentSemantics() {
        RecordingReconDaoHolder dao = buildDaoWithPool(2);
        LocalReconciliationProcessor processor = processorOver(providerFor(dao));
        IReconciliationProcessor.ReconciliationCandidatePool pool =
                processor.loadCandidatePool(TARGET_TYPE, SPACE, 1000);

        List<ReconciliationCandidate> exact = processor.reconcile("candidate-0", pool, "exact", 50);
        assertEquals(1, exact.size(), "exact hit expected");
        assertEquals("E0", exact.get(0).getEntityId());
        assertEquals(1.0, exact.get(0).getScore(), 1e-9);
        assertEquals(0, exact.get(0).getProperties().get("k"),
                "properties parsed from pool cache must ride with the candidate");

        List<ReconciliationCandidate> none = processor.reconcile("no-match-xyz", pool, "exact", 50);
        assertEquals(0, none.size(), "no hit -> empty candidates (UNMATCHED path)");
    }
}
