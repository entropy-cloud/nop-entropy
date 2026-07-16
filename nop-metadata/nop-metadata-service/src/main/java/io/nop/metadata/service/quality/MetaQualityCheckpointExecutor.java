package io.nop.metadata.service.quality;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmTemplate;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.dao.entity.NopMetaDataSource;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.dao.entity.NopMetaQualityCheckpoint;
import io.nop.metadata.dao.entity.NopMetaQualityResult;
import io.nop.metadata.dao.entity.NopMetaQualityRule;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.service.tableref.MetaTableReferenceResolver;
import io.nop.metadata.service.tableref.TableReference;
import io.nop.metadata.service.tableref.TableReferenceExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 质量检查点执行器（架构基线 §2.7.3 D3）。无状态编排器：按 checkpoint 配置解析规则集（D2），逐条**复用既有
 * §2.7.1 单规则执行路径**（resolve 目标表 → {@link MetaTableReferenceResolver} → {@link TableReferenceExecutor}
 * → {@link MetaQualityRuleExecutor#judge} → {@link QualityResultWriter} 写 NopMetaQualityResult），per-rule
 * try/catch + flushSession/clearSession 失败隔离（对齐 {@code executeQualityRulesForDataSource} 模式）。
 *
 * <p>本类**不自建连接**（连接由 {@link TableReferenceExecutor} 按 ref 形态分派）、**不重写判定逻辑**
 * （judge 算法在 §2.7.1 D3，本层仅调用）、**不复制结果写入**（共用 {@link QualityResultWriter}）。
 *
 * <p>失败/不可执行路径均显式（不静默跳过、不伪造、不静默空返回）：
 * <ul>
 *   <li>checkpoint status 非 ACTIVE（PAUSED/DISABLED）→ 抛 {@link #ERR_CHECKPOINT_NOT_ACTIVE}（D5）</li>
 *   <li>配置了 store 之外的动作类型且 enabled=true → 抛 {@link #ERR_CHECKPOINT_ACTION_NOT_SUPPORTED}（D4）</li>
 *   <li>解析后规则集为空 → 抛 {@link #ERR_CHECKPOINT_NO_RULES}（D2，不静默空集）</li>
 *   <li>引用的 ruleId/tableId 不存在 → 记入 errors 不中断（D2 per-item 隔离）</li>
 *   <li>单规则执行抛异常 → 记入 errors、clearSession、不中断后续规则（D3 失败隔离）</li>
 *   <li>entityType=database 规则 → 写 SKIP 结果行（不剔除，与 §2.7.1 D1 单规则语义一致）</li>
 * </ul>
 */
public class MetaQualityCheckpointExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(MetaQualityCheckpointExecutor.class);

    static final ErrorCode ERR_CHECKPOINT_NOT_ACTIVE =
            ErrorCode.define("metadata.checkpoint-not-active",
                    "Quality checkpoint is not ACTIVE (paused/disabled), cannot execute: "
                            + "{checkpointId} status={status}", "checkpointId", "status");
    static final ErrorCode ERR_CHECKPOINT_NO_RULES =
            ErrorCode.define("metadata.checkpoint-no-rules",
                    "Quality checkpoint resolved to an empty rule set (no explicit ruleIds and no rules mounted "
                            + "on tableIds), nothing to execute: {checkpointId}", "checkpointId");
    static final ErrorCode ERR_CHECKPOINT_ACTION_NOT_SUPPORTED =
            ErrorCode.define("metadata.checkpoint-action-not-supported",
                    "Quality checkpoint action type is not supported in first version (only store): "
                            + "{checkpointId} actionType={actionType}", "checkpointId", "actionType");
    static final ErrorCode ERR_CHECKPOINT_RULE_TARGET_TABLE_NOT_FOUND =
            ErrorCode.define("metadata.checkpoint-rule-target-table-not-found",
                    "Quality rule in checkpoint target table not found (entityId does not refer to an existing "
                            + "NopMetaTable): {checkpointId} qualityRuleId={qualityRuleId} entityId={entityId}",
                    "checkpointId", "qualityRuleId", "entityId");

    private final MetaQualityRuleExecutor ruleExecutor;
    private final MetaTableReferenceResolver tableRefResolver;
    private final TableReferenceExecutor tableRefExecutor;
    private final QualityResultWriter resultWriter;
    private final IDaoProvider daoProvider;
    private final IOrmTemplate orm;

    public MetaQualityCheckpointExecutor(MetaQualityRuleExecutor ruleExecutor,
                                         MetaTableReferenceResolver tableRefResolver,
                                         TableReferenceExecutor tableRefExecutor,
                                         QualityResultWriter resultWriter,
                                         IDaoProvider daoProvider,
                                         IOrmTemplate orm) {
        this.ruleExecutor = ruleExecutor;
        this.tableRefResolver = tableRefResolver;
        this.tableRefExecutor = tableRefExecutor;
        this.resultWriter = resultWriter;
        this.daoProvider = daoProvider;
        this.orm = orm;
    }

    /**
     * 执行检查点（架构基线 §2.7.3 D2-D5）。
     *
     * @param cp            检查点（非 null，已由 BizModel 加载）
     * @param schemaPattern 可选 schema 限定（null/空串表示依赖连接默认 schema）
     * @return 执行摘要 {@code {checkpointId, executedCount, passCount, failCount, errorCount, results:[...], errors:[...]}}
     */
    public Map<String, Object> execute(NopMetaQualityCheckpoint cp, String schemaPattern) {
        // D5：状态门禁——非 ACTIVE 显式失败（不静默跳过）
        if (!_NopMetadataCoreConstants.CHECKPOINT_STATUS_ACTIVE.equals(cp.getStatus())) {
            throw new NopException(ERR_CHECKPOINT_NOT_ACTIVE)
                    .param("checkpointId", cp.getCheckpointId())
                    .param("status", String.valueOf(cp.getStatus()));
        }

        // D4：动作校验——store 之外的动作类型且 enabled 显式失败（不静默跳过）
        validateActionsOrThrow(cp);

        // D2：规则集解析（显式 ruleIds ∪ tableIds 下挂载规则，去重；missing ref 记入 errors）
        ResolutionResult resolution = resolveRules(cp);
        if (resolution.rules.isEmpty()) {
            // 解析后规则集为空 → 显式失败（不静默空集、不伪造零计数）
            throw new NopException(ERR_CHECKPOINT_NO_RULES)
                    .param("checkpointId", cp.getCheckpointId());
        }

        IEntityDao<NopMetaQualityResult> resultDao = daoProvider.daoFor(NopMetaQualityResult.class);

        int passCount = 0;
        int failCount = 0;
        int errorCount = 0;
        int skipCount = 0;
        List<Map<String, Object>> results = new ArrayList<>();
        List<Map<String, Object>> errors = new ArrayList<>(resolution.errors);

        for (NopMetaQualityRule rule : resolution.rules) {
            try {
                QualityRuleJudgment judgment = executeSingleRule(cp, rule, schemaPattern);
                resultWriter.append(resultDao, rule.getQualityRuleId(), judgment);
                orm.flushSession();

                String status = judgment.getStatus();
                if (_NopMetadataCoreConstants.QUALITY_RESULT_STATUS_PASS.equals(status)) {
                    passCount++;
                } else if (_NopMetadataCoreConstants.QUALITY_RESULT_STATUS_FAIL.equals(status)) {
                    failCount++;
                } else if (_NopMetadataCoreConstants.QUALITY_RESULT_STATUS_ERROR.equals(status)) {
                    errorCount++;
                } else {
                    // SKIP（entityType=database / 方言不支持等）——写结果行审计，但不计入 pass/fail/error
                    skipCount++;
                }
                results.add(buildResultEntry(rule, judgment));
            } catch (Exception e) {
                LOG.error("checkpoint execute failed for rule: {}", rule.getQualityRuleId(), e);
                errors.add(buildExecutionErrorEntry(rule, e));
                // 失败隔离：清理未刷出的脏实体，不影响已 flush 的规则与后续规则
                orm.clearSession();
            }
        }

        int executedCount = passCount + failCount + errorCount + skipCount;
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("checkpointId", cp.getCheckpointId());
        summary.put("executedCount", executedCount);
        summary.put("passCount", passCount);
        summary.put("failCount", failCount);
        summary.put("errorCount", errorCount);
        summary.put("results", results);
        summary.put("errors", errors);
        return summary;
    }

    // ============================================================
    // D2：规则集解析
    // ============================================================

    /** 解析结果（去重后的规则集 + 解析期收集的错误，如 missing ruleId/tableId）。 */
    private static class ResolutionResult {
        final List<NopMetaQualityRule> rules;
        final List<Map<String, Object>> errors;

        ResolutionResult(List<NopMetaQualityRule> rules, List<Map<String, Object>> errors) {
            this.rules = rules;
            this.errors = errors;
        }
    }

    /**
     * 规则集解析（D2）：∪（每组 validations 的显式 ruleIds）∪（tableIds 下挂载的
     * {@code NopMetaQualityRule where entityId ∈ tableIds}）；去重。
     * ruleId 不存在 / tableId 不存在 → 记入 errors 不中断（per-item 隔离）。
     */
    @SuppressWarnings("unchecked")
    private ResolutionResult resolveRules(NopMetaQualityCheckpoint cp) {
        IEntityDao<NopMetaQualityRule> ruleDao = daoProvider.daoFor(NopMetaQualityRule.class);
        IEntityDao<NopMetaTable> tableDao = daoProvider.daoFor(NopMetaTable.class);

        List<Map<String, Object>> errors = new ArrayList<>();
        Set<String> explicitRuleIds = new LinkedHashSet<>();
        Set<String> tableIds = new LinkedHashSet<>();

        // 解析 validations JSON：[{ruleIds:[...], tableIds:[...]}]
        List<Map<String, Object>> validations = parseValidations(cp.getValidations());
        for (Map<String, Object> v : validations) {
            explicitRuleIds.addAll(asStringList(v.get("ruleIds")));
            tableIds.addAll(asStringList(v.get("tableIds")));
        }

        // 显式 ruleIds：逐条加载，missing 记入 errors（per-item 隔离，不中断）
        List<NopMetaQualityRule> rules = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String rid : explicitRuleIds) {
            NopMetaQualityRule rule = ruleDao.getEntityById(rid);
            if (rule == null) {
                errors.add(buildResolutionErrorEntry("ruleId", rid, "quality rule not found"));
                continue;
            }
            if (seen.add(rid)) {
                rules.add(rule);
            }
        }

        // tableIds：校验存在性（missing 记入 errors），存在的表加载其上挂载的规则（entityId ∈ tableIds，去重）
        if (!tableIds.isEmpty()) {
            Set<String> existingTableIds = new LinkedHashSet<>();
            for (String tid : tableIds) {
                NopMetaTable table = tableDao.getEntityById(tid);
                if (table == null) {
                    errors.add(buildResolutionErrorEntry("tableId", tid, "meta table not found"));
                } else {
                    existingTableIds.add(tid);
                }
            }
            if (!existingTableIds.isEmpty()) {
                QueryBean q = new QueryBean();
                q.addFilter(FilterBeans.in(NopMetaQualityRule.PROP_NAME_entityId, new ArrayList<>(existingTableIds)));
                List<NopMetaQualityRule> mounted = ruleDao.findAllByQuery(q);
                for (NopMetaQualityRule r : mounted) {
                    if (seen.add(r.getQualityRuleId())) {
                        rules.add(r);
                    }
                }
            }
        }

        return new ResolutionResult(rules, errors);
    }

    // ============================================================
    // D3：单规则执行（复用既有 §2.7.1 路径）
    // ============================================================

    /** 复用单规则执行路径：database→SKIP；其余→resolve 表→table-ref 分派→judge。 */
    private QualityRuleJudgment executeSingleRule(NopMetaQualityCheckpoint cp, NopMetaQualityRule rule,
                                                  String schemaPattern) {
        // entityType=database 首版 SKIP（§2.7.1 D1）——不剔除，写 SKIP 结果行与单规则一致
        if (_NopMetadataCoreConstants.QUALITY_ENTITY_TYPE_DATABASE.equals(rule.getEntityType())) {
            QualityRuleJudgment skip = new QualityRuleJudgment();
            skip.setStatus(_NopMetadataCoreConstants.QUALITY_RESULT_STATUS_SKIP);
            skip.setMessage("entityType=database not supported in first version (external-table-only execution)");
            skip.getDetails().put("reason", "database-not-supported-first-version");
            skip.getDetails().put("ruleType", rule.getRuleType());
            skip.getDetails().put("entityType", rule.getEntityType());
            return skip;
        }

        // 解析目标表（任意 tableType）+ table-reference
        IEntityDao<NopMetaTable> tableDao = daoProvider.daoFor(NopMetaTable.class);
        NopMetaTable table = tableDao.getEntityById(rule.getEntityId());
        if (table == null) {
            throw new NopException(ERR_CHECKPOINT_RULE_TARGET_TABLE_NOT_FOUND)
                    .param("checkpointId", cp.getCheckpointId())
                    .param("qualityRuleId", rule.getQualityRuleId())
                    .param("entityId", rule.getEntityId());
        }
        TableReference ref = tableRefResolver.resolve(table,
                daoProvider.daoFor(NopMetaDataSource.class),
                daoProvider.daoFor(NopMetaEntity.class),
                daoProvider.daoFor(NopMetaEntityField.class),
                orm);

        // 按 ref 形态分派 Connection 获取并执行（复用 §2.7.1 judge 算法，本层不重写判定）
        return tableRefExecutor.execute(ref,
                (conn, metaData, productName) -> ruleExecutor.judge(conn, ref, schemaPattern,
                        rule.getRuleType(), rule.getEntityType(),
                        rule.getParams(), rule.getSqlExpression(),
                        rule.getThreshold(), productName));
    }

    // ============================================================
    // D4：动作校验
    // ============================================================

    /** actions 为空/null/[] 视为合法（store-only 默认）；存在 store 之外且 enabled 的动作 → 显式失败。 */
    @SuppressWarnings("unchecked")
    private void validateActionsOrThrow(NopMetaQualityCheckpoint cp) {
        String actionsJson = cp.getActions();
        if (actionsJson == null || actionsJson.trim().isEmpty()) {
            return;
        }
        Object parsed;
        try {
            parsed = JsonTool.parse(actionsJson);
        } catch (Exception e) {
            // actions 存在但不可解析为 JSON —— 配置错误，回退为 store-only 默认（不静默伪造动作执行）
            LOG.warn("checkpoint {} actions is not valid JSON, falling back to store-only",
                    cp.getCheckpointId(), e);
            return;
        }
        if (!(parsed instanceof List)) {
            return;
        }
        for (Object o : (List<Object>) parsed) {
            if (!(o instanceof Map)) {
                continue;
            }
            Map<String, Object> action = (Map<String, Object>) o;
            String actionType = String.valueOf(action.get("actionType"));
            boolean enabled = !Boolean.FALSE.equals(action.get("enabled"));
            if (enabled && !_NopMetadataCoreConstants.CHECKPOINT_ACTION_TYPE_STORE.equals(actionType)) {
                throw new NopException(ERR_CHECKPOINT_ACTION_NOT_SUPPORTED)
                        .param("checkpointId", cp.getCheckpointId())
                        .param("actionType", actionType);
            }
        }
    }

    // ============================================================
    // helpers
    // ============================================================

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> parseValidations(String validationsJson) {
        if (validationsJson == null || validationsJson.trim().isEmpty()) {
            return java.util.Collections.emptyList();
        }
        Object parsed;
        try {
            parsed = JsonTool.parse(validationsJson);
        } catch (Exception e) {
            return java.util.Collections.emptyList();
        }
        if (!(parsed instanceof List)) {
            return java.util.Collections.emptyList();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object o : (List<Object>) parsed) {
            if (o instanceof Map) {
                result.add((Map<String, Object>) o);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static List<String> asStringList(Object value) {
        if (value == null) {
            return java.util.Collections.emptyList();
        }
        if (value instanceof List) {
            List<String> result = new ArrayList<>();
            for (Object o : (List<Object>) value) {
                if (o != null) {
                    result.add(String.valueOf(o));
                }
            }
            return result;
        }
        return java.util.Collections.emptyList();
    }

    private static Map<String, Object> buildResultEntry(NopMetaQualityRule rule, QualityRuleJudgment j) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("qualityRuleId", rule.getQualityRuleId());
        m.put("ruleName", rule.getRuleName());
        m.put("status", j.getStatus());
        m.put("actualValue", j.getActualValue());
        m.put("expectedValue", j.getExpectedValue());
        m.put("message", j.getMessage());
        return m;
    }

    private static Map<String, Object> buildExecutionErrorEntry(NopMetaQualityRule rule, Exception e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("source", "execution");
        m.put("qualityRuleId", rule.getQualityRuleId());
        m.put("ruleName", rule.getRuleName());
        m.put("error", toErrorMessage(e));
        return m;
    }

    private static Map<String, Object> buildResolutionErrorEntry(String refType, String refValue, String error) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("source", "resolution");
        m.put("refType", refType);
        m.put("refValue", refValue);
        m.put("error", error);
        return m;
    }

    private static String toErrorMessage(Exception e) {
        String msg = e.getMessage();
        return msg != null ? msg : e.getClass().getName();
    }
}
