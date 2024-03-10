/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.excel.model;

import io.nop.api.core.util.INeedInit;
import io.nop.excel.model._gen._ExcelSheet;

import java.util.ArrayList;

public class ExcelSheet extends _ExcelSheet implements IExcelSheet, INeedInit {
    public ExcelSheet() {
        setTable(new ExcelTable());
    }

    public String toString() {
        return getClass().getSimpleName() + "[name=" + getName() + ",loc=" + getLocation() + "]";
    }

    public ExcelSheet cloneInstance() {
        ExcelSheet sheet = new ExcelSheet();
        sheet.setLocation(getLocation());
        sheet.setName(getName());
        sheet.setSheetOptions(getSheetOptions());
        sheet.setAnnotations(new ArrayList<>(getAnnotations()));
        sheet.setImages(new ArrayList<>(getImages()));
        sheet.setConditionalStyles(new ArrayList<>(getConditionalStyles()));
        sheet.setModel(getModel());
        sheet.setPageBreaks(getPageBreaks());
        sheet.setTable(getTable().cloneInstance());
        return sheet;
    }

    @Override
    public void init() {
        getTable().init();
    }
}
