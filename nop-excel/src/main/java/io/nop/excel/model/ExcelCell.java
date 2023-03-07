/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.excel.model;

import io.nop.excel.model._gen._ExcelCell;

public class ExcelCell extends _ExcelCell {

    public ExcelCell() {

    }

    public String toString() {
        return getClass().getSimpleName() + "[text=" + getText() + ",loc=" + getLocation() + "]";
    }

    public XptCellModel makeModel() {
        XptCellModel model = getModel();
        if (model == null) {
            model = new XptCellModel();
            setModel(model);
        }
        return model;
    }

    @Override
    public ExcelCell cloneInstance() {
        ExcelCell cell = new ExcelCell();
        cell.setLocation(getLocation());
        cell.setStyleId(getStyleId());
        cell.setComment(getComment());
        cell.setType(getType());
        cell.setValue(getValue());
        cell.setFormula(getFormula());
        cell.setRichText(getRichText());
        cell.setMergeAcross(getMergeAcross());
        cell.setMergeDown(getMergeDown());
        cell.setModel(getModel());
        cell.setId(getId());
        return cell;
    }
}
