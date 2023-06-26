/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.excel.model;

import io.nop.api.core.util.INeedInit;
import io.nop.excel.model._gen._ExcelWorkbook;

public class ExcelWorkbook extends _ExcelWorkbook implements INeedInit {
    public ExcelWorkbook() {

    }

    @Override
    public void init() {
        for (ExcelSheet sheet : getSheets()) {
            sheet.init();
        }
    }

    public ExcelSheet removeSheet(String sheetName) {
        ExcelSheet sheet = getSheet(sheetName);
        if (sheet != null) {
            getSheets().remove(sheet);
        }
        return sheet;
    }
}
