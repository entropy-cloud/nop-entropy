package io.nop.datav.service.alert;

import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.datav.biz.PanelDataResult;
import io.nop.datav.dao.entity.NopDatavAlertRule;
import io.nop.datav.dao.entity.NopDatavAlertState;
import io.nop.datav.dao.entity.NopDatavPanel;
import io.nop.datav.service.NopDatavConfigs;
import io.nop.datav.service.query.PanelDataBinder;
import io.nop.datav.service.report.NotificationSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.datav.service.NopDatavErrors.ARG_ALERT_RULE_ID;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_ALERT_PANEL_NOT_FOUND;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_ALERT_RULE_NOT_FOUND;

/**
 * 告警评估器（D5-2，schedule-report-design.md §15/§19/§23）。
 *
 * <p>核心管线：加载规则 → 加载 panel（缺失抛 {@code ERR_DATAV_ALERT_PANEL_NOT_FOUND}）→
 * {@link PanelDataBinder#queryPanelData} 取数 → {@link AlertAggregator} 求标量 →
 * {@link AlertThresholdComparator} 比较 → 状态机转换（OK/TRIGGERED 两态）+ rearm 判定 →
 * 需通知时调 {@link NotificationSender#sendAlert} → 更新 {@link NopDatavAlertState}。</p>
 *
 * <p><b>容错约定（schedule-report-design.md §19）</b>：面板缺失/查询失败时记 {@code errorMsg}、
 * {@code state} 保持、方法吞错返回正常结果（不传播）；评估方法返回 {@link EvalResult}，
 * 由调度器层（{@link NopDatavAlertScheduler#executeScheduledAlert}）决定是否吞错。</p>
 *
 * <p><b>无数据行处理</b>：面板查询返回 0 行 → 视为条件不满足（不触发、不抛错），
 * 记 {@code consecutiveEvalCount = 0}。</p>
 *
 * <p><b>PanelDataBinder 非 bean</b>：本评估器注入 {@link IDaoProvider}+{@link IJdbcTemplate}
 * 后 {@code new PanelDataBinder(...)} 构造，与 {@code NopDatavPanelBizModel:47} 同模式。</p>
 */
public class AlertEvaluator {

    private static final Logger LOG = LoggerFactory.getLogger(AlertEvaluator.class);

    private final IDaoProvider daoProvider;
    private final IJdbcTemplate jdbcTemplate;
    private final NotificationSender notificationSender;

    /**
     * @param daoProvider       DAO 提供者
     * @param jdbcTemplate      JDBC 模板（PanelDataBinder 取数依赖）
     * @param notificationSender 通知发送器（D5-1 bean，本评估器调 sendAlert）
     */
    @Inject
    public AlertEvaluator(IDaoProvider daoProvider, IJdbcTemplate jdbcTemplate,
                          NotificationSender notificationSender) {
        this.daoProvider = daoProvider;
        this.jdbcTemplate = jdbcTemplate;
        this.notificationSender = notificationSender;
    }

