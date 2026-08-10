package io.nop.datav.service.report;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.resource.IResourceReference;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.file.core.IFileRecord;
import io.nop.file.core.IFileStore;
import io.nop.integration.api.email.EmailMessage;
import io.nop.integration.api.email.IEmailSender;
import io.nop.sys.dao.entity.NopSysNoticeTemplate;
import io.nop.datav.dao.entity.NopDatavAlertRule;
import io.nop.datav.dao.entity.NopDatavDashboard;
import io.nop.datav.dao.entity.NopDatavReportDelivery;
import io.nop.datav.dao.entity.NopDatavReportTask;

import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.datav.service.NopDatavErrors.ARG_ALERT_RULE_ID;
import static io.nop.datav.service.NopDatavErrors.ARG_NOTIFY_CHANNELS;
import static io.nop.datav.service.NopDatavErrors.ARG_REPORT_TASK_ID;
import static io.nop.datav.service.NopDatavErrors.ARG_TEMPLATE_KEY;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_ALERT_NO_NOTIFIABLE_CHANNEL;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_ALERT_SENDER_NOT_CONFIGURED;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_ALERT_TEMPLATE_NOT_FOUND;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_REPORT_NO_NOTIFIABLE_CHANNEL;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_REPORT_SENDER_NOT_CONFIGURED;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_REPORT_TEMPLATE_NOT_FOUND;
import static io.nop.datav.service.NopDatavConfigs.CFG_DATAV_ALERT_DEFAULT_SUBJECT;
import static io.nop.datav.service.NopDatavConfigs.CFG_DATAV_REPORT_DEFAULT_SENDER;
import static io.nop.datav.service.NopDatavConfigs.CFG_DATAV_REPORT_DEFAULT_SUBJECT;

/**
 * 定时报告通知发送器（D5-1）。
 *
 * <p>职责：渲染通知模板（{@link StringHelper#renderTemplate} + {@link NopSysNoticeTemplate}）
 * → 按 {@code notifyChannels} 分发 → 记录已送达渠道。</p>
 *
 * <p><b>渠道范围</b>：邮件（{@link IEmailSender#sendEmail}，带附件）端到端打通；
 * IM 渠道（{@code notifyChannels} 含 {@code im}）显式抛 {@link UnsupportedOperationException}
 * （实现在 nop-ai-gateway，列为 deferred，非静默/空壳）。</p>
 *
 * <p><b>显式失败约定（Minimum Rules #24）</b>：
 * <ul>
 *   <li>{@code notifyChannels} 为空 JSON 数组 → {@link NopDatavErrors#ERR_DATAV_REPORT_NO_NOTIFIABLE_CHANNEL}</li>
 *   <li>邮件渠道但发件人未配置（{@link NopDatavConfigs#CFG_DATAV_REPORT_DEFAULT_SENDER} 为空）
 *       → {@link NopDatavErrors#ERR_DATAV_REPORT_SENDER_NOT_CONFIGURED}</li>
 *   <li>模板键映射失败（{@code NopSysNoticeTemplate.name} 查不到）→
 *       {@link NopDatavErrors#ERR_DATAV_REPORT_TEMPLATE_NOT_FOUND}</li>
 *   <li>IM 渠道 → {@link UnsupportedOperationException}（非静默跳过）</li>
 * </ul>
 * 无静默跳过 / continue / 空返回路径。</p>
 */
public class NotificationSender {

    /** notifyChannels / recipients 中的渠道标识值（与 dict datav/notify-channel 的 value 对应） */
    public static final String CHANNEL_EMAIL = "email";
    public static final String CHANNEL_IM = "im";

    /** NopSysNoticeTemplate.tplType 约定值（本计划定义的报告交付通知模板类型）。
     *  受 NOP_SYS_NOTICE_TEMPLATE.TPL_TYPE 列 precision=10 限制，取 ≤ 10 字符的简短形式。 */
    public static final String TPL_TYPE_REPORT_DELIVERY = "rpt-deliv";

    /** 告警通知默认模板键（rule.templateKey 为空时使用，schedule-report-design.md §17） */
    public static final String DEFAULT_ALERT_TEMPLATE_KEY = "alert-notify";

    /** NopSysNoticeTemplate.tplType 约定值（D5-2 告警通知模板类型）。
     *  受 NOP_SYS_NOTICE_TEMPLATE.TPL_TYPE 列 precision=10 限制，取 ≤ 10 字符的简短形式。 */
    public static final String TPL_TYPE_ALERT_NOTIFY = "alert-ntl";

