/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.ooxml.xlsx.parse;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.IoHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.component.parse.AbstractResourceParser;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.ooxml.common.IOfficePackagePart;
import io.nop.ooxml.xlsx.XSSFRelation;
import io.nop.ooxml.xlsx.model.CommentsPart;
import io.nop.ooxml.xlsx.model.ExcelOfficePackage;
import io.nop.ooxml.xlsx.model.SharedStringsPart;
import io.nop.ooxml.xlsx.model.WorkbookPart;
import io.nop.ooxml.xlsx.model.XSSFSheetRef;

public abstract class AbstractXlsxParser extends AbstractResourceParser<ExcelWorkbook> {

    protected ExcelOfficePackage pkg;
    protected SharedStringsPart sharedStringsTable;
    protected WorkbookPart workbookPart;

    @Override
    protected ExcelWorkbook doParseResource(IResource resource) {
        ExcelOfficePackage pkg = new ExcelOfficePackage();
        try {
            pkg.loadFromResource(resource);
            pkg.setLocation(SourceLocation.fromPath(resource.getPath()));
            return parseFromPkg(pkg);
        } finally {
            IoHelper.safeClose(pkg);
        }
    }

    private ExcelWorkbook parseFromPkg(ExcelOfficePackage pkg) {
        this.pkg = pkg;
        IOfficePackagePart part = pkg.getPartByContentType(XSSFRelation.SHARED_STRINGS.getType());
        sharedStringsTable = part == null ? null : new SharedStringsTableParser(true).parseFromPart(part);

        workbookPart = pkg.getWorkbook();

        ExcelWorkbook wk = new ExcelWorkbook();
        wk.setLocation(pkg.getLocation());
        wk.setStyles(pkg.getStyles().getStyles());
        for (XSSFSheetRef sheetRef : workbookPart.getSheets()) {
            ExcelSheet sheet = parseSheet(wk, sheetRef, workbookPart);
            wk.addSheet(sheet);
        }
        return wk;
    }

    protected abstract ExcelSheet parseSheet(ExcelWorkbook workbook, XSSFSheetRef sheetRef, WorkbookPart workbookFile);

    protected CommentsPart getCommentsTable(IOfficePackagePart sheetPart) {
        IOfficePackagePart commentsPart = pkg.getRelPartByType(sheetPart, XSSFRelation.SHEET_COMMENTS.getRelation());
        if (commentsPart == null)
            return null;
        return CommentsPart.parse(commentsPart);
    }
}
