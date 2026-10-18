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
import io.nop.core.lang.xml.XNode;
import io.nop.core.model.table.ICellView;
import io.nop.excel.model.ExcelCell;
import io.nop.excel.model.ExcelChartModel;
import io.nop.excel.model.ExcelClientAnchor;
import io.nop.excel.print.ExcelPrintHelper;
import io.nop.excel.model.ExcelImage;
import io.nop.excel.model.ExcelPageSetup;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.excel.model.constants.ExcelModelConstants;
import io.nop.ooxml.common.IOfficePackagePart;
import io.nop.ooxml.xlsx.model.CommentsPart;
import io.nop.ooxml.xlsx.model.WorkbookPart;
import io.nop.ooxml.xlsx.model.XSSFSheetRef;
import io.nop.ooxml.xlsx.model.drawing.DrawingParser;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static io.nop.ooxml.xlsx.XlsxErrors.ARG_PRINT_TITLES;
import static io.nop.ooxml.xlsx.XlsxErrors.ARG_REL_ID;
import static io.nop.ooxml.xlsx.XlsxErrors.ARG_TYPE;
import static io.nop.ooxml.xlsx.XlsxErrors.ERR_XLSX_DUP_PRINT_TITLES;
import static io.nop.ooxml.xlsx.XlsxErrors.ERR_XLSX_INVALID_PRINT_TITLES;
import static io.nop.ooxml.xlsx.XlsxErrors.ERR_XLSX_NULL_REL_PART;

public class ExcelWorkbookParser extends AbstractXlsxParser {
    public static final String NAME_PRINT_TITLES = "_xlnm.Print_Titles";

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

        if (StringHelper.isEmpty(sheetRef.getName()))
            return null;

        SimpleSheetContentsHandler contentsHandler = new SimpleSheetContentsHandler(workbook, sheetRef.getName());

        SheetNodeHandler handler = new SheetNodeHandler(sharedStringsTable, contentsHandler);
        sheetPart.processXml(handler, null);

        ExcelSheet sheet = contentsHandler.getSheet();
        sheet.setLocation(pkg.getLocation());
        sheet.setDefaultColumnWidth((double) ExcelModelConstants.DEFAULT_COL_WIDTH_IN_PT);

        applyPrintTitles(sheetRef, workbookFile, sheet);

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
                List<ExcelImage> images = new DrawingParser().parseImages(drawing.loadXml());
                for (ExcelImage image : images) {
                    if (image.getEmbedId() == null)
                        continue;

                    IOfficePackagePart imagePart = pkg.getRelPart(drawing, image.getEmbedId());
                    if (imagePart == null)
                        continue;
                    image.setImgType(StringHelper.fileExt(imagePart.getPath()));
                    image.setData(ByteString.of(imagePart.generateBytes(DisabledEvalScope.INSTANCE)));
                    image.calcSize(sheet);
                    ExcelClientAnchor anchor = image.getAnchor();
                    sheet.getTable().makeCell(anchor.getRow1(), anchor.getCol1());
                    sheet.getTable().makeCell(anchor.getRow2(), anchor.getCol2());
                }
                sheet.setImages(images);

