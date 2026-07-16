package io.nop.metadata.service.contract;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.dao.entity.NopMetaCatalog;
import io.nop.metadata.dao.entity.NopMetaQualityResult;
import io.nop.metadata.dao.entity.NopMetaQualityRule;
import jakarta.inject.Inject;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 数据契约检查器（无状态，参考 {@code MetaQualityRuleExecutor} 执行器模式）。
 *
 * <p>按设计 04-data-governance.md §5.2 D2 钉死的算法执行契约检查：
 * <ol>
 *   <li>质量路径：解析 {@code qualityExpectations} → {@code qualityRuleIds} → 对每个 ruleId 校验 QualityRule 存在 +
 *       取最新 NopMetaQualityResult（executeTime desc, take 1）→ 汇总 qualitySummary。</li>
 *   <li>SLA 路径：解析 {@code sla} → 按 entityTableId 取最新 NopMetaCatalog（collectedAt desc）→
 *       refreshFrequency↔collectedAt、maxLatency↔lastModified → slaSummary（lastModified 为空记 unknown 不判定）。</li>
 *   <li>按 D2 归并规则计算 status（ERROR>FAIL>PASS，无可检查项→ERROR）。</li>
 * </ol>
 *
 * <p>失败路径显式（不吞异常、不静默 pass）：JSON 解析失败 / ruleId 不存在 / 无可检查项 均显式失败或 status=ERROR + 明确 message。
 *
 * <p>本类不自建连接，所有数据来自平台 ORM 查询（元数据目录内聚合，无需物理数据源连接）。
 */
public class MetaContractChecker {

    static final ErrorCode ERR_CONTRACT_QUALITY_EXPECTATIONS_INVALID =
            ErrorCode.define("metadata.contract-quality-expectations-invalid",
                    "Failed to parse qualityExpectations JSON for contract: {contractId} error={error}",
                    "contractId", "error");
    static final ErrorCode ERR_CONTRACT_SLA_INVALID =
            ErrorCode.define("metadata.contract-sla-invalid",
                    "Failed to parse sla JSON for contract: {contractId} error={error}",
                    "contractId", "error");

    private final IDaoProvider daoProvider;