    private final IDaoProvider daoProvider;
    private final IFileStore fileStore;
    private IEmailSender emailSender;

    @Inject
    public NotificationSender(IDaoProvider daoProvider, IFileStore fileStore) {
        this.daoProvider = daoProvider;
        this.fileStore = fileStore;
    }

    /**
     * 注入 {@link IEmailSender}（{@code @Nullable}——宿主未注册邮件发送器时不注入，
     * 邮件渠道显式失败 {@link NopDatavErrors#ERR_DATAV_REPORT_SENDER_NOT_CONFIGURED}，非静默跳过）。
     * 生产 runtime 由宿主 app 提供；测试环境注入 mock 发送器断言 sendEmail 调用。
     */
    @Inject
    public void setEmailSender(@Nullable IEmailSender emailSender) {
        this.emailSender = emailSender;
    }

    /**
     * 发送报告通知。
     *
     * @param task           报告任务（含 recipients/notifyChannels/templateKey/format）
     * @param delivery       交付记录（含 generatedFileRecordId/rowCount）
     * @return 已送达渠道列表（CSV 字符串，写入 delivery.deliveredChannels）
     * @throws NopException 无通知渠道 / 发件人未配置 / 模板未找到（显式失败）
     * @throws UnsupportedOperationException IM 渠道（未实现，非静默）
     */
    public List<String> sendReport(NopDatavReportTask task, NopDatavReportDelivery delivery,
                                   @Nullable IServiceContext context) {
        List<String> channels = parseJsonArray(task.getNotifyChannels());
        if (channels.isEmpty()) {
            throw new NopException(ERR_DATAV_REPORT_NO_NOTIFIABLE_CHANNEL)
                    .param(ARG_REPORT_TASK_ID, task.getReportTaskId())
                    .param(ARG_NOTIFY_CHANNELS, task.getNotifyChannels());
        }

        List<String> recipients = parseJsonArray(task.getRecipients());
        Map<String, Object> templateVars = buildTemplateVars(task, delivery);

        // 渲染模板（subject + body）
        RenderedTemplate tpl = renderTemplate(task, templateVars);

        List<String> delivered = new ArrayList<>();
        for (String channel : channels) {
            String c = channel == null ? "" : channel.trim().toLowerCase();
            if (CHANNEL_EMAIL.equals(c)) {
                deliverViaEmail(task, recipients, tpl, delivery);
                delivered.add(CHANNEL_EMAIL);
            } else if (CHANNEL_IM.equals(c)) {
                // IM 渠道实现在 nop-ai-gateway，列 deferred（schedule-report-design.md §1/§10）
                throw new UnsupportedOperationException(
                        "IM channel not yet implemented: notifyChannels contains 'im' for reportTaskId="
                                + task.getReportTaskId() + " (IChannelMessageService impl in nop-ai-gateway)");
            } else {
                // 未知渠道——显式失败（非静默跳过）
                throw new NopException(ERR_DATAV_REPORT_NO_NOTIFIABLE_CHANNEL)
                        .param(ARG_REPORT_TASK_ID, task.getReportTaskId())
                        .param(ARG_NOTIFY_CHANNELS, task.getNotifyChannels());
            }
        }
        return delivered;
    }

    // ============================================================
    // 告警通知（D5-2，无附件短文本，schedule-report-design.md §17）
    // ============================================================

