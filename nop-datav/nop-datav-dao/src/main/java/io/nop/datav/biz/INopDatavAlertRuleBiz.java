package io.nop.datav.biz;

import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.core.context.IServiceContext;
import io.nop.orm.biz.ICrudBiz;

import io.nop.datav.dao.entity.NopDatavAlertRule;
import io.nop.datav.dao.entity.NopDatavAlertState;

/**
 * 告警规则 BizModel 接口（D5-2）。
 *
 * <p>CRUD 继承 {@link ICrudBiz}；自定义 action：
 * <ul>
 *   <li>{@link #enableAlertRule} / {@link #disableAlertRule}：启用/禁用规则（注册/注销 cron job）</li>
 *   <li>{@link #evaluateAlertNow}：手动立即评估一次（便于测试与运维，断言即时结果）</li>
 *   <li>{@link #getAlertState}：查询告警状态（owner/admin 可读）</li>
 * </ul>
 * </p>
 *
 * <p>action 经 {@code @Auth} + 看板 owner RLS（规则经 panelId → dashboard 间接归属，schedule-report-design.md §21）。</p>
 */
public interface INopDatavAlertRuleBiz extends ICrudBiz<NopDatavAlertRule> {

    @BizMutation("enableAlertRule")
    NopDatavAlertRule enableAlertRule(@Name("id") String alertRuleId, IServiceContext context);

    @BizMutation("disableAlertRule")
    NopDatavAlertRule disableAlertRule(@Name("id") String alertRuleId, IServiceContext context);

    /**
     * 手动立即评估一次告警规则。同步返回评估后的状态（{@link NopDatavAlertState}）。
     */
    @BizMutation("evaluateAlertNow")
    NopDatavAlertState evaluateAlertNow(@Name("id") String alertRuleId, IServiceContext context);

    @BizQuery("getAlertState")
    NopDatavAlertState getAlertState(@Name("id") String alertRuleId, IServiceContext context);
}
