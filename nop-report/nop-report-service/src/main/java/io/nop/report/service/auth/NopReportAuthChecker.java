/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.service.auth;

import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.dao.api.IDaoProvider;
import io.nop.orm.IOrmTemplate;
import io.nop.report.core.engine.IReportAuthChecker;
import io.nop.report.dao.entity.NopReportDatasource;
import io.nop.report.dao.entity.NopReportDatasourceAuth;
import io.nop.report.dao.entity.NopReportDefinition;
import io.nop.report.dao.entity.NopReportDefinitionAuth;
import io.nop.report.service.NopReportConfigs;
import jakarta.inject.Inject;

import java.util.HashSet;
import java.util.Set;

import static io.nop.report.core.ReportAuthErrors.ARG_DS_NAME;
import static io.nop.report.core.ReportAuthErrors.ARG_REPORT_PATH;
import static io.nop.report.core.ReportAuthErrors.ARG_ROLES;
import static io.nop.report.core.ReportAuthErrors.ERR_REPORT_AUTH_DATASOURCE_DENIED;
import static io.nop.report.core.ReportAuthErrors.ERR_REPORT_AUTH_REPORT_DENIED;

/**
 * 报表/数据源授权检查实现（bean nopReportAuthChecker）。
 * <p>
 * 授权语义（见 docs-for-ai/03-modules/nop-report.md）：
 * <ul>
 * <li>报表定义/数据源未配置 auth 记录 → 不限制；配置了记录 → 当前用户角色须命中任一记录</li>
 * <li>无用户上下文且资源配置了 auth 记录 → fail-closed 拒绝（调度等系统调用方须显式建立用户上下文）</li>
 * <li>豁免角色由配置项 nop.report.auth.exempt-roles 指定，默认无豁免</li>
 * <li>报表路径与 NopReportDefinition.rptText 精确匹配（rptText 约定存模板 VFS 全路径）</li>
 * </ul>
 * <p>
 * 权限记录经 <b>ORM 关联加载</b>（definition.reportAuths / datasource.datasourceAuths，
 * 懒加载在 runInSession 内完成），不单独发查询。本检查由<b>系统入口</b>（对外 BizModel action，
 * 建议标注 {@code @SingleSession}）调用；内部引擎不感知授权。
 */
public class NopReportAuthChecker implements IReportAuthChecker {

    private IDaoProvider daoProvider;

    private IOrmTemplate ormTemplate;

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    @Inject
    public void setOrmTemplate(IOrmTemplate ormTemplate) {
        this.ormTemplate = ormTemplate;
    }

    @Override
    public void checkReportAccess(String reportPath) {
        ormTemplate.runInSession(() -> {
            NopReportDefinition probe = new NopReportDefinition();
            probe.setRptText(reportPath);
            NopReportDefinition definition = daoProvider.daoFor(NopReportDefinition.class).findFirstByExample(probe);
            if (definition == null)
                return;

            Set<String> authRoleIds = new HashSet<>();
            for (NopReportDefinitionAuth auth : definition.getReportAuths())
                authRoleIds.add(auth.getRoleId());
            if (authRoleIds.isEmpty())
                return;

            checkRoles(authRoleIds, reportPath, null);
        });
    }

    @Override
    public void checkDataSourceAccess(String querySpace) {
        if (querySpace == null)
            return;

        ormTemplate.runInSession(() -> {
            NopReportDatasource probe = new NopReportDatasource();
            probe.setName(querySpace);
            NopReportDatasource datasource = daoProvider.daoFor(NopReportDatasource.class).findFirstByExample(probe);
            if (datasource == null)
                return;

            Set<String> authRoleIds = new HashSet<>();
            for (NopReportDatasourceAuth auth : datasource.getDatasourceAuths())
                authRoleIds.add(auth.getRoleId());
            if (authRoleIds.isEmpty())
                return;

            checkRoles(authRoleIds, null, querySpace);
        });
    }

    private void checkRoles(Set<String> authRoleIds, String reportPath, String dsName) {
        IUserContext userContext = IUserContext.get();
        Set<String> roles = userContext == null ? Set.of() : userContext.getRoles();

        String exemptRoles = NopReportConfigs.CFG_REPORT_AUTH_EXEMPT_ROLES.get();
        for (String exemptRole : exemptRoles.split(",")) {
            if (!exemptRole.isEmpty())
                authRoleIds.add(exemptRole.trim());
        }

        for (String roleId : authRoleIds) {
            if (roles.contains(roleId))
                return;
        }

        if (reportPath != null)
            throw new NopException(ERR_REPORT_AUTH_REPORT_DENIED)
                    .param(ARG_REPORT_PATH, reportPath)
                    .param(ARG_ROLES, roles);
        throw new NopException(ERR_REPORT_AUTH_DATASOURCE_DENIED)
                .param(ARG_DS_NAME, dsName)
                .param(ARG_ROLES, roles);
    }
}