    /**
     * 发送告警通知（D5-2，无附件短文本）。
     *
     * <p>与 {@link #sendReport} 共享渠道解析 / recipients JSON 解析 / {@link IEmailSender} 接线，
     * 但<b>无附件</b>（告警通知通常是短文本：告警/恢复 + 当前值）。</p>
     *
     * <p><b>渠道范围</b>：邮件（{@link IEmailSender#sendEmail}，无附件）端到端打通；
     * IM 渠道显式抛 {@link UnsupportedOperationException}（沿用 D5-1 §1/§10 deferred 裁定）。</p>
     *
     * <p><b>显式失败约定（Minimum Rules #24）</b>：
     * <ul>
     *   <li>{@code notifyChannels} 为空 JSON 数组 → {@link NopDatavErrors#ERR_DATAV_ALERT_NO_NOTIFIABLE_CHANNEL}</li>
     *   <li>邮件渠道但发件人未配置 → {@link NopDatavErrors#ERR_DATAV_ALERT_SENDER_NOT_CONFIGURED}</li>
     *   <li>模板缺失 → {@link NopDatavErrors#ERR_DATAV_ALERT_TEMPLATE_NOT_FOUND}</li>
     *   <li>IM 渠道 → {@link UnsupportedOperationException}</li>
     * </ul>
     * 告警专用错误码自建（不复用 report 码），避免跨 plan 命名耦合与错误消息文本不匹配。</p>
     *
     * @param rule         告警规则（含 recipients/notifyChannels/templateKey）
     * @param state        告警当前状态（OK/TRIGGERED）
     * @param currentValue 当前评估标量值（字符串形式，用于模板变量）
     * @param alertType    告警类型（trigger/recover）
     * @return 已送达渠道列表
     */
    public List<String> sendAlert(NopDatavAlertRule rule, String state, String currentValue,
                                  String alertType) {
        List<String> channels = parseJsonArray(rule.getNotifyChannels());
        if (channels.isEmpty()) {
            throw new NopException(ERR_DATAV_ALERT_NO_NOTIFIABLE_CHANNEL)
                    .param(ARG_ALERT_RULE_ID, rule.getAlertRuleId())
                    .param(ARG_NOTIFY_CHANNELS, rule.getNotifyChannels());
        }

        List<String> recipients = parseJsonArray(rule.getRecipients());
        Map<String, Object> templateVars = buildAlertTemplateVars(rule, state, currentValue, alertType);

        RenderedTemplate tpl = renderAlertTemplate(rule, templateVars);

        List<String> delivered = new ArrayList<>();
        for (String channel : channels) {
            String c = channel == null ? "" : channel.trim().toLowerCase();
            if (CHANNEL_EMAIL.equals(c)) {
                deliverAlertViaEmail(rule, recipients, tpl);
                delivered.add(CHANNEL_EMAIL);
            } else if (CHANNEL_IM.equals(c)) {
                throw new UnsupportedOperationException(
                        "IM channel not yet implemented: notifyChannels contains 'im' for alertRuleId="
                                + rule.getAlertRuleId() + " (IChannelMessageService impl in nop-ai-gateway)");
            } else {
                throw new NopException(ERR_DATAV_ALERT_NO_NOTIFIABLE_CHANNEL)
                        .param(ARG_ALERT_RULE_ID, rule.getAlertRuleId())
                        .param(ARG_NOTIFY_CHANNELS, rule.getNotifyChannels());
            }
        }
        return delivered;
    }

    private void deliverAlertViaEmail(NopDatavAlertRule rule, List<String> recipients, RenderedTemplate tpl) {
        if (emailSender == null) {
            throw new NopException(ERR_DATAV_ALERT_SENDER_NOT_CONFIGURED)
                    .param(ARG_ALERT_RULE_ID, rule.getAlertRuleId());
        }
        String sender = CFG_DATAV_REPORT_DEFAULT_SENDER.get();
        if (StringHelper.isEmpty(sender)) {
            throw new NopException(ERR_DATAV_ALERT_SENDER_NOT_CONFIGURED)
                    .param(ARG_ALERT_RULE_ID, rule.getAlertRuleId());
        }
        if (recipients.isEmpty()) {
            throw new NopException(ERR_DATAV_ALERT_NO_NOTIFIABLE_CHANNEL)
                    .param(ARG_ALERT_RULE_ID, rule.getAlertRuleId())
                    .param(ARG_NOTIFY_CHANNELS, rule.getRecipients());
        }

        EmailMessage mail = new EmailMessage();
        mail.setFrom(sender);
        mail.setTo(recipients);
        mail.setSubject(tpl.subject);
        mail.setText(tpl.body);
        mail.setHtml(false);
        // 告警通知无附件（与 sendReport 区别）
        emailSender.sendEmail(mail);
    }

    private RenderedTemplate renderAlertTemplate(NopDatavAlertRule rule, Map<String, Object> vars) {
        // subject 永远从告警配置项渲染（保证有值）
        String subject = StringHelper.renderTemplate(
                CFG_DATAV_ALERT_DEFAULT_SUBJECT.get(), vars::get);

        String templateKey = rule.getTemplateKey();
        if (StringHelper.isEmpty(templateKey)) {
            // 无模板键 → body 用默认占位
            return new RenderedTemplate(subject, defaultAlertBody(vars));
        }
        NopSysNoticeTemplate tpl = findTemplateByName(templateKey);
        if (tpl == null) {
            throw new NopException(ERR_DATAV_ALERT_TEMPLATE_NOT_FOUND)
                    .param(ARG_TEMPLATE_KEY, templateKey);
        }
        String body = StringHelper.renderTemplate(tpl.getContent(), vars::get);
        return new RenderedTemplate(subject, body);
    }

