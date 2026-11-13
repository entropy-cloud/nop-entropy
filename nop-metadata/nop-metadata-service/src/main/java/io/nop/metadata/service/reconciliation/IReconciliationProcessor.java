
package io.nop.metadata.service.reconciliation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 可插拔对账匹配服务接口（设计 08-reconciliation.md §1.2 D1 裁定）。
 *
 * <p>首版内置 {@link LocalReconciliationProcessor}（本地候选集匹配）。接口纯接口、无外部依赖，
 * 外部 HTTP 实现（OpenRefine/Wikidata 兼容端点）可在后续作为新 impl 插入，不改执行器与 BizModel。
 *
 * <p>入参显式包含 {@code matchStrategy}，使策略可由调用方（config）控制。
 */
public interface IReconciliationProcessor {

    /**
     * 对给定值检索候选实体匹配。
     *
     * @param value           待匹配的原始值（来自表行 {@code columnName} 列）
     * @param targetType      目标实体类型（过滤候选，可空表示不限）
     * @param identifierSpace 标识符空间（过滤候选，可空表示不限）
     * @param matchStrategy   匹配策略（{@code exact} / {@code fuzzy}，对应 dict {@code meta/match-strategy}）
     * @param limit           返回候选数上限（null/<=0 表示不限）
     * @return 候选列表（按 score 降序；候选为空返回空列表，不静默伪造候选）
     */
    List<ReconciliationCandidate> reconcile(String value, String targetType, String identifierSpace,
                                            String matchStrategy, Integer limit);

    /**
     * W-08（plan 2528 裁定⑥）：批载入候选池（单次对账执行一次性加载，行间复用——消除
     * 逐行全池重载 N+1）。池为单次执行的执行期快照：执行期间并发候选变更对本轮不可见
     * （快照一致性语义，owner doc 声明）。
     *
     * @param targetType      目标实体类型（过滤候选，可空表示不限）
     * @param identifierSpace 标识符空间（过滤候选，可空表示不限）
     * @param poolLimit       候选池加载显式上界（{@code nop.metadata.reconciliation.candidate-pool-limit}，
     *                        缺省 1000）；池行数超界<b>显式失败</b>（{@code ERR_RECON_CANDIDATE_POOL_EXCEED_LIMIT}，
     *                        不静默截断——截断即静默改变匹配语义）
     * @return 候选池快照（候选实体 + 随池缓存的已解析 properties——单次执行内每候选解析一次）
     */
    ReconciliationCandidatePool loadCandidatePool(String targetType, String identifierSpace, int poolLimit);

    /**
     * W-08（裁定⑥）：在候选池快照上对给定值匹配（与逐行版
     * {@link #reconcile(String, String, String, String, Integer)} 同一打分/排序/截断语义，
     * 零重复池加载）。
     */
    List<ReconciliationCandidate> reconcile(String value, ReconciliationCandidatePool pool,
                                            String matchStrategy, Integer limit);

    /**
     * W-08（裁定⑥）：候选池快照——池实体集合 + 随池缓存的解析产物。
     */
    class ReconciliationCandidatePool {
        private final String targetType;
        private final String identifierSpace;
        private final List<PoolEntry> entries;

        public ReconciliationCandidatePool(String targetType, String identifierSpace, List<PoolEntry> entries) {
            this.targetType = targetType;
            this.identifierSpace = identifierSpace;
            this.entries = entries != null ? entries : Collections.emptyList();
        }

        public String getTargetType() {
            return targetType;
        }

        public String getIdentifierSpace() {
            return identifierSpace;
        }

        public List<PoolEntry> getEntries() {
            return entries;
        }

        public boolean isEmpty() {
            return entries.isEmpty();
        }

        /** 池条目：候选实体要素 + 已解析 properties（单次执行内解析一次）。 */
        public static class PoolEntry {
            private final String entityId;
            private final String entityName;
            private final String entityType;
            private final Map<String, Object> properties;

            public PoolEntry(String entityId, String entityName, String entityType,
                             Map<String, Object> properties) {
                this.entityId = entityId;
                this.entityName = entityName;
                this.entityType = entityType;
                this.properties = properties != null ? properties : Collections.emptyMap();
            }

            public String getEntityId() {
                return entityId;
            }

            public String getEntityName() {
                return entityName;
            }

            public String getEntityType() {
                return entityType;
            }

            public Map<String, Object> getProperties() {
                return properties;
            }
        }
    }

    /**
     * 候选实体匹配结果。{@code score} ∈ [0.0, 1.0]：exact 完全相等为 1.0，
     * fuzzy 为 levenshtein 归一化相似度。
     */
    @SuppressWarnings("java:S116")
    class ReconciliationCandidate {
        private final String entityId;
        private final String entityName;
        private final String entityType;
        private final double score;
        private final Map<String, Object> properties;

        public ReconciliationCandidate(String entityId, String entityName, String entityType, double score,
                                       Map<String, Object> properties) {
            this.entityId = entityId;
            this.entityName = entityName;
            this.entityType = entityType;
            this.score = score;
            this.properties = properties != null ? properties : Collections.emptyMap();
        }

        public String getEntityId() {
            return entityId;
        }

        public String getEntityName() {
            return entityName;
        }

        public String getEntityType() {
            return entityType;
        }

        public double getScore() {
            return score;
        }

        public Map<String, Object> getProperties() {
            return properties;
        }

        /** 序列化为 details JSON 数组元素结构（{entityId, entityName, entityType, score, properties}）。 */
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("entityId", entityId);
            m.put("entityName", entityName);
            m.put("entityType", entityType);
            m.put("score", score);
            if (!properties.isEmpty()) {
                m.put("properties", properties);
            }
            return m;
        }
    }
}
