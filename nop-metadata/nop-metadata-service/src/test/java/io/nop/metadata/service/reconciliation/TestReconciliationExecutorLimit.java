package io.nop.metadata.service.reconciliation;

import io.nop.core.lang.json.JsonTool;
import io.nop.metadata.dao.entity.NopMetaReconciliationConfig;
import io.nop.metadata.dao.entity.NopMetaReconciliationResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AR-13：{@link ReconciliationExecutor#execute} 候选序列化有界验证。
 *
 * <p>修复前 {@code ReconciliationExecutor.execute} 硬编码 limit 为 null，导致所有 fuzzy 候选（score > 阈值）
 * 被全量序列化进 details JSON。修复后传入默认 limit=50（{@link ReconciliationExecutor#DEFAULT_CANDIDATE_LIMIT}），
 * 候选序列化有界。
 *
 * <p>使用 mock {@link IReconciliationProcessor} 验证（不依赖 ORM session / IoC）。
 */
public class TestReconciliationExecutorLimit {

    /**
     * mock processor：记录传入的 limit 参数，并按指定数量返回候选。
     * 用于验证 ReconciliationExecutor 传入了非 null limit，以及候选数有界。
     */
    static class RecordingProcessor implements IReconciliationProcessor {
        Integer lastLimit;
        int candidateCount;

        RecordingProcessor(int candidateCount) {
            this.candidateCount = candidateCount;
        }

        @Override
        public List<ReconciliationCandidate> reconcile(String value, String targetType, String identifierSpace,
                                                        String matchStrategy, Integer limit) {
            this.lastLimit = limit;
            List<ReconciliationCandidate> result = new ArrayList<>();
            for (int i = 0; i < candidateCount; i++) {
                result.add(new ReconciliationCandidate(
                        "Q" + i, "Entity" + i, "company", 0.5, Collections.emptyMap()));
            }
            // 模拟 LocalReconciliationProcessor 的 limit 截断逻辑
            if (limit != null && limit > 0 && result.size() > limit) {
                return new ArrayList<>(result.subList(0, limit));
            }
            return result;
        }
    }

    private static NopMetaReconciliationConfig config(String matchStrategy, boolean autoMatch, Double threshold) {
        NopMetaReconciliationConfig c = new NopMetaReconciliationConfig();
        c.setConfigId("rc-limit-test");
        c.setColumnName("NAME");
        c.setMatchStrategy(matchStrategy);
        c.setAutoMatch(autoMatch ? (byte) 1 : (byte) 0);
        if (threshold != null) {
            c.setAutoMatchThreshold(threshold);
        }
        return c;
    }

    private static Map<String, Object> row(String name) {
        return Collections.singletonMap("NAME", name);
    }

    /**
     * limit 参数被真实消费（非 null 静默忽略）—— ReconciliationExecutor 传入 50 而非 null。
     */
    @Test
    public void defaultLimitPassedToProcessor() {
        RecordingProcessor processor = new RecordingProcessor(0);
        ReconciliationExecutor executor = new ReconciliationExecutor(processor);

        NopMetaReconciliationConfig cfg = config("fuzzy", true, 0.8);
        executor.execute(cfg, Collections.singletonList(row("test")));

        assertNotNull(processor.lastLimit, "limit must not be null (AR-13: was null before fix)");
        assertEquals(ReconciliationExecutor.DEFAULT_CANDIDATE_LIMIT, processor.lastLimit,
                "limit must be DEFAULT_CANDIDATE_LIMIT (50)");
    }

    /**
     * 大候选池下 details 候选数 ≤ limit（有界序列化）。
     * 模拟 100 个候选（score=0.5），limit=50 截断后 details 中每行 candidates ≤ 50。
     */
    @Test
    public void largeCandidatePoolBoundedByLimit() {
        // 100 候选（autoMatch=false → status=MULTIPLE，候选全序列化进 details）
        RecordingProcessor processor = new RecordingProcessor(100);
        ReconciliationExecutor executor = new ReconciliationExecutor(processor);

        NopMetaReconciliationConfig cfg = config("fuzzy", false, 0.0);
        NopMetaReconciliationResult result = executor.execute(cfg, Collections.singletonList(row("test")));

        assertNotNull(result.getDetails());
        @SuppressWarnings("unchecked")
        List<Object> details = (List<Object>) JsonTool.parse(result.getDetails());
        assertEquals(1, details.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> row0 = (Map<String, Object>) details.get(0);
        @SuppressWarnings("unchecked")
        List<Object> candidates = (List<Object>) row0.get("candidates");
        assertTrue(candidates.size() <= ReconciliationExecutor.DEFAULT_CANDIDATE_LIMIT,
                "candidates in details must be bounded by DEFAULT_CANDIDATE_LIMIT (50), got: " + candidates.size());
        assertEquals(ReconciliationExecutor.DEFAULT_CANDIDATE_LIMIT, candidates.size(),
                "exactly 50 candidates after limit truncation (100 input, limit=50)");
    }

    /**
     * 小候选池不受影响（候选数 < limit 时不截断）。
     */
    @Test
    public void smallCandidatePoolNotTruncated() {
        RecordingProcessor processor = new RecordingProcessor(3);
        ReconciliationExecutor executor = new ReconciliationExecutor(processor);

        NopMetaReconciliationConfig cfg = config("fuzzy", false, 0.0);
        NopMetaReconciliationResult result = executor.execute(cfg, Collections.singletonList(row("test")));

        @SuppressWarnings("unchecked")
        List<Object> details = (List<Object>) JsonTool.parse(result.getDetails());
        @SuppressWarnings("unchecked")
        Map<String, Object> row0 = (Map<String, Object>) details.get(0);
        @SuppressWarnings("unchecked")
        List<Object> candidates = (List<Object>) row0.get("candidates");
        assertEquals(3, candidates.size(), "small candidate pool (3) must not be truncated to less than 3");
    }
}
