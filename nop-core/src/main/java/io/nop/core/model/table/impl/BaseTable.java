/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.model.table.impl;

import io.nop.core.lang.json.IJsonHandler;

import java.util.ArrayList;
import java.util.List;

public class BaseTable extends AbstractTable<BaseRow> {

    private static final long serialVersionUID = -4662561617378749671L;

    private List<BaseRow> rows;
    private List<BaseColumnConfig> cols = new ArrayList<>();
    private Object model;

    public BaseTable(int rowCount) {
        this.rows = new ArrayList<>(rowCount);
    }

    public BaseTable() {
        this.rows = new ArrayList<>();
    }

    public Object getModel() {
        return model;
    }

    public void setModel(Object model) {
        this.model = model;
    }

    @Override
    public List<BaseColumnConfig> getCols() {
        return cols;
    }

    public void setCols(List<BaseColumnConfig> cols) {
        this.cols = cols;
    }

    @Override
    public List<BaseRow> getRows() {
        return rows;
    }

    @Override
    public BaseRow newRow() {
        return new BaseRow();
    }

    @Override
    protected void outputJson(IJsonHandler handler) {
        super.outputJson(handler);

        handler.put("cols", this.getCols());
        handler.put("rows", this.getRows());
    }
}