    private static String defaultAlertBody(Map<String, Object> vars) {
        StringBuilder sb = new StringBuilder();
        sb.append("Alert Rule: ").append(vars.get("ruleName")).append('\n');
        sb.append("Panel: ").append(vars.get("panelId")).append('\n');
        sb.append("State: ").append(vars.get("state")).append('\n');
        sb.append("Type: ").append(vars.get("alertType")).append('\n');
        sb.append("Current Value: ").append(vars.get("currentValue")).append('\n');
        sb.append("Threshold: ").append(vars.get("thresholdValue"));
        if (vars.get("thresholdValue2") != null && !"".equals(vars.get("thresholdValue2"))) {
            sb.append(" ~ ").append(vars.get("thresholdValue2"));
        }
        sb.append('\n');
        sb.append("Operator: ").append(vars.get("operator"));
        return sb.toString();
    }

    private static Map<String, Object> buildAlertTemplateVars(NopDatavAlertRule rule, String state,
                                                              String currentValue, String alertType) {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("ruleName", rule.getRuleName());
        vars.put("panelId", rule.getPanelId());
        vars.put("state", state == null ? "" : state);
        vars.put("currentValue", currentValue == null ? "" : currentValue);
        vars.put("alertType", alertType == null ? "" : alertType);
        vars.put("operator", rule.getOperator() == null ? "" : rule.getOperator());
        vars.put("thresholdValue", rule.getThresholdValue() == null ? "" : rule.getThresholdValue().toPlainString());
        vars.put("thresholdValue2", rule.getThresholdValue2() == null ? "" : rule.getThresholdValue2().toPlainString());
        return vars;
    }

    // ============================================================
    // 邮件分发（报告）
    // ============================================================

    private void deliverViaEmail(NopDatavReportTask task, List<String> recipients,
                                 RenderedTemplate tpl, NopDatavReportDelivery delivery) {
        if (emailSender == null) {
            // 宿主未提供 IEmailSender 实现——显式失败（非静默）
            throw new NopException(ERR_DATAV_REPORT_SENDER_NOT_CONFIGURED)
                    .param(ARG_REPORT_TASK_ID, task.getReportTaskId());
        }
        String sender = CFG_DATAV_REPORT_DEFAULT_SENDER.get();
        if (StringHelper.isEmpty(sender)) {
            throw new NopException(ERR_DATAV_REPORT_SENDER_NOT_CONFIGURED)
                    .param(ARG_REPORT_TASK_ID, task.getReportTaskId());
        }
        if (recipients.isEmpty()) {
            // 邮件渠道已启用但 recipients 为空——视为无通知渠道（显式失败）
            throw new NopException(ERR_DATAV_REPORT_NO_NOTIFIABLE_CHANNEL)
                    .param(ARG_REPORT_TASK_ID, task.getReportTaskId())
                    .param(ARG_NOTIFY_CHANNELS, task.getRecipients());
        }

        EmailMessage mail = new EmailMessage();
        mail.setFrom(sender);
        mail.setTo(recipients);
        mail.setSubject(tpl.subject);
        mail.setText(tpl.body);
        mail.setHtml(false);

        // 附件：从 IFileStore 重建 IResource（temp resource 在 executor 写出后即删，
        // 这里经 fileRecordId 取持久化副本作为附件源）
        if (!StringHelper.isEmpty(delivery.getGeneratedFileRecordId())) {
            IFileRecord fileRecord = fileStore.getFile(delivery.getGeneratedFileRecordId());
            if (fileRecord != null && fileRecord.getResource() != null) {
                mail.setAttachments(Collections.<IResourceReference>singletonList(fileRecord.getResource()));
            }
        }

        emailSender.sendEmail(mail);
    }

    // ============================================================
    // 模板渲染
    // ============================================================

    private RenderedTemplate renderTemplate(NopDatavReportTask task, Map<String, Object> vars) {
        // subject 永远从配置项渲染（保证有值，不依赖模板存在）
        String subject = StringHelper.renderTemplate(
                CFG_DATAV_REPORT_DEFAULT_SUBJECT.get(), vars::get);

        // body 从 NopSysNoticeTemplate 渲染；模板键映射失败 → 显式失败（非空模板）
        String templateKey = task.getTemplateKey();
        if (StringHelper.isEmpty(templateKey)) {
            // 无模板键——body 用默认占位（subject 已含报告名）
            return new RenderedTemplate(subject, defaultBody(vars));
        }
        NopSysNoticeTemplate tpl = findTemplateByName(templateKey);
        if (tpl == null) {
            throw new NopException(ERR_DATAV_REPORT_TEMPLATE_NOT_FOUND)
                    .param(ARG_TEMPLATE_KEY, templateKey);
        }
        String body = StringHelper.renderTemplate(tpl.getContent(), vars::get);
        return new RenderedTemplate(subject, body);
    }