    @Inject
    public MetaContractChecker(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    /**
     * 执行契约检查，返回结构化结果（D2 钉死结构）。
     *
     * @param contractId          契约 ID（用于错误定位）
     * @param entityTableId       契约关联数据表 ID（MetaTable.metaTableId），用于 Catalog 查询；null/空表示无关联表
     * @param qualityExpectations 质量期望 JSON 文本（形状 {"qualityRuleIds":["id",...]}）
     * @param sla                 SLA JSON 文本（约定键 refreshFrequency/maxLatency/retention）
     * @return {@code {timestamp, status, message, qualitySummary, slaSummary}}
     */
    public Map<String, Object> check(String contractId, String entityTableId,
                                     String qualityExpectations, String sla) {
        Date now = new Date();
        long nowMs = now.getTime();

        // 解析 qualityExpectations（失败→status=ERROR，不静默 pass）
        List<String> qualityRuleIds;
        try {
            qualityRuleIds = parseQualityRuleIds(qualityExpectations);
        } catch (NopException e) {
            throw e;
        } catch (Exception e) {
            throw new NopException(ERR_CONTRACT_QUALITY_EXPECTATIONS_INVALID)
                    .param("contractId", contractId).param("error", toMsg(e));
        }

        // 解析 sla（失败→status=ERROR）
        Map<String, Object> slaMap;
        try {
            slaMap = parseJsonObject(sla);
        } catch (Exception e) {
            throw new NopException(ERR_CONTRACT_SLA_INVALID)
                    .param("contractId", contractId).param("error", toMsg(e));
        }

        // 质量路径
        Map<String, Object> qualitySummary = aggregateQuality(contractId, qualityRuleIds);
        boolean qualityHasError = Boolean.TRUE.equals(qualitySummary.get("hasError"));

        // SLA 路径
        Map<String, Object> slaSummary = evaluateSla(entityTableId, slaMap, nowMs);

        // D2 归并规则
        String status;
        String message;
        boolean slaConfigured = slaMap != null && !slaMap.isEmpty();
        boolean qualityConfigured = !qualityRuleIds.isEmpty();
        if (!qualityConfigured && !slaConfigured) {
            status = "ERROR";
            message = "契约无可检查项（qualityExpectations 为空且 sla 为空）";
        } else if (qualityHasError) {
            // 任一被引用 ruleId 不存在 / 解析异常 → ERROR（已在 aggregateQuality 标记 hasError + message）
            status = "ERROR";
            message = "质量路径存在错误，详见 qualitySummary";
        } else {
            boolean slaFresh = Boolean.TRUE.equals(slaSummary.get("slaFresh"));
            long failedRules = toLong(qualitySummary.get("failedRules"));
            if (!slaFresh || failedRules > 0) {
                status = "FAIL";
                message = buildFailMessage(failedRules, slaFresh, slaSummary);
            } else {
                status = "PASS";
                message = "契约检查通过";
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timestamp", new Timestamp(nowMs));
        result.put("status", status);
        result.put("message", message);
        // qualitySummary/slaSummary 内部辅助字段（hasError）剥离，避免污染 latestResult
        result.put("qualitySummary", stripInternalKey(qualitySummary, "hasError"));
        result.put("slaSummary", slaSummary);
        return result;
    }

    // ===== 质量路径 =====

    /**
     * 解析 qualityExpectations → qualityRuleIds（D2 钉死形状）。
     * 空数组或缺 key 视为"无质量检查项"（返回空 list，不报错）。
     */
    @SuppressWarnings("unchecked")
    private List<String> parseQualityRuleIds(String qualityExpectations) {
        if (qualityExpectations == null || qualityExpectations.trim().isEmpty()) {
            return new ArrayList<>();
        }
        Map<String, Object> map = parseJsonObject(qualityExpectations);
        if (map == null) {
            return new ArrayList<>();
        }
        Object raw = map.get("qualityRuleIds");
        if (raw == null) {
            return new ArrayList<>();
        }
        if (!(raw instanceof List)) {
            throw new NopException(ERR_CONTRACT_QUALITY_EXPECTATIONS_INVALID)
                    .param("contractId", "").param("error",
                            "qualityRuleIds is not an array: " + raw.getClass().getSimpleName());
        }
        List<String> ids = new ArrayList<>();
        for (Object o : (List<Object>) raw) {
            if (o == null) {
                continue;
            }
            ids.add(String.valueOf(o));
        }
        return ids;
    }

    /** 对每个 ruleId 校验存在 + 取最新 QualityResult，汇总 qualitySummary。 */
    private Map<String, Object> aggregateQuality(String contractId, List<String> qualityRuleIds) {
        IEntityDao<NopMetaQualityRule> ruleDao = daoProvider.daoFor(NopMetaQualityRule.class);
        IEntityDao<NopMetaQualityResult> resultDao = daoProvider.daoFor(NopMetaQualityResult.class);

        int totalRules = qualityRuleIds.size();
        int passedRules = 0;
        int failedRules = 0;
        int noResultRules = 0;
        boolean hasError = false;
        List<Map<String, Object>> details = new ArrayList<>();
        List<String> errorMessages = new ArrayList<>();

        for (String ruleId : qualityRuleIds) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("ruleId", ruleId);

            // 校验 QualityRule 存在（D2：ruleId 不存在 → status=ERROR）
            NopMetaQualityRule rule = ruleDao.getEntityById(ruleId);
            if (rule == null) {
                hasError = true;
                detail.put("latestStatus", "error");
                detail.put("message", "QualityRule not found: " + ruleId);
                errorMessages.add("ruleId " + ruleId + " does not exist");
                details.add(detail);
                continue;
            }

            // 取最新 QualityResult（executeTime desc, take 1）
            NopMetaQualityResult latest = findLatestResult(resultDao, ruleId);
            if (latest == null) {
                noResultRules++;
                detail.put("latestStatus", "no-result");
                detail.put("message", "无执行结果");
            } else {
                String rs = latest.getStatus();
                detail.put("latestStatus", rs);
                detail.put("message", latest.getMessage());
                if (_PassFail.isPass(rs)) {
                    passedRules++;
                } else if (_PassFail.isFail(rs)) {
                    failedRules++;
                }
                // ERROR/SKIP 等不计入 pass/fail（保留在 latestStatus 供调用方判定）
            }
            details.add(detail);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalRules", totalRules);
        summary.put("passedRules", passedRules);
        summary.put("failedRules", failedRules);
        summary.put("noResultRules", noResultRules);
        summary.put("details", details);
        summary.put("hasError", hasError);
        if (!errorMessages.isEmpty()) {
            summary.put("errors", errorMessages);
        }
        return summary;
    }

    /** 取某 ruleId 最新一条 QualityResult（按 executeTime desc）。 */
    private NopMetaQualityResult findLatestResult(IEntityDao<NopMetaQualityResult> dao, String qualityRuleId) {
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaQualityResult.PROP_NAME_qualityRuleId, qualityRuleId));
        q.addOrderField(NopMetaQualityResult.PROP_NAME_executeTime, true);
        return dao.findFirstByQuery(q);
    }

    // ===== SLA 路径 =====

    /**
     * SLA 新鲜度评估（D2 钉死算法）。
     * refreshFrequency↔collectedAt、maxLatency↔lastModified，时间归一为毫秒。
     */
    private Map<String, Object> evaluateSla(String entityTableId, Map<String, Object> slaMap, long nowMs) {
        Map<String, Object> summary = new LinkedHashMap<>();
        if (slaMap == null || slaMap.isEmpty()) {
            // 无 SLA 配置 → slaFresh=true（不影响归并）
            summary.put("catalogAvailable", false);
            summary.put("collectionStale", false);
            summary.put("dataStale", false);
            summary.put("slaFresh", true);
            summary.put("note", "no sla configured");
            return summary;
        }

        // 取最新 Catalog（entityTableId → metaTableId → collectedAt desc）
        NopMetaCatalog latest = findLatestCatalog(entityTableId);
        boolean catalogAvailable = latest != null;
        summary.put("catalogAvailable", catalogAvailable);

        boolean collectionStale = false;
        boolean dataStale = false;

        if (catalogAvailable) {
            summary.put("collectedAt", latest.getCollectedAt());
            summary.put("lastModified", latest.getLastModified());

            // refreshFrequency ↔ collectedAt
            Long refreshMs = toDurationMillis(slaMap.get("refreshFrequency"), "interval", "unit");
            if (refreshMs != null) {
                long collectedMs = latest.getCollectedAt() != null ? latest.getCollectedAt().getTime() : 0L;
                collectionStale = (nowMs - collectedMs) > refreshMs;
            }

            // maxLatency ↔ lastModified（lastModified 为空记 unknown 不判定，v1 恒走此分支）
            Long maxLatencyMs = toDurationMillis(slaMap.get("maxLatency"), "value", "unit");
            if (maxLatencyMs != null) {
                if (latest.getLastModified() == null) {
                    summary.put("maxLatencyStatus", "unknown");
                    // 不判定（dataStale 保持 false，不计入失败）
                } else {
                    long lastModMs = latest.getLastModified().getTime();
                    dataStale = (nowMs - lastModMs) > maxLatencyMs;
                }
            }
        }

        boolean slaFresh = !collectionStale && !dataStale;
        summary.put("collectionStale", collectionStale);
        summary.put("dataStale", dataStale);
        summary.put("slaFresh", slaFresh);
        return summary;
    }

    /** 取某 metaTableId 最新一条 NopMetaCatalog（按 collectedAt desc）。 */
    private NopMetaCatalog findLatestCatalog(String metaTableId) {
        if (metaTableId == null || metaTableId.isEmpty()) {
            return null;
        }
        IEntityDao<NopMetaCatalog> dao = daoProvider.daoFor(NopMetaCatalog.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaCatalog.PROP_NAME_metaTableId, metaTableId));
        q.addOrderField(NopMetaCatalog.PROP_NAME_collectedAt, true);
        return dao.findFirstByQuery(q);
    }

