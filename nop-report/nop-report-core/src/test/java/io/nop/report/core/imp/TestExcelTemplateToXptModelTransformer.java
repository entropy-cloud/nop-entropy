/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.core.imp;

import io.nop.excel.imp.model.ImportFieldModel;
import io.nop.excel.model.ExcelCell;
import io.nop.excel.model.ExcelRow;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelTable;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class TestExcelTemplateToXptModelTransformer {

    /**
     * 列表区域末行之后没有单元格（稀疏表格）时，endList不应抛NPE
     */
    @Test
    public void testEndListWithMissingCellAfterListRegion() {
        ExcelSheet sheet = new ExcelSheet();
        sheet.setName("S1");
        ExcelTable table = new ExcelTable();

        // 表格只有一行表头，列表区域结束后没有更多单元格
        ExcelRow row = new ExcelRow();
        ExcelCell headerCell = new ExcelCell();
        row.setCells(new ArrayList<>(List.of(headerCell)));
        table.setRows(new ArrayList<>(List.of(row)));
        sheet.setTable(table);

        ExcelTemplateToXptModelTransformer.BuildXptModelListener listener =
                new ExcelTemplateToXptModelTransformer.BuildXptModelListener(sheet,
                        XLang.newCompileTool().allowUnregisteredScopeVar(true));

        ImportFieldModel fieldModel = new ImportFieldModel();
        fieldModel.setName("items");
        fieldModel.setList(true);

        listener.beginList(0, 0, 0, 0, fieldModel, false);
        // 之前这里会因为getCell返回null而抛NPE
        listener.endList(0, 0, fieldModel);
    }
}
