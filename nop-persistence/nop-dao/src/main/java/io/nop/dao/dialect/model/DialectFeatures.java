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
}