    /**
     * 把 SLA 中的 {interval/value, unit} 结构归一为毫秒。
     *
     * @param durationMap refreshFrequency({interval,unit}) 或 maxLatency({value,unit})
     * @param amountKey   量值键名（refreshFrequency 用 "interval"，maxLatency 用 "value"）
     * @param unitKey     单位键名（统一 "unit"）
     */
    @SuppressWarnings("unchecked")
    private Long toDurationMillis(Object durationMap, String amountKey, String unitKey) {
        if (durationMap == null) {
            return null;
        }
        Map<String, Object> m;
        if (durationMap instanceof Map) {
            m = (Map<String, Object>) durationMap;
        } else {
            return null;
        }
        Object amountObj = m.get(amountKey);
        Object unitObj = m.get(unitKey);
        if (amountObj == null || unitObj == null) {
            return null;
        }
        double amount;
        try {
            amount = ((Number) amountObj).doubleValue();
        } catch (ClassCastException e) {
            amount = Double.parseDouble(String.valueOf(amountObj));
        }
        String unit = String.valueOf(unitObj).toLowerCase();
        TimeUnit tu;
        switch (unit) {
            case "millisecond":
            case "ms":
                tu = TimeUnit.MILLISECONDS;
                break;
            case "second":
            case "sec":
            case "s":
                tu = TimeUnit.SECONDS;
                break;
            case "minute":
            case "min":
            case "m":
                tu = TimeUnit.MINUTES;
                break;
            case "hour":
            case "h":
            case "hr":
                tu = TimeUnit.HOURS;
                break;
            case "day":
            case "d":
                tu = TimeUnit.DAYS;
                break;
            default:
                tu = TimeUnit.MILLISECONDS;
                break;
        }
        return tu.toMillis((long) amount);
    }

