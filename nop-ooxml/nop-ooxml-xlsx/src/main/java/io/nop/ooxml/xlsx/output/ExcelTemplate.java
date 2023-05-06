/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.ooxml.xlsx.output;

import io.nop.commons.mutable.MutableInt;
import io.nop.core.context.IEvalContext;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.FileResource;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.excel.model.IExcelSheet;
import io.nop.ooxml.common.IOfficePackagePart;
import io.nop.ooxml.common.OfficeConstants;
import io.nop.ooxml.common.model.ContentTypesPart;
import io.nop.ooxml.common.model.OfficeRelsPart;
import io.nop.ooxml.common.output.AbstractOfficeTemplate;
import io.nop.ooxml.xlsx.XSSFRelation;
import io.nop.ooxml.xlsx.model.ExcelOfficePackage;
import io.nop.ooxml.xlsx.model.StylesPart;
import io.nop.ooxml.xlsx.model.WorkbookPart;

import java.io.File;

import static io.nop.ooxml.common.model.PackagingURIHelper.createPartName;

public class ExcelTemplate extends AbstractOfficeTemplate {

    private final ExcelWorkbook workbook;
    private final ExcelOfficePackage pkg;
    private final IExcelSheetGenerator sheetGenerator;

    public ExcelTemplate(ExcelOfficePackage pkg, ExcelWorkbook workbook,
                         IExcelSheetGenerator sheetGenerator) {
        this.workbook = workbook;
        this.pkg = pkg;
        this.sheetGenerator = sheetGenerator;
        pkg.loadInMemory();
    }

    public ExcelTemplate(ExcelWorkbook workbook,
                         IExcelSheetGenerator sheetGenerator) {
        this(ExcelOfficePackage.loadEmpty(), workbook, sheetGenerator);
    }

    public ExcelTemplate(ExcelWorkbook workbook) {
        this(workbook, null);
    }

    @Override
    public void generateToDir(File dir, IEvalContext context) {
        ExcelOfficePackage pkg = this.pkg.copy();

        context.getEvalScope().setLocalValue(null, OfficeConstants.VAR_OFC_PKG, pkg);

        pkg.getWorkbook().clearSheets();

        MutableInt index = new MutableInt();

        if (sheetGenerator != null) {
            sheetGenerator.generate(context, sheet -> {
                generateSheet(pkg, dir, index.get(), sheet, context);
                index.incrementAndGet();
            });
        } else if (workbook != null) {
            for (ExcelSheet sheet : workbook.getSheets()) {
                generateSheet(pkg, dir, index.get(), sheet, context);
                index.incrementAndGet();
            }
        }

        if (workbook != null) {
            pkg.addFile(new StylesPart(workbook.getStyles()));
        }
        pkg.generateToDir(dir, context.getEvalScope());
    }

    private void generateSheet(ExcelOfficePackage pkg, File dir, int index, IExcelSheet sheet, IEvalContext context) {
        ContentTypesPart contentTypes = pkg.getContentTypes();
        int sheetId = index + 1;
        String sheetPath = "/xl/worksheets/sheet" + sheetId + ".xml";
        contentTypes.addContentType(createPartName(sheetPath), XSSFRelation.WORKSHEET.getType());

        String commentPath = "/xl/comments" + sheetId + ".xml";
        contentTypes.addContentType(createPartName(commentPath), XSSFRelation.SHEET_COMMENTS.getType());

        WorkbookPart workbook = pkg.getWorkbook();
        OfficeRelsPart rels = pkg.makeRelsForPart(workbook);
        String relPath = "worksheets/sheet" + sheetId + ".xml";
        String relId = rels.addRelationship(XSSFRelation.WORKSHEET.getRelation(), relPath, null);
        workbook.addSheet(relId, sheetId, sheet.getName());

        IResource resource = new FileResource(new File(dir, sheetPath));
        new ExcelSheetWriter(sheet, index == 0).indent(isIndent()).generateToResource(resource, context);
        IOfficePackagePart sheetPart = pkg.addFile(sheetPath, resource);

        IResource commentResource = new FileResource(new File(dir, commentPath));
        new ExcelCommentsWriter(sheet).indent(isIndent()).generateToResource(commentResource, context);
        pkg.addFile(commentPath, commentResource);

        String relCommentsPath = "../comments" + sheetId + ".xml";
        OfficeRelsPart sheetRels = pkg.makeRelsForPart(sheetPart);
        sheetRels.removeRelationshipByType(XSSFRelation.SHEET_COMMENTS.getRelation());
        sheetRels.addRelationship(XSSFRelation.SHEET_COMMENTS.getRelation(), relCommentsPath, null);
    }
}