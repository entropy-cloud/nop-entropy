/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.excel.imp.model;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.INeedInit;
import io.nop.excel.imp.model._gen._ImportModel;

import java.util.LinkedHashMap;
import java.util.Map;

import static io.nop.excel.ExcelErrors.ARG_ALIAS;
import static io.nop.excel.ExcelErrors.ARG_NEW_NAME;
import static io.nop.excel.ExcelErrors.ARG_OLD_NAME;
import static io.nop.excel.ExcelErrors.ERR_IMP_DUPLICATE_SHEET_ALIAS;

public class ImportModel extends _ImportModel implements INeedInit {
    private final Map<String, ImportSheetModel> sheetMap = new LinkedHashMap<>();

    public ImportModel() {

    }

    public Map<String, ImportSheetModel> getSheetMap() {
        return sheetMap;
    }

    public ImportSheetModel getSheetByNameOrAlias(String name) {
        return sheetMap.get(name);
    }

    @Override
    public void init() {
        getSheets().forEach(ImportSheetModel::init);

        sheetMap.clear();
        getSheets().forEach(sheet -> {
            sheetMap.put(sheet.getName(), sheet);
            if (sheet.getAlias() != null)
                for (String sheetName : sheet.getAlias()) {
                    ImportSheetModel oldSheet = sheetMap.put(sheetName, sheet);
                    if (oldSheet != null)
                        throw new NopException(ERR_IMP_DUPLICATE_SHEET_ALIAS)
                                .source(sheet)
                                .param(ARG_OLD_NAME, oldSheet.getName())
                                .param(ARG_NEW_NAME, sheet.getName())
                                .param(ARG_ALIAS, sheetName);
                }
        });

        if (isDefaultStripText()) {
            for (ImportSheetModel sheet : getSheets()) {
                sheet.initStripText(true);
            }
        }
    }
}
