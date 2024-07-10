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
import io.nop.commons.bytes.ByteString;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.DisabledEvalScope;
import io.nop.core.model.table.ICellView;
import io.nop.excel.ExcelConstants;
import io.nop.excel.model.ExcelCell;
import io.nop.excel.model.ExcelImage;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.excel.util.UnitsHelper;
import io.nop.ooxml.common.IOfficePackagePart;
import io.nop.ooxml.xlsx.model.CommentsPart;
import io.nop.ooxml.xlsx.model.WorkbookPart;
import io.nop.ooxml.xlsx.model.XSSFSheetRef;
import io.nop.ooxml.xlsx.model.drawing.DrawingParser;

import java.util.List;
import java.util.function.Predicate;

import static io.nop.ooxml.xlsx.XlsxErrors.ARG_REL_ID;
import static io.nop.ooxml.xlsx.XlsxErrors.ARG_TYPE;
import static io.nop.ooxml.xlsx.XlsxErrors.ERR_XLSX_NULL_REL_PART;

public class ExcelWorkbookParser extends AbstractXlsxParser {
    private boolean includeImages = true;
    private boolean includeComments = true;
    private Predicate<String> sheetFilter;

    public ExcelWorkbookParser includeImages(boolean b) {
        this.includeImages = b;
        return this;
    }

    public ExcelWorkbookParser includeComments(boolean b) {
        this.includeComments = b;
        return this;
    }

    public ExcelWorkbookParser sheetFilter(Predicate<String> sheetFilter) {
        this.sheetFilter = sheetFilter;
        return this;
    }

    @Override
    protected ExcelSheet parseSheet(ExcelWorkbook workbook, XSSFSheetRef sheetRef, WorkbookPart workbookFile) {
        if (sheetFilter != null && !sheetFilter.test(sheetRef.getName()))
            return null;

        IOfficePackagePart sheetPart = pkg.getRelPart(workbookFile, sheetRef.getRelId());
        if (sheetPart == null)
            throw new NopException(ERR_XLSX_NULL_REL_PART).param(ARG_TYPE, "sheet").param(ARG_REL_ID, sheetRef.getRelId());


        SimpleSheetContentsHandler contentsHandler = new SimpleSheetContentsHandler(workbook, sheetRef.getName());

        SheetNodeHandler handler = new SheetNodeHandler(sharedStringsTable, contentsHandler);
        sheetPart.processXml(handler, null);

        ExcelSheet sheet = contentsHandler.getSheet();
        sheet.setLocation(pkg.getLocation());
        sheet.setDefaultColumnWidth(ExcelConstants.DEFAULT_COL_WIDTH * UnitsHelper.DEFAULT_CHARACTER_WIDTH_IN_PT);

        if (includeComments) {
            CommentsPart comments = pkg.getCommentsTable(sheetPart);
            if (comments != null) {
                comments.forEachComment((cellPos, comment) -> {
                    ICellView cell = sheet.getTable().getCell(cellPos.getRowIndex(), cellPos.getColIndex());
                    if (cell != null) {
                        ExcelCell ec = (ExcelCell) cell.getRealCell();
                        ec.setComment(comment.getComment());
                    } else {
                        ExcelCell ec = new ExcelCell();
                        ec.setLocation(new SourceLocation(workbook.resourcePath(), 0, 0, 0, 0,
                                sheet.getName(), cellPos.toABString(), null));
                        ec.setComment(comment.getComment());
                        sheet.getTable().setCell(cellPos.getRowIndex(), cellPos.getColIndex(), ec);
                    }
                });
            }
        }

        if (includeImages && contentsHandler.getDrawingId() != null) {
            IOfficePackagePart drawing = pkg.getRelPart(sheetPart, contentsHandler.getDrawingId());
            if (drawing != null) {
                List<ExcelImage> images = new DrawingParser().parseDrawing(drawing.loadXml());
                for (ExcelImage image : images) {
                    if (image.getEmbedId() == null)
                        continue;

                    IOfficePackagePart imagePart = pkg.getRelPart(drawing, image.getEmbedId());
                    if (imagePart == null)
                        continue;
                    image.setImgType(StringHelper.fileExt(imagePart.getPath()));
                    image.setData(ByteString.of(imagePart.generateBytes(DisabledEvalScope.INSTANCE)));
                    image.calcSize(sheet);
                }
                sheet.setImages(images);
            }
        }

        return sheet;
    }

}