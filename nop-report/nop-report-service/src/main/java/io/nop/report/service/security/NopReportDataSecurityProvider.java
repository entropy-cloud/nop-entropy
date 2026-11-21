/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.service.security;

import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.report.core.engine.IReportDataSecurityProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 数据级安全规则提供者：规则来自 VFS 配置 /nop/report/data-security.yaml。
 * <p>
 * 规则 schema：
 *
 * <pre>
 * rules:                 # 行级过滤
 *   - reportPathPrefix: /report/sec/   # 可选，报表路径前缀
 *     dsName: ds1                      # 可选，数据集名（缺省匹配所有数据集）
 *     rowCondition: "dept_id = {deptId} and tenant_id = {tenantId}"   # {userId}/{tenantId}/{deptId} 占位符 -> ? 参数
 * masks:                 # 列级脱敏
 *   - dsName: ds1        # 可选；缺省=字段级规则，对所有数据集生效（dsName=null 的取值路径仅匹配此类规则）
 *     field: phone
 *     type: phone        # phone / name / idCard / full
 * </pre>
 * <p>
 * 加载语义：按 resource lastModified 缓存，首用加载，文件变更后下次检查重读。
 * 行规则命中但无用户上下文且条件引用了上下文占位符 → fail-closed 抛 nop.err.report.data-sec.*。
 */
public class NopReportDataSecurityProvider implements IReportDataSecurityProvider {
    static final Logger LOG = LoggerFactory.getLogger(NopReportDataSecurityProvider.class);

    public static final String CONFIG_PATH = "/nop/report/data-security.yaml";

    static final Pattern PLACEHOLDER = Pattern.compile("\\{(userId|tenantId|deptId)}");

    private volatile long lastModified = -1;
    private volatile DataSecRules rules;

    static class DataSecRules {
        final List<RowRule> rowRules;
        final List<MaskRule> maskRules;

        DataSecRules(List<RowRule> rowRules, List<MaskRule> maskRules) {
            this.rowRules = rowRules;
            this.maskRules = maskRules;
        }
    }

    static class RowRule {
        String reportPathPrefix;
        String dsName;
        String rowCondition;
    }

    static class MaskRule {
        String dsName;
        String field;
        String type;
    }

    DataSecRules getRules() {
        IResource resource = VirtualFileSystem.instance().getResource(CONFIG_PATH);
        long modified = resource == null || !resource.exists() ? 0 : resource.lastModified();
        if (rules == null || modified != lastModified) {
            synchronized (this) {
                if (rules == null || modified != lastModified) {
                    rules = loadRules(resource);
                    lastModified = modified;
                }
            }
        }
        return rules;
    }

    @SuppressWarnings("unchecked")
    DataSecRules loadRules(IResource resource) {
        if (resource == null || !resource.exists())
            return new DataSecRules(Collections.emptyList(), Collections.emptyList());
        Map<String, Object> root = (Map<String, Object>) JsonTool.parseYaml(null, resource.readText());
        List<RowRule> rowRules = new ArrayList<>();
        List<MaskRule> maskRules = new ArrayList<>();
        Object rulesNode = root == null ? null : root.get("rules");
        if (rulesNode instanceof List) {
            int index = 0;
            for (Object item : (List<Object>) rulesNode) {
                Map<String, Object> map = (Map<String, Object>) item;
                RowRule r = new RowRule();
                r.reportPathPrefix = str(map.get("reportPathPrefix"));
                r.dsName = str(map.get("dsName"));
                r.rowCondition = str(map.get("rowCondition"));
                if (StringHelper.isEmpty(r.rowCondition))
                    throw new NopException(ReportDataSecErrors.ERR_REPORT_DATA_SEC_RULE_FIELD_REQUIRED)
                            .param(ReportDataSecErrors.ARG_RULE_INDEX, index)
                            .param(ReportDataSecErrors.ARG_FIELD_NAME, "rowCondition");
                rowRules.add(r);
                index++;
            }
        }
        Object masksNode = root == null ? null : root.get("masks");
        if (masksNode instanceof List) {
            int index = 0;
            for (Object item : (List<Object>) masksNode) {
                Map<String, Object> map = (Map<String, Object>) item;
                MaskRule m = new MaskRule();
                m.dsName = str(map.get("dsName"));
                m.field = str(map.get("field"));
                m.type = str(map.get("type"));
                if (StringHelper.isEmpty(m.field))
                    throw new NopException(ReportDataSecErrors.ERR_REPORT_DATA_SEC_RULE_FIELD_REQUIRED)
                            .param(ReportDataSecErrors.ARG_RULE_INDEX, index)
                            .param(ReportDataSecErrors.ARG_FIELD_NAME, "field");
                if (StringHelper.isEmpty(m.type))
                    throw new NopException(ReportDataSecErrors.ERR_REPORT_DATA_SEC_RULE_FIELD_REQUIRED)
                            .param(ReportDataSecErrors.ARG_RULE_INDEX, index)
                            .param(ReportDataSecErrors.ARG_FIELD_NAME, "type");
                maskRules.add(m);
                index++;
            }
        }
        return new DataSecRules(rowRules, maskRules);
    }

    static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    @Override
    public RowCondition getRowCondition(String reportPath, String dsName) {
        DataSecRules current = getRules();
        for (RowRule rule : current.rowRules) {
            if (rule.reportPathPrefix != null && (reportPath == null || !reportPath.startsWith(rule.reportPathPrefix)))
                continue;
            if (rule.dsName != null && !rule.dsName.equals(dsName))
                continue;

            return buildCondition(rule.rowCondition);
        }
        return null;
    }

    RowCondition buildCondition(String template) {
        if (StringHelper.isEmpty(template))
            return null;

        IUserContext userContext = IUserContext.get();
        List<Object> params = new ArrayList<>();
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            sb.append(template, last, matcher.start()).append('?');
            String name = matcher.group(1);
            Object value = contextValue(userContext, name);
            if (value == null)
                throw new NopException(ReportDataSecErrors.ERR_REPORT_DATA_SEC_NO_USER_CONTEXT)
                        .param(ReportDataSecErrors.ARG_PLACEHOLDER, name);
            params.add(value);
            last = matcher.end();
        }
        sb.append(template.substring(last));
        return new RowCondition(sb.toString(), params);
    }

    private Object contextValue(IUserContext userContext, String name) {
        if (userContext == null)
            return null;
        switch (name) {
            case "userId":
                return userContext.getUserId();
            case "tenantId":
                return userContext.getTenantId();
            case "deptId":
                return userContext.getDeptId();
            default:
                return null;
        }
    }

    @Override
    public Object maskValue(String dsName, String field, Object value) {
        if (value == null)
            return null;
        DataSecRules current = getRules();
        for (MaskRule rule : current.maskRules) {
            if (!rule.field.equals(field))
                continue;
            // dsName=null 的取值路径仅匹配无数据集约束的字段级规则（保守侧）
            if (rule.dsName != null && !rule.dsName.equals(dsName))
                continue;
            return mask(String.valueOf(value), rule.type);
        }
        return value;
    }

    public String mask(String value, String type) {
        switch (type) {
            case "phone":
                return value.length() >= 7
                        ? value.substring(0, 3) + "****" + value.substring(value.length() - 4)
                        : "****";
            case "name":
                return value.length() <= 1 ? value : value.charAt(0) + "**";
            case "idCard":
                return value.length() >= 8
                        ? value.substring(0, 4) + "********" + value.substring(value.length() - 4)
                        : "****";
            case "full":
                return "****";
            default:
                return value;
        }
    }
}
