///**
// * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
// * Author: canonical_entropy@163.com
// * Blog:   https://www.zhihu.com/people/canonical-entropy
// * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
// * Github: https://github.com/entropy-cloud/nop-entropy
// */
//package io.nop.report.pdf.renderer;
//
//import com.lowagie.text.Cell;
//import com.lowagie.text.Document;
//import com.lowagie.text.Font;
//import com.lowagie.text.Paragraph;
//import com.lowagie.text.Rectangle;
//import com.lowagie.text.Table;
//import com.lowagie.text.pdf.PdfWriter;
//import io.nop.api.core.time.CoreMetrics;
//import io.nop.api.core.util.ProcessResult;
//import io.nop.core.context.IEvalContext;
//import io.nop.core.model.table.ICellView;
//import io.nop.core.model.table.IRowView;
//import io.nop.core.model.table.ITableView;
//import io.nop.core.resource.tpl.IBinaryTemplateOutput;
//import io.nop.excel.model.ExcelBorderStyle;
//import io.nop.excel.model.ExcelPageSetup;
//import io.nop.excel.model.ExcelStyle;
//import io.nop.excel.model.ExcelWorkbook;
//import io.nop.excel.model.IExcelSheet;
//import io.nop.excel.model.color.ColorHelper;
//import io.nop.excel.model.constants.ExcelPaperSize;
//import io.nop.ooxml.xlsx.output.IExcelSheetGenerator;
//import io.nop.report.pdf.font.FontManager;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.awt.*;
//import java.io.IOException;
//import java.io.OutputStream;
//import java.util.HashMap;
//import java.util.Map;
//
//public class PdfReportRenderer implements IBinaryTemplateOutput {
//    static final Logger LOG = LoggerFactory.getLogger(PdfReportRenderer.class);
//
//    private final ExcelWorkbook model;
//    private final IExcelSheetGenerator sheetGenerator;
//
//    private PdfWriter out;
//    private Document doc;
//
//    private final Map<String, Color> colorMap = new HashMap<>();
//
//    public PdfReportRenderer(ExcelWorkbook model, IExcelSheetGenerator sheetGenerator) {
//        this.model = model;
//        this.sheetGenerator = sheetGenerator;
//    }
//
//    @Override
//    public void generateToStream(OutputStream os, IEvalContext context) throws IOException {
//        long beginTime = CoreMetrics.currentTimeMillis();
//        LOG.debug("nop.report.begin-generate-pdf");
//
//        doc = new Document();
//
//        PdfWriter writer = PdfWriter.getInstance(doc, os);
//        this.out = writer;
//        doc.open();
//
//
//        if (sheetGenerator != null) {
//            sheetGenerator.generate(context, this::renderSheet);
//        } else {
//            model.getSheets().forEach(sheet -> {
//                renderSheet(sheet, context);
//            });
//        }
//        out.flush();
//
//        doc.close();
//        out.close();
//
//        long endTime = CoreMetrics.currentTimeMillis();
//        LOG.info("nop.report.end-generate-pdf:usedTime={}", endTime - beginTime);
//    }
//
//    private Color getColor(String color) {
//        Color ret = colorMap.get(color);
//        if (ret == null) {
//            ret = new Color(ColorHelper.toArgbInt(color), true);
//            colorMap.put(color, ret);
//        }
//        return ret;
//    }
//
//    private void renderSheet(IExcelSheet sheet, IEvalContext context) {
//        ITableView table = sheet.getTable();
//        if (table.getRowCount() == 0)
//            return;
//
//        doc.setPageSize(getPageSize(sheet.getPageSetup()));
//        doc.newPage();
//        Table pTable = new Table(table.getColCount(), table.getRowCount());
//        int row = table.getRowCount();
//        int colCount = table.getColCount();
//
//        for (int i = 0; i < row; i++) {
//            renderRow(table.getRow(i), pTable, context, table, i, colCount);
//        }
//
//        doc.add(pTable);
//    }
//
//    private Rectangle getPageSize(ExcelPageSetup pageSetup) {
//        ExcelPaperSize paperSize = pageSetup == null ? ExcelPaperSize.DEFAULT : ExcelPaperSize.of(pageSetup.getPaperSize());
//        boolean hor = pageSetup != null && Boolean.TRUE.equals(pageSetup.getOrientationHorizontal());
//        return ExcelPdfHelper.getPageSize(paperSize, hor);
//    }
//
//    private float[] getColWidths(ITableView table, float totalWidth) {
//        int col = table.getColCount();
//        float[] widths = new float[col];
//        float curSize = 0f;
//        for (int i = 0; i < col; i++) {
//            Double size = table.getColWidth(i);
//            if (size == null)
//                size = 140D;
//            curSize += size.floatValue();
//            widths[i] = size.floatValue();
//        }
//
//        if (totalWidth > 0 && curSize > 0) {
//            for (int i = 0; i < col; i++) {
//                widths[i] = widths[i] * totalWidth / curSize;
//            }
//        }
//        return widths;
//    }
//
//    private void renderRow(IRowView row, Table pTable, IEvalContext context,
//                           ITableView table, int rowIndex, int colCount) {
//
//        row.forEachCell(rowIndex, (cell, i, j) -> {
//            Cell pCell;
//            if (cell == null) {
//                pCell = new Cell();
//                pCell.disableBorderSide(Rectangle.BOX);
//            } else {
//                pCell = renderCell(cell, i, j, context);
//            }
//            pTable.addCell(pCell);
//            return ProcessResult.CONTINUE;
//        });
//    }
//
//    private Cell renderCell(ICellView cell, int rowIndex, int colIndex, IEvalContext context) {
//        ExcelStyle style = model.getStyle(cell.getStyleId());
//        Font font;
//        if (style != null && style.getFont() != null) {
//            font = FontManager.instance().getFont(style.getFont());
//        } else {
//            font = FontManager.instance().getDefaultFont();
//        }
//
//        String value = cell.getText();
//        Cell pCell = new Cell(new Paragraph(value, font));
//
//        //cellStyle
//        pCell.setColspan(cell.getColSpan());
//        pCell.setRowspan(cell.getRowSpan());
//        if (style != null) {
//            setBorder(pCell, style, rowIndex, colIndex, context);
//            if (style.getVerticalAlign() != null) {
//                pCell.setVerticalAlignment(ExcelPdfHelper.getVerticalAlign(style.getVerticalAlign()));
//            }
//            if (style.getHorizontalAlign() != null) {
//                pCell.setHorizontalAlignment(ExcelPdfHelper.getHorizontalAlign(style.getHorizontalAlign()));
//            }
//        }
//        return pCell;
//    }
//
//    private void setBorder(Cell pCell, ExcelStyle style, int rowIndex, int colIndex, IEvalContext context) {
//        //预先判定是否要生成上边框
//        boolean setTop = true;
//        //预先判定是否要生成左边框
//        boolean setLeft = true;
//
//        ExcelBorderStyle bs = style.getTopBorder();
//        if (bs != null && setTop) {
//            pCell.setBorderColorTop(getColor(bs.getColor()));
//            pCell.setBorderWidthTop(bs.getWeight());
//        } else {
//            pCell.disableBorderSide(Rectangle.TOP);
//        }
//        bs = style.getLeftBorder();
//        if (bs != null && setLeft) {
//            pCell.setBorderColorLeft(getColor(bs.getColor()));
//            pCell.setBorderWidthLeft(bs.getWeight());
//        } else {
//            pCell.disableBorderSide(Rectangle.LEFT);
//        }
//        bs = style.getRightBorder();
//        if (bs != null) {
//            pCell.setBorderColorRight(getColor(bs.getColor()));
//            pCell.setBorderWidthRight(bs.getWeight());
//        } else {
//            pCell.disableBorderSide(Rectangle.RIGHT);
//        }
//        bs = style.getBottomBorder();
//        if (bs != null) {
//            pCell.setBorderColorBottom(getColor(bs.getColor()));
//            pCell.setBorderWidthBottom(bs.getWeight());
//        } else {
//            pCell.disableBorderSide(Rectangle.BOTTOM);
//        }
//    }
//}