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

    /**
     * WI3 版本门：INTERSECT 集合算子关键字（`<sqls>` 属性，与既有 exceptKeyword 对称）。
     * 值面契约（`ai-dev/design/eql-cross-db-compat/2026-10-04-wi3-phase0-adjudications.md` §二）：
     * null = 未配置（生成器回落缺省 "intersect"）；"none" = 方言无 INTERSECT 算子
     * （生成器编译期报错）；其他非空值 = 直接用作关键字。空串经 xdef string 绑定实测
     * 被规范化为 null、与未配置不可区分，故「显式不支持」用哨兵 none 表达。
     */
    private String intersectKeyword;

    /**
     * WI12 交付①（plan 38）：`FOR UPDATE NOWAIT` 锁后缀模板（`<sqls>` 子元素，与
     * `<forUpdate>` 同构）。presence = 能力 + 文本二合一：已配置 = 方言支持 nowait 锁选项
     * 且生成器输出该文本（AstToSqlGenerator.printForUpdate 消费 SqlQuerySelect.nowait）；
     * 未配置 = 编译期响亮报错。继承链显式关断 = `<forUpdateNowait x:override="replace"/>`
     * （加载后为 null、与未配置同口径；mysql5.7 继承 mysql、duckdb 继承 postgresql 的
     * 关断面即用此形态）。
     */
    private String forUpdateNowait;

    public DialectSqls() {

    }

    public String getForUpdateNowait() {
        return forUpdateNowait;
    }

    public void setForUpdateNowait(String forUpdateNowait) {
        this.forUpdateNowait = forUpdateNowait;
    }

    public String getSqlIntervalLiteral() {
        return sqlIntervalLiteral;
    }

    public void setSqlIntervalLiteral(String sqlIntervalLiteral) {
        this.sqlIntervalLiteral = sqlIntervalLiteral;
    }

    public String getIntersectKeyword() {
        return intersectKeyword;
    }

    public void setIntersectKeyword(String intersectKeyword) {
        this.intersectKeyword = intersectKeyword;
    }
}
