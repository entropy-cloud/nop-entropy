/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.dialect.model;

import io.nop.dao.dialect.model._gen._DialectFeatures;

public class DialectFeatures extends _DialectFeatures {
    private Boolean supportWindowFrameRows;
    private Boolean supportWindowFrameRange;
    private Boolean supportWindowFrameGroups;
    private Boolean supportLateral;

    // WI12 交付⑤（plan 38）：FROM (VALUES …) 表源能力位（javadoc 见 getter）
    private Boolean supportValuesTableSource;
    // WI7b（plan 37）：GROUP BY 位置 grouping 操作符 ROLLUP/CUBE 能力位——分立两标志，
    // mysql 函数式 ROLLUP（8.4.3+ 超基线）与 CUBE（永无）successor 触发条件不同（裁定文档 §3.2）。
    private Boolean supportRollup;
    private Boolean supportCube;

    // Plan 2380 Phase 2：EQL upsert 语句能力位。缺省（未配置）= null，
    // 由 IDialect.isSupportUpsert() 归一为 false（保守缺省，编译期报错）。
    private Boolean supportUpsert;
    // Plan 2380 Phase 2：INSERT / DELETE 上的投影列表形式 RETURNING 能力位。
    // 与 supportReturningForUpdate **分立**：三个标志各自的方言支持集合不同，
    // 且既有 supportReturningForUpdate 的方言取值不得因本次扩展而改变（BC）。
    private Boolean supportReturningForInsert;
    private Boolean supportReturningForDelete;

    public DialectFeatures() {

    }

    /**
     * WI2: 窗口 frame 三能力位。nop-dao 的 precompile 生成管线未接线（见 plan 07），
     * 属性 setter/getter 先落在保留类上；xdef 绑定按属性名反射 setter，可正常生效。
     * 未来启用生成管线后可迁入 _DialectFeatures 并删除本类字段。
     */
    public Boolean getSupportWindowFrameRows() {
        return supportWindowFrameRows;
    }

    public void setSupportWindowFrameRows(Boolean supportWindowFrameRows) {
        this.supportWindowFrameRows = supportWindowFrameRows;
    }

    public Boolean getSupportWindowFrameRange() {
        return supportWindowFrameRange;
    }

    public void setSupportWindowFrameRange(Boolean supportWindowFrameRange) {
        this.supportWindowFrameRange = supportWindowFrameRange;
    }

    public Boolean getSupportWindowFrameGroups() {
        return supportWindowFrameGroups;
    }

    public void setSupportWindowFrameGroups(Boolean supportWindowFrameGroups) {
        this.supportWindowFrameGroups = supportWindowFrameGroups;
    }

    /**
     * WI1 BUG-2: LATERAL 子查询表源能力位。同窗口 frame 三能力位：生成管线未接线，
     * 手写 retention 字段 + xdef 按属性名反射 setter。缺省（未配置）= null，
     * 由 IDialect.isSupportLateral() 归一为 false（保守缺省，编译期报错）。
     */
    public Boolean getSupportLateral() {
        return supportLateral;
    }

    public void setSupportLateral(Boolean supportLateral) {
        this.supportLateral = supportLateral;
    }

    /**
     * WI7b: ROLLUP/CUBE 能力位（与 supportLateral 同款保留类手法——nop-dao precompile
     * 生成管线未接线，xdef 绑定按属性名反射 setter）。未配置由 IDialect.isSupportRollup()/
     * isSupportCube() 归一为 false（保守缺省，编译期报错）。
     */
    public Boolean getSupportRollup() {
        return supportRollup;
    }

    public void setSupportRollup(Boolean supportRollup) {
        this.supportRollup = supportRollup;
    }

    public Boolean getSupportCube() {
        return supportCube;
    }

    public void setSupportCube(Boolean supportCube) {
        this.supportCube = supportCube;
    }

    /**
     * WI12 交付⑤（plan 38，裁定
     * ai-dev/design/eql-cross-db-compat/2026-10-05-wi12-phase0-adjudications.md §4）：
     * {@code FROM (VALUES …)} 表源能力位（与 supportLateral 同款保留类手法——nop-dao
     * precompile 生成管线未接线，xdef 绑定按属性名反射 setter）。未配置由
     * {@code IDialect.isSupportValuesTableSource()} 归一为 false（保守缺省，编译期报错）。
     */
    public Boolean getSupportValuesTableSource() {
        return supportValuesTableSource;
    }

    public void setSupportValuesTableSource(Boolean supportValuesTableSource) {
        this.supportValuesTableSource = supportValuesTableSource;
    }

    /**
     * Plan 2380 Phase 2：EQL {@code upsert} 语句能力位（与 {@code supportLateral} 同款
     * 保留类手法——nop-dao precompile 生成管线未接线，xdef 绑定按属性名反射 setter）。
     * 未配置由 {@code IDialect.isSupportUpsert()} 归一为 false（保守缺省，编译期报错）。
     *
     * <p>取值必须与 {@code UpsertStatements.resolveUpsertStyle} 的结论一致：形态解析器
     * 返回 null 的方言本标志必须为 false，否则会出现"能力位说支持、SQL 生成器无对应
     * 分支"的静默不一致。
     */
    public Boolean getSupportUpsert() {
        return supportUpsert;
    }

    public void setSupportUpsert(Boolean supportUpsert) {
        this.supportUpsert = supportUpsert;
    }

    /**
     * Plan 2380 Phase 2：{@code INSERT} 上返回投影列表形式（无 INTO 宿主变量）的
     * {@code RETURNING} 能力位。语义边界与 {@code supportReturningForUpdate} 同款，
     * 但两者是分立标志——不要用"该库有没有 RETURNING 子句"来理解它们。
     */
    public Boolean getSupportReturningForInsert() {
        return supportReturningForInsert;
    }

    public void setSupportReturningForInsert(Boolean supportReturningForInsert) {
        this.supportReturningForInsert = supportReturningForInsert;
    }

    /** Plan 2380 Phase 2：{@code DELETE} 上的投影列表形式 {@code RETURNING} 能力位。 */
    public Boolean getSupportReturningForDelete() {
        return supportReturningForDelete;
    }

    public void setSupportReturningForDelete(Boolean supportReturningForDelete) {
        this.supportReturningForDelete = supportReturningForDelete;
    }
}
