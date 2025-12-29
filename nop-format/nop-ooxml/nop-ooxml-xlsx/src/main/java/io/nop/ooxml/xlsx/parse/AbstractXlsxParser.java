/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.xlsx.parse;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.IoHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.component.parse.AbstractResourceParser;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.ooxml.common.IOfficePackagePart;
import io.nop.ooxml.xlsx.XSSFRelation;
import io.nop.ooxml.xlsx.model.ExcelOfficePackage;
import io.nop.ooxml.xlsx.model.SharedStringsPart;
import io.nop.ooxml.xlsx.model.WorkbookPart;
import io.nop.ooxml.xlsx.model.XSSFSheetRef;

import java.io.Closeable;

import static io.nop.ooxml.xlsx.XlsxErrors.ARG_SHEET_NAME;
import static io.nop.ooxml.xlsx.XlsxErrors.ERR_XLSX_UNKNOWN_SHEET_NAME;

public abstract class AbstractXlsxParser extends AbstractResourceParser<ExcelWorkbook> implements Closeable {

    protected ExcelOfficePackage pkg;
    protected SharedStringsPart sharedStringsTable;
    protected WorkbookPart workbookPart;

    protected ExcelWorkbook wk;

    @Override
    protected ExcelWorkbook doParseResource(IResource resource) {
        ExcelOfficePackage pkg = loadFromResource(resource);
        try {
            for (XSSFSheetRef sheetRef : workbookPart.getSheets()) {
                ExcelSheet sheet = parseSheet(wk, sheetRef, workbookPart);
                if (sheet != null)
                    wk.addSheet(sheet);
            }
            endParseWorkbook(wk);
            return wk;
        } finally {
            IoHelper.safeCloseObject(pkg);
        }
    }

    public void close() {
        pkg.close();
    }

    public ExcelOfficePackage loadFromResource(IResource resource) {
        ExcelOfficePackage pkg = new ExcelOfficePackage();
        pkg.loadFromResource(resource);
        pkg.setLocation(SourceLocation.fromPath(resource.getPath()));
        this.pkg = pkg;

        IOfficePackagePart part = pkg.getPartByContentType(XSSFRelation.SHARED_STRINGS.getType());
        sharedStringsTable = part == null ? null : new SharedStringsTableParser(true).parseFromPart(part);

        workbookPart = pkg.getWorkbook();

        this.wk = new ExcelWorkbook();

        wk.setLocation(pkg.getLocation());
        wk.setStyles(pkg.getStyles().getStyles());
        return pkg;
    }

    protected void endParseWorkbook(ExcelWorkbook wk) {
        wk.init();
    }

    protected abstract ExcelSheet parseSheet(ExcelWorkbook workbook, XSSFSheetRef sheetRef, WorkbookPart workbookFile);

    public ExcelSheet parseSheet(String sheetName) {
        XSSFSheetRef sheetRef = sheetName == null ? workbookPart.getFirstSheet() : workbookPart.requireSheetByName(sheetName);
        if (sheetRef == null)
            throw new NopException(ERR_XLSX_UNKNOWN_SHEET_NAME).source(pkg).param(ARG_SHEET_NAME, sheetName);
        return parseSheet(wk, sheetRef, workbookPart);
    }
}
