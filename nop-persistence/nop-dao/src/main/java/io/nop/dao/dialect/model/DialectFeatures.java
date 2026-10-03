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
}