    // ===== helpers =====

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonObject(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        Object parsed = JsonTool.parse(json);
        if (parsed == null) {
            return null;
        }
        if (parsed instanceof Map) {
            return (Map<String, Object>) parsed;
        }
        throw new NopException(ERR_CONTRACT_SLA_INVALID)
                .param("contractId", "").param("error",
                        "expected JSON object but got " + parsed.getClass().getSimpleName());
    }

    private static Map<String, Object> stripInternalKey(Map<String, Object> map, String key) {
        Map<String, Object> copy = new LinkedHashMap<>(map);
        copy.remove(key);
        return copy;
    }

    private static String buildFailMessage(long failedRules, boolean slaFresh, Map<String, Object> slaSummary) {
        StringBuilder sb = new StringBuilder();
        if (failedRules > 0) {
            sb.append("质量失败规则数=").append(failedRules);
        }
        if (!slaFresh) {
            if (sb.length() > 0) {
                sb.append("；");
            }
            sb.append("SLA 不满足（");
            if (Boolean.TRUE.equals(slaSummary.get("collectionStale"))) {
                sb.append("采集过期");
            }
            if (Boolean.TRUE.equals(slaSummary.get("dataStale"))) {
                if (sb.charAt(sb.length() - 1) != '（') {
                    sb.append(',');
                }
                sb.append("数据过期");
            }
            if (!slaSummary.containsKey("collectionStale") && !slaSummary.containsKey("dataStale")
                    && Boolean.FALSE.equals(slaSummary.get("catalogAvailable"))) {
                sb.append("无 Catalog 记录");
            }
            sb.append('）');
        }
        return sb.toString();
    }

    private static long toLong(Object o) {
        if (o == null) {
            return 0L;
        }
        if (o instanceof Number) {
            return ((Number) o).longValue();
        }
        return Long.parseLong(String.valueOf(o));
    }

    private static String toMsg(Throwable e) {
        String m = e.getMessage();
        return m != null ? m : e.getClass().getName();
    }

    /** 质量结果状态判定辅助（对齐 dict meta/quality-result-status 大写值）。 */
    private static final class _PassFail {
        static boolean isPass(String status) {
            return "PASS".equals(status);
        }

        static boolean isFail(String status) {
            return "FAIL".equals(status);
        }
    }
}
