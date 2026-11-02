
package io.nop.metadata.service.reconciliation;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.json.JsonTool;
import io.nop.metadata.dao.entity.NopMetaReconciliationConfig;
import io.nop.metadata.dao.entity.NopMetaReconciliationResult;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.service.NopMetadataReconciliationConfigs;
import io.nop.metadata.service.NopMetadataException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 对账执行器（纯组件，设计 08-reconciliation.md §3.3 行为契约）。
 *
 * <p>入参 {@code rows} 由 BizModel action 调 {@code queryData} 取得的 {@code items} 传入。
 * 本执行器不持有 BizModel、不伪造 context、不复制取数逻辑。
 *
 * <p>逐行按 {@code config.columnName} 取值 → 调 {@link IReconciliationProcessor} 取候选 →
 * 按 D5 钉死规则（设计 §3.2）判 status → 汇总 statistics + details → 返回未持久化的 Result 实体
 * （由 BizModel 落库）。
 *
 * <p>判定规则（status 单一事实源）：
 * <ul>
 *   <li>候选为空 → UNMATCHED。</li>
 *   <li>autoMatch=false 时，有候选一律 → MULTIPLE（交人工）。</li>
 *   <li>恰 1 候选且 score≥阈值 → MATCHED（selectedId=该候选）。</li>
 *   <li>恰 1 候选且 score<阈值 → MULTIPLE。</li>
 *   <li>候选≥2 → MULTIPLE（最高分候选仍列出）。</li>
 * </ul>
 *
 * <p>失败路径显式：行缺失 {@code columnName} 键、候选行数据异常均抛 ErrorCode（不静默跳过）。
 * 空候选→UNMATCHED 体现在结果（非整体异常、不静默 pass）。
 */
public class ReconciliationExecutor {

    /** status 常量（对齐 dict meta/reconciliation-status）。 */
    static final String STATUS_MATCHED = "MATCHED";
    static final String STATUS_UNMATCHED = "UNMATCHED";
    static final String STATUS_MULTIPLE = "MULTIPLE";
    static final String STATUS_MANUAL = "MANUAL";

    /**
     * AR-13：reconciliation 候选序列化默认上限。硬编码（非 config-driven，避免 ORM Protected Area 变更）。
     * 经 ReconciliationProcessor.reconcile 传递给 LocalReconciliationProcessor，候选序列化进 details JSON 有界，
     * 大候选池下不会 OOM/多秒序列化。config-driven limit 移入 Non-Blocking Follow-ups。
     */
    static final int DEFAULT_CANDIDATE_LIMIT = 50;


    private final IReconciliationProcessor reconciliationService;

