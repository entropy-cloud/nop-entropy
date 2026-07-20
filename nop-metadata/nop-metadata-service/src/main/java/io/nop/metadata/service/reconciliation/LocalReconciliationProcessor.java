/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.metadata.service.reconciliation;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.dao.entity.NopMetaReconciliationEntity;
import io.nop.metadata.service.NopMetadataErrors;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 本地对账匹配服务（设计 08-reconciliation.md §1.2 D1 + §四 D2 裁定）。
 *
 * <p>按 {@code entityType}+{@code identifierSpace} 从 {@link NopMetaReconciliationEntity}（候选实体缓存）
 * 检索候选集，按 {@code matchStrategy} 计算 score：
 * <ul>
 *   <li><b>exact</b>（{@link _NopMetadataCoreConstants#MATCH_STRATEGY_EXACT}）：完全相等（忽略大小写）→ score=1.0，否则 0。</li>
 *   <li><b>fuzzy</b>（{@link _NopMetadataCoreConstants#MATCH_STRATEGY_FUZZY}）：levenshtein 归一化相似度（忽略大小写）。</li>
 * </ul>
 *
 * <p>候选经 score 降序排序后取 limit。候选为空 → 返回空列表（不静默伪造候选）。
 * 未知 matchStrategy → 显式失败抛 ErrorCode（不静默跳过、不伪造 score）。
 *
 * <p>DAO 访问经 NopIoC {@link IDaoProvider} 注入（对齐 {@code MetaContractChecker} 服务 bean 模式）。
 */
public class LocalReconciliationProcessor implements IReconciliationProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(LocalReconciliationProcessor.class);


    /** score 排序：降序（高分在前），同名次稳定排序保持候选集原始顺序。 */
    private static final Comparator<ReconciliationCandidate> SCORE_DESC =
            Comparator.comparingDouble(ReconciliationCandidate::getScore).reversed();

    /** exact/fuzzy 命中下限：fuzzy 相似度低于此值不视为候选，避免无意义噪声候选。 */
    private static final double FUZZY_MIN_SCORE = 0.0001;

    private final IDaoProvider daoProvider;

    @Inject
    public LocalReconciliationProcessor(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    @Override
    public List<ReconciliationCandidate> reconcile(String value, String targetType, String identifierSpace,
                                                   String matchStrategy, Integer limit) {
        if (value == null || value.isEmpty()) {
            // 空值无可对账内容 → 返回空候选（非异常，由执行器判为 UNMATCHED）
            return Collections.emptyList();
        }
        List<NopMetaReconciliationEntity> pool = loadCandidates(targetType, identifierSpace);
        if (pool.isEmpty()) {
            // 候选池为空 → 返回空列表（不静默伪造候选）
            return Collections.emptyList();
        }

        List<ReconciliationCandidate> matched = new ArrayList<>();
        for (NopMetaReconciliationEntity entity : pool) {
            double score = score(value, entity.getEntityName(), matchStrategy);
            if (score > 0.0) {
                matched.add(new ReconciliationCandidate(
                        entity.getEntityId(),
                        entity.getEntityName(),
                        entity.getEntityType(),
                        score,
                        parseProperties(entity.getProperties())));
            }
        }
        matched.sort(SCORE_DESC);

        if (limit != null && limit > 0 && matched.size() > limit) {
            return new ArrayList<>(matched.subList(0, limit));
        }
        return matched;
    }

    /** 按 targetType + identifierSpace 加载候选实体（任一为空则该维度不限）。 */
    private List<NopMetaReconciliationEntity> loadCandidates(String targetType, String identifierSpace) {
        IEntityDao<NopMetaReconciliationEntity> dao = daoProvider.daoFor(NopMetaReconciliationEntity.class);
        QueryBean q = new QueryBean();
        if (targetType != null && !targetType.isEmpty()) {
            q.addFilter(FilterBeans.eq(NopMetaReconciliationEntity.PROP_NAME_entityType, targetType));
        }
        if (identifierSpace != null && !identifierSpace.isEmpty()) {
            q.addFilter(FilterBeans.eq(NopMetaReconciliationEntity.PROP_NAME_identifierSpace, identifierSpace));
        }
        return dao.findAllByQuery(q);
    }

    /** 按策略计算 score。未知策略 → 显式失败。 */
    private double score(String value, String candidateName, String matchStrategy) {
        if (candidateName == null || candidateName.isEmpty()) {
            return 0.0;
        }
        if (_NopMetadataCoreConstants.MATCH_STRATEGY_EXACT.equals(matchStrategy)) {
            return value.equalsIgnoreCase(candidateName) ? 1.0 : 0.0;
        }
        if (_NopMetadataCoreConstants.MATCH_STRATEGY_FUZZY.equals(matchStrategy)) {
            double sim = levenshteinSimilarity(value, candidateName);
            return sim > FUZZY_MIN_SCORE ? round(sim) : 0.0;
        }
        throw new NopException(NopMetadataErrors.ERR_RECON_UNSUPPORTED_MATCH_STRATEGY).param("matchStrategy", String.valueOf(matchStrategy));
    }

    /** levenshtein 归一化相似度 = 1 - distance / max(len)。忽略大小写。 */
    static double levenshteinSimilarity(String a, String b) {
        String s1 = a.toLowerCase();
        String s2 = b.toLowerCase();
        int n = s1.length();
        int m = s2.length();
        if (n == 0 && m == 0) {
            return 1.0;
        }
        int maxLen = Math.max(n, m);
        if (maxLen == 0) {
            return 1.0;
        }
        int dist = levenshteinDistance(s1, s2);
        return 1.0 - ((double) dist / maxLen);
    }

    /** 经典 DP levenshtein 编辑距离。 */
    private static int levenshteinDistance(String s1, String s2) {
        int n = s1.length();
        int m = s2.length();
        if (n == 0) {
            return m;
        }
        if (m == 0) {
            return n;
        }
        int[] prev = new int[m + 1];
        int[] curr = new int[m + 1];
        for (int j = 0; j <= m; j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= n; i++) {
            curr[0] = i;
            char c1 = s1.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                int cost = (c1 == s2.charAt(j - 1)) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[m];
    }

    /** 保留 4 位小数，避免浮点精度噪声导致阈值边界判定抖动。 */
    private static double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    @SuppressWarnings("unchecked")
    private static java.util.Map<String, Object> parseProperties(String propertiesJson) {
        if (propertiesJson == null || propertiesJson.trim().isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            Object parsed = JsonTool.parse(propertiesJson);
            if (parsed instanceof java.util.Map) {
                return (java.util.Map<String, Object>) parsed;
            }
        } catch (Exception e) {
            // plan 2026-07-19-1250-3 Phase 2 维度09-09：静默吞异常修复——记录 warn 日志（含原始 JSON 摘要 + 完整 stack trace）
            LOG.warn("parseProperties failed -- propertiesJsonSnippet={}",
                    propertiesJson.length() > 200 ? propertiesJson.substring(0, 200) + "..." : propertiesJson,
                    e);
        }
        return Collections.emptyMap();
    }
}
