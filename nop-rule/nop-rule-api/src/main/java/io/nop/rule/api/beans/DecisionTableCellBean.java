/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.rule.api.beans;

import io.nop.api.core.annotations.data.DataBean;

import java.util.List;

@DataBean
public class DecisionTableCellBean {
    private int rowIndex;
    private int colIndex;

    private List<RuleOutputBean> outputs;

    public int getRowIndex() {
        return rowIndex;
    }

    public void setRowIndex(int rowIndex) {
        this.rowIndex = rowIndex;
    }

    public int getColIndex() {
        return colIndex;
    }

    public void setColIndex(int colIndex) {
        this.colIndex = colIndex;
    }

    public List<RuleOutputBean> getOutputs() {
        return outputs;
    }

    public void setOutputs(List<RuleOutputBean> outputs) {
        this.outputs = outputs;
    }
}