    public ReconciliationExecutor(IReconciliationProcessor reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    /**
     * 执行对账，返回未持久化的 {@link NopMetaReconciliationResult}（含 statistics + details，由 BizModel 落库）。
     *
     * @param config 对账配置（提供 columnName/matchStrategy/targetEntityType/identifierSpace/autoMatch/threshold）
     * @param rows   表行列表（由 BizModel 调 queryData 取得的 items，每行为列名→值 Map）
     * @return 未持久化的 Result 实体（configId/metaEntityId/executeTime/statistics/details 已填充）
     */
    public NopMetaReconciliationResult execute(NopMetaReconciliationConfig config,
                                               List<Map<String, Object>> rows) {
        String columnName = config.getColumnName();
        int total = rows != null ? rows.size() : 0;
        int matched = 0;
        int unmatched = 0;
        int multiple = 0;

        List<Map<String, Object>> details = new ArrayList<>(total);

        // W-08（plan 2528 裁定⑥）：候选池执行开头一次载入，行间复用同一池快照——
        // 消除逐行全池重载 N+1（R×P×O(L²) → O(P)+R×O(P·L) 打分）；池为单次执行的执行期快照
        // （执行期间并发候选变更对本轮不可见——快照一致性语义，owner doc 声明）。
        IReconciliationProcessor.ReconciliationCandidatePool candidatePool =
                reconciliationService.loadCandidatePool(config.getTargetEntityType(),
                        config.getIdentifierSpace(), resolveCandidatePoolLimit());

        for (int rowIndex = 0; rowIndex < total; rowIndex++) {
            Map<String, Object> row = rows.get(rowIndex);
            Object raw = row.get(columnName);
            if (!row.containsKey(columnName)) {
                // 行缺失配置的列名键 → 显式失败（不静默跳过该行）
                throw new NopMetadataException(NopMetadataErrors.ERR_RECON_ROW_MISSING_COLUMN)
                        .param("configId", config.getConfigId())
                        .param("columnName", columnName)
                        .param("rowIndex", rowIndex);
            }
            String value = raw == null ? null : String.valueOf(raw);

            // AR-13：传入默认 limit=50（非 null），候选序列化有界（零回退——结果行候选截断维持）。
            List<IReconciliationProcessor.ReconciliationCandidate> candidates =
                    reconciliationService.reconcile(value, candidatePool,
                            config.getMatchStrategy(), DEFAULT_CANDIDATE_LIMIT);

            String status = judgeStatus(candidates, config);
            switch (status) {
                case STATUS_MATCHED:
                    matched++;
                    break;
                case STATUS_UNMATCHED:
                    unmatched++;
                    break;
                case STATUS_MULTIPLE:
                    multiple++;
                    break;
                default:
                    // judgeStatus 只返回上述三态之一，防御性校验
                    throw new NopMetadataException(NopMetadataErrors.ERR_RECON_UNKNOWN_STATUS)
                            .param("status", status);
            }

            details.add(toRowDetail(rowIndex, value, status, candidates));
        }

        Map<String, Object> statistics = buildStatistics(total, matched, unmatched, multiple);

        NopMetaReconciliationResult result = new NopMetaReconciliationResult();
        result.setConfigId(config.getConfigId());
        result.setMetaEntityId(config.getMetaEntityId());
        result.setStatistics(JsonTool.stringify(statistics));
        result.setDetails(JsonTool.stringify(details));
        return result;
    }

    /**
     * W-08（裁定⑥）：候选池加载上界解析——{@code nop.metadata.reconciliation.candidate-pool-limit}
     * 缺省 1000；非正值显式拒绝（无界加载即 C5-104 缺陷本体，不提供关闭档）。
     * 包内可覆写（测试缝）。
     */
    protected int resolveCandidatePoolLimit() {
        Integer v = NopMetadataReconciliationConfigs.CFG_RECON_CANDIDATE_POOL_LIMIT.get();
        if (v == null) {
            return DEFAULT_CANDIDATE_POOL_LIMIT;
        }
        if (v <= 0) {
            throw new NopMetadataException(NopMetadataErrors.ERR_RECON_CANDIDATE_POOL_EXCEED_LIMIT)
                    .param(NopMetadataErrors.ARG_POOL_LIMIT, v)
                    .param(NopMetadataErrors.ARG_CONFIG_KEY, "nop.metadata.reconciliation.candidate-pool-limit");
        }
        return v;
    }

    /** W-08（裁定⑥）：候选池加载上界缺省值（池查询显式 limit——C5-104 无界全池闭合）。 */
    public static final int DEFAULT_CANDIDATE_POOL_LIMIT = 1000;

    /** D5 钉死规则：见类 Javadoc。 */
    private String judgeStatus(List<IReconciliationProcessor.ReconciliationCandidate> candidates,
                               NopMetaReconciliationConfig config) {
        if (candidates == null || candidates.isEmpty()) {
            return STATUS_UNMATCHED;
        }
        // autoMatch=false 时，有候选一律交人工
        if (!isAutoMatch(config)) {
            return STATUS_MULTIPLE;
        }
        Double threshold = config.getAutoMatchThreshold();
        double effectiveThreshold = threshold != null ? threshold : 1.0;
        if (candidates.size() == 1) {
            if (candidates.get(0).getScore() >= effectiveThreshold) {
                return STATUS_MATCHED;
            }
            return STATUS_MULTIPLE;
        }
        // 候选≥2 → MULTIPLE
        return STATUS_MULTIPLE;
    }

    private static boolean isAutoMatch(NopMetaReconciliationConfig config) {
        Byte v = config.getAutoMatch();
        return v != null && v != 0;
    }

    private Map<String, Object> toRowDetail(int rowIndex, String originalValue, String status,
                                            List<IReconciliationProcessor.ReconciliationCandidate> candidates) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("rowIndex", rowIndex);
        d.put("originalValue", originalValue);
        d.put("status", status);
        List<Map<String, Object>> candidateMaps = new ArrayList<>();
        String selectedId = null;
        if (candidates != null) {
            for (IReconciliationProcessor.ReconciliationCandidate c : candidates) {
                candidateMaps.add(c.toMap());
            }
            // MATCHED 时自动选中最高分候选（candidates 已按 score 降序）
            if (STATUS_MATCHED.equals(status) && !candidates.isEmpty()) {
                selectedId = candidates.get(0).getEntityId();
            }
        }
        d.put("candidates", candidateMaps);
        if (selectedId != null) {
            d.put("selectedId", selectedId);
        }
        return d;
    }

    private static Map<String, Object> buildStatistics(int total, int matched, int unmatched, int multiple) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("totalRows", total);
        s.put("matchedRows", matched);
        s.put("unmatchedRows", unmatched);
        s.put("multipleMatches", multiple);
        double rate = total > 0 ? Math.round(((double) matched / total) * 10000.0) / 10000.0 : 0.0;
        s.put("matchRate", rate);
        return s;
    }
}