    /**
     * 评估告警规则一次。
     *
     * <p>本方法<b>不吞业务错误</b>——异常向上抛由 {@link NopDatavAlertScheduler#executeScheduledAlert}
     * 统一吞错返回正常结果（FAILED-brick 规避）。但面板缺失/查询失败属容错路径：
     * 记 {@code errorMsg}、{@code state} 保持、返回带 error 的 {@link EvalResult}（不抛）。</p>
     *
     * @param ruleId 告警规则 ID
     * @return 评估结果（含是否触发、是否通知、新状态、当前值、错误信息）
     */
    public EvalResult evaluate(String ruleId) {
        NopDatavAlertRule rule = daoProvider.daoFor(NopDatavAlertRule.class).getEntityById(ruleId);
        if (rule == null) {
            throw new NopException(ERR_DATAV_ALERT_RULE_NOT_FOUND).param(ARG_ALERT_RULE_ID, ruleId);
        }

        NopDatavAlertState state = loadOrCreateState(rule);
        long now = System.currentTimeMillis();
        Timestamp nowTs = new Timestamp(now);
        state.setLastEvalTime(nowTs);
        state.setErrorMsg(null);

        // 加载 panel（缺失 → 容错：记 errorMsg、state 保持、返回 error 结果）
        NopDatavPanel panel = daoProvider.daoFor(NopDatavPanel.class).getEntityById(rule.getPanelId());
        if (panel == null) {
            String msg = "Panel not found: " + rule.getPanelId();
            state.setErrorMsg(msg);
            saveState(state);
            return new EvalResult(ruleId, state.getState(), null, null, false, false, msg);
        }

        // 取数
        PanelDataResult data;
        try {
            PanelDataBinder binder = new PanelDataBinder(daoProvider, jdbcTemplate);
            Integer rowLimit = NopDatavConfigs.CFG_DATAV_ALERT_EVAL_MAX_ROWS.get();
            Map<String, Object> requestParams = parseParams(rule.getParams());
            data = binder.queryPanelData(rule.getPanelId(), panel, requestParams, rowLimit);
        } catch (Exception e) {
            // 面板查询失败 → 容错：记 errorMsg、state 保持、返回 error 结果（不抛）
            Throwable reason = NopException.adapt(e);
            String msg = "Panel query failed: " + safeMsg(reason);
            LOG.error("nop.datav.alert-evaluator.query-failed: alertRuleId={} panelId={}",
                    ruleId, rule.getPanelId(), reason);
            state.setErrorMsg(msg);
            saveState(state);
            return new EvalResult(ruleId, state.getState(), null, null, false, false, msg);
        }

        // 聚合求标量（列不存在 / 非数值 → 抛错，由调度器层吞）
        AlertAggregator.ScalarResult scalar = AlertAggregator.aggregate(
                data, rule.getValueField(), rule.getAggregation(), ruleId);

        // 无数据行 → 视为条件不满足
        boolean conditionMet;
        BigDecimal currentValueBigDecimal;
        String currentValueStr;
        if (!scalar.hasValue()) {
            conditionMet = false;
            currentValueBigDecimal = null;
            currentValueStr = "<no data>";
        } else {
            currentValueBigDecimal = scalar.value;
            currentValueStr = scalar.value.toPlainString();
            conditionMet = AlertThresholdComparator.compare(
                    currentValueBigDecimal, rule.getOperator(),
                    rule.getThresholdValue(), rule.getThresholdValue2(), ruleId);
        }

        // consecutiveEvalCount：纯审计（条件满足 +1 / 不满足归 0），不影响状态转换
        int newCount = conditionMet
                ? safeInt(state.getConsecutiveEvalCount()) + 1
                : 0;
        state.setConsecutiveEvalCount(newCount);

        // 状态机转换 + rearm 判定（schedule-report-design.md §15）
        String oldState = state.getState();
        if (NopDatavAlertStateValue.isOk(oldState)) {
            if (conditionMet) {
                // OK + 条件满足 → TRIGGERED（发告警通知）
                // Dim14-03: 先持久化 interim state（TRIGGERED 但 lastNotifiedTime 清空），
                // 通知成功后才置 lastNotifiedTime。通知失败时异常向上抛，lastNotifiedTime 保持 null，
                // 下次评估（rearmSeconds > 0）可经 shouldRearm(null)=true 立即重试。
                state.setState(NopDatavAlertStateValue.TRIGGERED.getValue());
                state.setLastTriggeredTime(nowTs);
                state.setLastNotifiedTime(null);
                saveState(state);
                sendAlertNotification(rule, state.getState(), currentValueStr, NopDatavAlertType.TRIGGER);
                state.setLastNotifiedTime(nowTs);
                saveState(state);
                return new EvalResult(ruleId, state.getState(), currentValueBigDecimal, currentValueStr,
                        true, true, null);
            } else {
                // OK + 条件不满足 → 保持 OK（不通知）
                saveState(state);
                return new EvalResult(ruleId, state.getState(), currentValueBigDecimal, currentValueStr,
                        false, false, null);
            }
        } else {
            // TRIGGERED 态
            if (!conditionMet) {
                // TRIGGERED + 条件不再满足 → OK（发恢复通知）
                // Dim14-03: 先持久化 state=OK（正确反映条件不再满足），通知成功后才置 lastResolvedTime。
                // 通知失败时异常向上抛，state 已为 OK（条件不再满足的客观事实），lastResolvedTime 保持旧值。
                state.setState(NopDatavAlertStateValue.OK.getValue());
                saveState(state);
                sendAlertNotification(rule, state.getState(), currentValueStr, NopDatavAlertType.RECOVER);
                state.setLastResolvedTime(nowTs);
                saveState(state);
                return new EvalResult(ruleId, state.getState(), currentValueBigDecimal, currentValueStr,
                        false, true, null);
            } else {
                // TRIGGERED + 条件持续 → rearm 判定
                int rearmSeconds = rule.getRearmSeconds() == null ? 0 : rule.getRearmSeconds();
                if (rearmSeconds > 0 && shouldRearm(state.getLastNotifiedTime(), rearmSeconds, now)) {
                    // Dim14-03: 先发通知，成功后才置 lastNotifiedTime。通知失败时 lastNotifiedTime 保留
                    // 旧值（上次通知时间），下次评估 shouldRearm 仍可判定已过冷静期 → 重试。
                    sendAlertNotification(rule, state.getState(), currentValueStr, NopDatavAlertType.TRIGGER);
                    state.setLastNotifiedTime(nowTs);
                    saveState(state);
                    return new EvalResult(ruleId, state.getState(), currentValueBigDecimal, currentValueStr,
                            true, true, null);
                } else {
                    // rearmSeconds == 0 或冷静期未到 → 不重复通知
                    saveState(state);
                    return new EvalResult(ruleId, state.getState(), currentValueBigDecimal, currentValueStr,
                            true, false, null);
                }
            }
        }
    }

