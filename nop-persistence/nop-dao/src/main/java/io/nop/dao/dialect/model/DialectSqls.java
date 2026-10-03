/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.dialect.model;

import io.nop.dao.dialect.model._gen._DialectSqls;

public class DialectSqls extends _DialectSqls {
    /**
     * WI1 BUG-5 骨架：INTERVAL 字面量输出模板（`<sqls>` 子元素，与 `<dateLiteral>` 同构）。
     * nop-dao 的 precompile 生成管线未接线（同 {@link DialectFeatures} 的 retention 手法），
     * xdef 绑定按元素名反射 setter；逐方言模板内容归 roadmap WI7b（唯一 owner），本字段
     * 只承接配置值，未配置为 null。
     */
    private String sqlIntervalLiteral;

    public DialectSqls() {

    }

    public String getSqlIntervalLiteral() {
        return sqlIntervalLiteral;
    }

    public void setSqlIntervalLiteral(String sqlIntervalLiteral) {
        this.sqlIntervalLiteral = sqlIntervalLiteral;
    }
}
