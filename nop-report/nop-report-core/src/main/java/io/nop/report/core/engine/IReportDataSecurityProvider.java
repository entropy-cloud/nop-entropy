/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.core.engine;

import io.nop.api.core.ioc.BeanContainer;

import java.util.List;

/**
 * 报表数据级安全 SPI（行级过滤 + 列级脱敏）。由 nop-report-service 提供实现
 * （bean 名 nopReportDataSecurityProvider），引擎经 {@link BeanContainer#tryGetBean} 可选消费：
 * bean 未注册时不启用数据级安全。
 */
public interface IReportDataSecurityProvider {
    String BEAN_NAME = "nopReportDataSecurityProvider";

    static IReportDataSecurityProvider get() {
        // tryGetBean 只判 provider 为空；provider 在而容器已卸载（registerInstance(null)）时会 NPE
        if (!BeanContainer.isInitialized())
            return null;
        return (IReportDataSecurityProvider) BeanContainer.tryGetBean(BEAN_NAME);
    }

    /**
     * 返回指定报表数据集的行级过滤条件，null 表示不过滤。
     *
     * @param reportPath 报表模板 VFS 路径
     * @param dsName     数据集名（标签内归一化键 dsName||'ds1'；JdbcDataSet 流式标签固定按默认键）
     * @return 条件 SQL 片段（可用 ? 占位）+ 绑定参数；占位符数量须与参数一致
     */
    RowCondition getRowCondition(String reportPath, String dsName);

    /**
     * 展示层列脱敏。dsName 为 null 时仅匹配无数据集约束的字段级规则（保守侧，防漏脱敏）。
     *
     * @return 脱敏后的值；无需脱敏时原样返回
     */
    Object maskValue(String dsName, String field, Object value);

    /**
     * 行级过滤条件：conditionSql 中的 ? 依次对应 params。
     */
    class RowCondition {
        private final String conditionSql;
        private final List<Object> params;

        public RowCondition(String conditionSql, List<Object> params) {
            this.conditionSql = conditionSql;
            this.params = params;
        }

        public String getConditionSql() {
            return conditionSql;
        }

        public List<Object> getParams() {
            return params;
        }
    }
}