    /**
     * 为规则创建初始 OK 状态（规则创建时调用）。
     */
    public NopDatavAlertState createInitialState(NopDatavAlertRule rule) {
        long now = System.currentTimeMillis();
        NopDatavAlertState state = new NopDatavAlertState();
        state.setAlertRuleId(rule.getAlertRuleId());
        state.setState(NopDatavAlertStateValue.OK.getValue());
        state.setConsecutiveEvalCount(0);
        state.setDelFlag((byte) 0);
        state.setVersion(0L);
        state.setCreatedBy(rule.getCreatedBy());
        state.setCreateTime(new Timestamp(now));
        state.setUpdatedBy(rule.getUpdatedBy());
        state.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavAlertState.class).saveEntityDirectly(state);
        return state;
    }

    private NopDatavAlertState loadOrCreateState(NopDatavAlertRule rule) {
        IEntityDao<NopDatavAlertState> dao = daoProvider.daoFor(NopDatavAlertState.class);
        QueryBean q = new QueryBean();
        q.addFilter(io.nop.api.core.beans.FilterBeans.eq(
                NopDatavAlertState.PROP_NAME_alertRuleId, rule.getAlertRuleId()));
        NopDatavAlertState state = dao.findFirstByQuery(q);
        if (state != null) {
            return state;
        }
        // 状态行不存在（规则创建时未初始化）→ 兜底创建
        return createInitialState(rule);
    }

    private void saveState(NopDatavAlertState state) {
        state.setUpdatedBy("system");
        state.setUpdateTime(new Timestamp(System.currentTimeMillis()));
        daoProvider.daoFor(NopDatavAlertState.class).updateEntityDirectly(state);
    }

    private void sendAlertNotification(NopDatavAlertRule rule, String state, String currentValue,
                                       NopDatavAlertType alertType) {
        // 通知失败（如未配置发件人/无渠道/模板缺失/IM 渠道）会抛错，由调度器层吞
        notificationSender.sendAlert(rule, state, currentValue, alertType.getValue());
    }

    private static boolean shouldRearm(Timestamp lastNotifiedTime, int rearmSeconds, long now) {
        if (lastNotifiedTime == null) {
            // 从未通知过 → 满足 rearm 立即通知（理论上 TRIGGERED 态首次必然 lastNotifiedTime 已置，兜底）
            return true;
        }
        long elapsed = now - lastNotifiedTime.getTime();
        return elapsed >= rearmSeconds * 1000L;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseParams(String paramsJson) {
        if (StringHelper.isEmpty(paramsJson)) {
            return null;
        }
        try {
            Object parsed = io.nop.core.lang.json.JsonTool.parse(paramsJson);
            if (parsed instanceof Map) {
                return (Map<String, Object>) parsed;
            }
        } catch (Exception ignored) {
            // 非法 JSON → 返回 null（让 PanelDataBinder 按无参数处理）
        }
        return null;
    }

    private static int safeInt(Integer v) {
        return v == null ? 0 : v;
    }

    private static String safeMsg(Throwable t) {
        if (t == null) {
            return "unknown";
        }
        String msg = t.getMessage();
        return msg == null ? t.getClass().getSimpleName() : msg;
    }

    /**
     * 评估结果。
     *
     * <ul>
     *   <li>{@code conditionMet}：本次评估条件是否满足（基于 rearm 是否触发通知独立判定）</li>
     *   <li>{@code notified}：本次评估是否实际发送了通知（OK→TRIGGERED / TRIGGERED→OK / rearm 重发）</li>
     *   <li>{@code newState}：评估后的状态（OK/TRIGGERED）</li>
     *   <li>{@code currentValue}：评估标量（BigDecimal 形式，无数据时为 null）</li>
     *   <li>{@code currentValueStr}：评估标量字符串形式（用于日志/断言）</li>
     *   <li>{@code errorMsg}：容错路径的错误信息（面板缺失/查询失败；正常路径为 null）</li>
     * </ul>
     */
    public static final class EvalResult {
        public final String alertRuleId;
        public final String newState;
        public final BigDecimal currentValue;
        public final String currentValueStr;
        public final boolean conditionMet;
        public final boolean notified;
        public final String errorMsg;

        public EvalResult(String alertRuleId, String newState, BigDecimal currentValue,
                          String currentValueStr, boolean conditionMet, boolean notified, String errorMsg) {
            this.alertRuleId = alertRuleId;
            this.newState = newState;
            this.currentValue = currentValue;
            this.currentValueStr = currentValueStr;
            this.conditionMet = conditionMet;
            this.notified = notified;
            this.errorMsg = errorMsg;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("alertRuleId", alertRuleId);
            m.put("state", newState);
            m.put("currentValue", currentValueStr);
            m.put("conditionMet", conditionMet);
            m.put("notified", notified);
            m.put("error", errorMsg);
            return m;
        }
    }
}
