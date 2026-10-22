/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.core.engine;

/**
 * 报表/数据源授权检查 SPI。由 nop-report-service 提供实现（bean 名 nopReportAuthChecker），
 * 引擎经 {@link io.nop.api.core.ioc.BeanContainer#tryGetBean} 可选消费：
 * bean 未注册（引擎作嵌入式库使用）时不启用授权检查。
 */
public interface IReportAuthChecker {
    String BEAN_NAME = "nopReportAuthChecker";

    /**
     * 可选查找容器中的 checker；未注册（引擎作嵌入式库使用）返回 null，此时引擎不启用授权检查。
     */
    static IReportAuthChecker get() {
        return (IReportAuthChecker) io.nop.api.core.ioc.BeanContainer.tryGetBean(BEAN_NAME);
    }

    /**
     * 校验当前用户可渲染指定报表。未授权抛 nop.err.report.auth.* 错误。
     *
     * @param reportPath 报表模板的 VFS 全路径（xpt-rt 约定与 NopReportDefinition.rptText 精确匹配）
     */
    void checkReportAccess(String reportPath);

    /**
     * 校验当前用户可访问指定数据源。未授权抛 nop.err.report.auth.* 错误。
     *
     * @param querySpace 数据源名；null 表示默认数据源（由报表级权限覆盖，实现可直接放行）
     */
    void checkDataSourceAccess(String querySpace);
}