    private NopSysNoticeTemplate findTemplateByName(String name) {
        IEntityDao<NopSysNoticeTemplate> dao = daoProvider.daoFor(NopSysNoticeTemplate.class);
        io.nop.api.core.beans.query.QueryBean q = new io.nop.api.core.beans.query.QueryBean();
        q.addFilter(io.nop.api.core.beans.FilterBeans.eq(NopSysNoticeTemplate.PROP_NAME_name, name));
        return dao.findFirstByQuery(q);
    }

    private static String defaultBody(Map<String, Object> vars) {
        StringBuilder sb = new StringBuilder();
        sb.append("Report: ").append(vars.get("reportName")).append('\n');
        sb.append("Dashboard: ").append(vars.get("dashboardName")).append('\n');
        sb.append("Generated at: ").append(vars.get("generatedTime")).append('\n');
        sb.append("File: ").append(vars.get("fileName")).append('\n');
        sb.append("Rows: ").append(vars.get("rowCount")).append('\n');
        sb.append("Status: ").append(vars.get("deliveryStatus"));
        return sb.toString();
    }

    private Map<String, Object> buildTemplateVars(NopDatavReportTask task, NopDatavReportDelivery delivery) {
        // 加载看板名（缺失则用 dashboardId 兜底）
        String dashboardName = task.getDashboardId();
        NopDatavDashboard dashboard = daoProvider.daoFor(NopDatavDashboard.class)
                .getEntityById(task.getDashboardId());
        if (dashboard != null && !StringHelper.isEmpty(dashboard.getDisplayName())) {
            dashboardName = dashboard.getDisplayName();
        } else if (dashboard != null && !StringHelper.isEmpty(dashboard.getDashboardName())) {
            dashboardName = dashboard.getDashboardName();
        }

        // 文件大小/名（从 IFileRecord 取，若存在）
        String fileName = "";
        String fileSize = "";
        if (!StringHelper.isEmpty(delivery.getGeneratedFileRecordId())) {
            try {
                IFileRecord record = fileStore.getFile(delivery.getGeneratedFileRecordId());
                if (record != null) {
                    fileName = record.getFileName() == null ? "" : record.getFileName();
                    fileSize = String.valueOf(record.getLength());
                }
            } catch (Exception ignored) {
                // 模板变量缺失不应阻断送达——保留空值
            }
        }

        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("reportName", task.getTaskName());
        vars.put("dashboardName", dashboardName);
        vars.put("generatedTime", String.valueOf(delivery.getStartTime()));
        vars.put("fileName", fileName);
        vars.put("fileSize", fileSize);
        vars.put("rowCount", String.valueOf(delivery.getRowCount()));
        vars.put("deliveryStatus", NopDatavReportDeliveryStatus.label(delivery.getStatus()));
        return vars;
    }

    // ============================================================
    // helpers
    // ============================================================

    @SuppressWarnings("unchecked")
    private static List<String> parseJsonArray(String json) {
        if (StringHelper.isEmpty(json)) {
            return Collections.emptyList();
        }
        try {
            Object parsed = JsonTool.parse(json);
            if (parsed instanceof List) {
                List<String> result = new ArrayList<>();
                for (Object o : (List<Object>) parsed) {
                    if (o != null) {
                        String s = String.valueOf(o).trim();
                        if (!s.isEmpty()) {
                            result.add(s);
                        }
                    }
                }
                return result;
            }
        } catch (Exception ignored) {
            // 非法 JSON → 视为空（下游会显式失败）
        }
        return Collections.emptyList();
    }

    /** 测试辅助：暴露 emailSender 实例（断言 sendEmail 被调用） */
    @Nullable
    public IEmailSender getEmailSender() {
        return emailSender;
    }

    /** 渲染后的通知（subject + body） */
    private static final class RenderedTemplate {
        final String subject;
        final String body;

        RenderedTemplate(String subject, String body) {
            this.subject = subject;
            this.body = body;
        }
    }
}
