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
import io.nop.report.core.engine.IReportAuthChecker;
import io.nop.report.dao.entity.NopReportDatasource;
import io.nop.report.dao.entity.NopReportDatasourceAuth;
import io.nop.report.dao.entity.NopReportDefinition;
import io.nop.report.dao.entity.NopReportDefinitionAuth;
import io.nop.report.service.NopReportConfigs;
import jakarta.inject.Inject;

import java.util.HashSet;
import java.util.List;
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
 */
public class NopReportAuthChecker implements IReportAuthChecker {

    private IDaoProvider daoProvider;

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    @Override
    public void checkReportAccess(String reportPath) {
        NopReportDefinition probe = new NopReportDefinition();
        probe.setRptText(reportPath);
        NopReportDefinition definition = daoProvider.daoFor(NopReportDefinition.class).findFirstByExample(probe);
        if (definition == null)
            return;

        NopReportDefinitionAuth authProbe = new NopReportDefinitionAuth();
        authProbe.setRptId(definition.getRptId());
        List<? extends NopReportDefinitionAuth> auths =
                daoProvider.daoFor(NopReportDefinitionAuth.class).findAllByExample(authProbe);
        if (auths.isEmpty())
            return;

        checkRoles(allowedRoleIds(auths), reportPath, null);
    }

    @Override
    public void checkDataSourceAccess(String querySpace) {
        if (querySpace == null)
            return;

        NopReportDatasource probe = new NopReportDatasource();
        probe.setName(querySpace);
        NopReportDatasource datasource = daoProvider.daoFor(NopReportDatasource.class).findFirstByExample(probe);
        if (datasource == null)
            return;

        NopReportDatasourceAuth authProbe = new NopReportDatasourceAuth();
        authProbe.setDatasourceId(datasource.getSid());
        List<? extends NopReportDatasourceAuth> auths =
                daoProvider.daoFor(NopReportDatasourceAuth.class).findAllByExample(authProbe);
        if (auths.isEmpty())
            return;

        checkRoles(allowedRoleIds(auths), null, querySpace);
    }

    private <T> Set<String> allowedRoleIds(List<T> auths) {
        Set<String> roleIds = new HashSet<>();
        for (Object auth : auths) {
            if (auth instanceof NopReportDefinitionAuth)
                roleIds.add(((NopReportDefinitionAuth) auth).getRoleId());
            else if (auth instanceof NopReportDatasourceAuth)
                roleIds.add(((NopReportDatasourceAuth) auth).getRoleId());
        }
        return roleIds;
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
