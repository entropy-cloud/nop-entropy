/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

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