                List<ExcelChartModel> charts = new DrawingParser().parseCharts(drawing.loadXml(), pkg, drawing);
                if (!charts.isEmpty()) {
                    sheet.setCharts(charts);
                }
            }
        }

        return sheet;
    }

    /**
     * 解析 workbook.xml 的 definedNames 中本 sheet 的 _xlnm.Print_Titles（模板级重复标题行/列声明），
     * 归一化为 1-based 闭区间字符串写入 pageSetup（如 "1:2"/"A:B"）。非法形态显式报错，不静默忽略。
     * <p>
     * localSheetId 取 sheetRef 在 workbook 完整 sheet 列表中的下标（OOXML 语义），与 sheetFilter 过滤无关。
     */
    void applyPrintTitles(XSSFSheetRef sheetRef, WorkbookPart workbookFile, ExcelSheet sheet) {
        XNode definedNamesN = workbookFile.getNode().childByTag("definedNames");
        if (definedNamesN == null || definedNamesN.getChildren() == null)
            return;

        int localSheetId = workbookFile.getSheets().indexOf(sheetRef);
        String rowsRange = null;
        String colsRange = null;
        boolean found = false;
        for (XNode dn : definedNamesN.getChildren()) {
            if (dn == null || !"definedName".equals(dn.getTagName()))
                continue;
            if (!NAME_PRINT_TITLES.equals(dn.attrText("name")))
                continue;

            String lsidText = dn.attrText("localSheetId");
            if (lsidText == null)
                throw new NopException(ERR_XLSX_INVALID_PRINT_TITLES)
                        .param(ARG_PRINT_TITLES, dn.getText() + " (missing localSheetId)");
            int lsid = parseIntOrDefault(lsidText, -1);
            if (lsid != localSheetId)
                continue;
            if (found)
                throw new NopException(ERR_XLSX_DUP_PRINT_TITLES).param(ARG_PRINT_TITLES, dn.getText());

            found = true;
            String value = dn.getText();
            List<String[]> ranges = parsePrintTitleSegments(value);
            for (String[] range : ranges) {
                String kind = range[0];
                String normalized = range[1];
                if ("row".equals(kind)) {
                    if (rowsRange != null)
                        throw new NopException(ERR_XLSX_INVALID_PRINT_TITLES).param(ARG_PRINT_TITLES, value);
                    rowsRange = normalized;
                } else {
                    if (colsRange != null)
                        throw new NopException(ERR_XLSX_INVALID_PRINT_TITLES).param(ARG_PRINT_TITLES, value);
                    colsRange = normalized;
                }
            }
        }

        if (rowsRange != null || colsRange != null) {
            ExcelPageSetup pageSetup = sheet.getPageSetup();
            if (pageSetup == null) {
                pageSetup = new ExcelPageSetup();
                sheet.setPageSetup(pageSetup);
            }
            pageSetup.setRepeatTitleRows(rowsRange);
            pageSetup.setRepeatTitleCols(colsRange);
        }
    }

    /**
     * 拆分 Print_Titles value（如 {@code Sheet1!$1:$2,Sheet1!$A:$B}）为归一化段。
     * 每段剥去 sheet 名前缀（含单引号形式）与 {@code $}；纯数字段为行段、纯字母段为列段。
     *
     * @return 每项 {@code [kind, normalized]}，kind 为 "row"/"col"，normalized 为 "1:2"/"A:B"
     */
    static List<String[]> parsePrintTitleSegments(String value) {
        List<String[]> ret = new ArrayList<>();
        if (StringHelper.isEmpty(value))
            throw new NopException(ERR_XLSX_INVALID_PRINT_TITLES).param(ARG_PRINT_TITLES, value);
        for (String seg : value.split(",", -1)) {
            String s = seg.trim();
            if (s.isEmpty())
                throw new NopException(ERR_XLSX_INVALID_PRINT_TITLES).param(ARG_PRINT_TITLES, value);

            int bang = s.lastIndexOf('!');
            String ref = bang >= 0 ? s.substring(bang + 1).trim() : s;
            ref = ref.replace("$", "");

            int colon = ref.indexOf(':');
            if (colon <= 0 || colon == ref.length() - 1)
                throw new NopException(ERR_XLSX_INVALID_PRINT_TITLES).param(ARG_PRINT_TITLES, value);

            String a = ref.substring(0, colon).trim();
            String b = ref.substring(colon + 1).trim();
            if (isDigits(a) && isDigits(b)) {
                ret.add(new String[]{"row", normalizeRange(a, b)});
            } else if (isLetters(a) && isLetters(b)) {
                ret.add(new String[]{"col", normalizeColRange(a.toUpperCase(), b.toUpperCase())});
            } else {
                // 单元格区域（如 $A$1:$B$2）等多形态不支持
                throw new NopException(ERR_XLSX_INVALID_PRINT_TITLES).param(ARG_PRINT_TITLES, value);
            }
        }
        return ret;
    }

    private static int parseIntOrDefault(String s, int dft) {
        if (StringHelper.isEmpty(s))
            return dft;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return dft;
        }
    }

    private static String normalizeRange(String a, String b) {
        if (Integer.parseInt(a) > Integer.parseInt(b))
            throw new NopException(ERR_XLSX_INVALID_PRINT_TITLES).param(ARG_PRINT_TITLES, a + ":" + b);
        return a + ":" + b;
    }

    private static String normalizeColRange(String a, String b) {
        if (ExcelPrintHelper.colLettersToIndex(a) > ExcelPrintHelper.colLettersToIndex(b))
            throw new NopException(ERR_XLSX_INVALID_PRINT_TITLES).param(ARG_PRINT_TITLES, a + ":" + b);
        return a + ":" + b;
    }

    private static boolean isDigits(String s) {
        if (s.isEmpty())
            return false;
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i)))
                return false;
        }
        return true;
    }

    private static boolean isLetters(String s) {
        if (s.isEmpty())
            return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')))
                return false;
        }
        return true;
    }

}