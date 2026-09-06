package io.nop.report.pdf.renderer;

import io.nop.api.core.beans.geometry.RectangleBean;
import io.nop.api.core.beans.geometry.SizeBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.IoHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.model.table.CellPosition;
import io.nop.core.model.table.CellRange;
import io.nop.core.model.table.ITableView;
import io.nop.core.model.table.utils.TableSplitHelper;
import io.nop.excel.model.ExcelFont;
import io.nop.excel.model.ExcelHeaderFooter;
import io.nop.excel.model.ExcelImage;
import io.nop.excel.model.ExcelPageSetup;
import io.nop.excel.model.ExcelStyle;
import io.nop.excel.model.IExcelSheet;
import io.nop.excel.model.IExcelStyleProvider;
import io.nop.excel.print.ExcelPrintHelper;
import io.nop.report.pdf.ReportPdfConfigs;
import io.nop.report.pdf.utils.PdfImageHelper;
import io.nop.report.pdf.utils.PdfPrintHelper;
import io.nop.report.pdf.utils.PdfStyleHelper;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.util.Matrix;

import java.io.IOException;
import java.util.List;

public class PdfSheetRenderer {
    private final PdfRenderer renderer;
    private final IExcelStyleProvider styleProvider;

    public PdfSheetRenderer(PdfRenderer renderer, IExcelStyleProvider styleProvider) {
        this.renderer = renderer;
        this.styleProvider = styleProvider;
    }

    public void renderSheet(IExcelSheet sheet) {
        ExcelPageSetup pageSetup = sheet.getPageSetup();
        SizeBean pagerSize = ExcelPrintHelper.getPaperSize(pageSetup);
        RectangleBean printArea = ExcelPrintHelper.calculatePrintArea(pagerSize, sheet.getPageMargins());

        if (pageSetup != null && pageSetup.isFitToWidthAndHeight()) {
            renderSheet(sheet, pagerSize, printArea, null);
            return;
        }

        boolean enableColumnPaging = pageSetup == null || !Boolean.FALSE.equals(pageSetup.getFitToWidth());

        ITableView table = sheet.getTable();

        // 第一遍：按完整打印区拆分。单页能放下则不做任何重复
        List<CellRange> tableRegions = TableSplitHelper.splitTable(table,
                printArea.getWidth(), printArea.getHeight(),
                sheet.defaultColumnWidth(), sheet.defaultRowHeight(), enableColumnPaging);
        if (tableRegions.size() <= 1) {
            renderSheet(sheet, pagerSize, printArea, null);
            return;
        }

        // 需要分页：为续页的重复标题块预留空间后重新拆分，避免页尾内容溢出
        int repeatRows = repeatHeaderRows(sheet);
        int repeatCols = repeatKeyColumns(sheet);
        double repeatHeight = repeatRows > 0 ? table.getRangeHeight(0, repeatRows - 1, sheet.defaultRowHeight()) : 0;
        double repeatWidth = repeatCols > 0 ? table.getRangeWidth(0, repeatCols - 1, sheet.defaultColumnWidth()) : 0;
        if (repeatHeight > 0 || repeatWidth > 0) {
            tableRegions = TableSplitHelper.splitTable(table,
                    Math.max(50, printArea.getWidth() - repeatWidth),
                    Math.max(50, printArea.getHeight() - repeatHeight),
                    sheet.defaultColumnWidth(), sheet.defaultRowHeight(), enableColumnPaging);
        }

        for (CellRange region : tableRegions) {
            renderSheet(sheet, pagerSize, printArea, region);
        }
    }

    private double repeatRowsHeight(IExcelSheet sheet, CellRange region) {
        int k = Math.min(repeatHeaderRows(sheet), region.getFirstRowIndex());
        return k > 0 ? sheet.getTable().getRangeHeight(0, k - 1, sheet.defaultRowHeight()) : 0;
    }

    private double repeatColsWidth(IExcelSheet sheet, CellRange region) {
        int m = Math.min(repeatKeyColumns(sheet), region.getFirstColIndex());
        return m > 0 ? sheet.getTable().getRangeWidth(0, m - 1, sheet.defaultColumnWidth()) : 0;
    }

    private int repeatHeaderRows(IExcelSheet sheet) {
        int n = Math.max(0, ReportPdfConfigs.CFG_PDF_REPEAT_HEADER_ROWS.get());
        return Math.min(n, Math.max(0, sheet.getTable().getRowCount() - 1));
    }

    private int repeatKeyColumns(IExcelSheet sheet) {
        int n = Math.max(0, ReportPdfConfigs.CFG_PDF_REPEAT_KEY_COLUMNS.get());
        return Math.min(n, Math.max(0, sheet.getTable().getColCount() - 1));
    }

    public void renderSheet(IExcelSheet sheet, SizeBean paperSize, RectangleBean printArea, CellRange region) {
        ITableView table;
        if (region == null) {
            table = sheet.getTable();
        } else {
            table = sheet.getTable().getSubTable(region).clip();
        }

        // 续页重复标题块：行带续页重复表头行，列带续页重复关键列（F3/F4）；
        // 行列同时拆分的内页还需角块（表头行×关键列），否则左上角空缺且表头与内容列错位dx
        ITableView repeatRowsTable = null;
        ITableView repeatColsTable = null;
        ITableView cornerTable = null;
        if (region != null) {
            int repeatRows = repeatHeaderRows(sheet);
            int repeatCols = repeatKeyColumns(sheet);
            boolean rowRepeat = repeatRows > 0 && region.getFirstRowIndex() > 0;
            boolean colRepeat = repeatCols > 0 && region.getFirstColIndex() > 0;
            if (rowRepeat) {
                int k = Math.min(repeatRows, region.getFirstRowIndex());
                repeatRowsTable = sheet.getTable()
                        .getSubTable(new CellRange(0, region.getFirstColIndex(), k - 1, region.getLastColIndex())).clip();
            }
            if (colRepeat) {
                int m = Math.min(repeatCols, region.getFirstColIndex());
                repeatColsTable = sheet.getTable()
                        .getSubTable(new CellRange(region.getFirstRowIndex(), 0, region.getLastRowIndex(), m - 1)).clip();
            }
            if (rowRepeat && colRepeat) {
                int k = Math.min(repeatRows, region.getFirstRowIndex());
                int m = Math.min(repeatCols, region.getFirstColIndex());
                cornerTable = sheet.getTable()
                        .getSubTable(new CellRange(0, 0, k - 1, m - 1)).clip();
            }
        }

        PdfPageRenderer pageRenderer = null;
        try {
            pageRenderer = renderer.addPage(PdfPrintHelper.toRectangle(paperSize));
            float pageHeight = (float) paperSize.getHeight();

            // 渲染页眉页脚
            renderHeaderFooter(pageRenderer, sheet, printArea, pageHeight);
            // 页脚已由模板显式配置时无需默认页码
            ExcelPageSetup pageSetup = sheet.getPageSetup();
            if (pageSetup != null && pageSetup.getFooter() != null) {
                renderer.markFooterDrawn();
            }

            // 重复块占位尺寸需先于applyPageSetup计算，fit缩放/居中要把dx/dy计入
            double dx = repeatColsTable != null ? repeatColsWidth(sheet, region) : 0;
            double dy = repeatRowsTable != null ? repeatRowsHeight(sheet, region) : 0;

            // 应用页面设置，并取得设备页矩形在当前用户空间的裁剪矩形（供图片裁剪）
            java.awt.Rectangle pageClipRect = applyPageSetup(pageRenderer, sheet, table, printArea, pageHeight, dx, dy);

            // 四块网格布局：角块(表头×关键列)在原点，表头行块在x=dx，关键列块在y=-dy，
            // 内容在(dx,-dy)。重复块用save/restore包裹避免CTM残留，内容块的累积平移
            // 保持到renderImages（其region偏移坐标依赖同一变换空间）
            PDPageContentStream cs = pageRenderer.getContentStream();

            if (cornerTable != null) {
                renderTable(pageRenderer, sheet, cornerTable);
            }

            if (repeatRowsTable != null) {
                cs.saveGraphicsState();
                cs.transform(Matrix.getTranslateInstance((float) dx, 0));
                renderTable(pageRenderer, sheet, repeatRowsTable);
                cs.restoreGraphicsState();
            }

            if (repeatColsTable != null) {
                cs.saveGraphicsState();
                cs.transform(Matrix.getTranslateInstance(0, (float) -dy));
                renderTable(pageRenderer, sheet, repeatColsTable);
                cs.restoreGraphicsState();
            }

            if (dx != 0)
                cs.transform(Matrix.getTranslateInstance((float) dx, 0));
            if (dy != 0)
                cs.transform(Matrix.getTranslateInstance(0, (float) -dy));

            renderTable(pageRenderer, sheet, table);

            renderImages(pageRenderer, sheet, printArea, region, pageClipRect, pageHeight);

        } catch (IOException e) {
            throw NopException.wrap(e);
        } finally {
            IoHelper.safeClose(pageRenderer);
        }
    }

    private void renderImages(PdfPageRenderer pageRenderer, IExcelSheet sheet, RectangleBean printArea, CellRange region,
                              java.awt.Rectangle pageClipRect, float pageHeight) {
        List<ExcelImage> images = sheet.getImages();
        if (images == null || images.isEmpty())
            return;

        double startX = 0;
        double startY = 0;

        if (region != null) {
            startX = sheet.getTable().getRangeWidth(0, region.getFirstColIndex() - 1, sheet.defaultColumnWidth());
            startY = sheet.getTable().getRangeHeight(0, region.getFirstRowIndex() - 1, sheet.defaultRowHeight());
        }

        for (ExcelImage image : images) {
            // 套打背景图：print=false的图片屏幕可见、打印/PDF导出时隐藏（与XLSX/HTML渲染器一致）
            if (!image.isPrint())
                continue;

            CellPosition pos = image.getAnchor().getStartPosition();
            if (pos == null)
                continue;

            if (region != null && !region.containsCell(pos.getRowIndex(), pos.getColIndex())) {
                continue;
            }

            image.calcSize(sheet);

            double imageX = image.getLeft() - startX;
            double imageY = image.getTop() - startY;

            // 图片裁剪到页面设备矩形在当前用户空间的对应范围（越界部分不绘制）
            PdfImageHelper.drawImage(renderer, pageRenderer.getContentStream(), image,
                    imageX, -imageY - image.getHeight(),
                    image.getWidth(), image.getHeight(), pageClipRect);
        }
    }

    private java.awt.Rectangle applyPageSetup(PdfPageRenderer pageRenderer, IExcelSheet sheet, ITableView table,
                                              RectangleBean printArea, float pageHeight,
                                              double repeatDx, double repeatDy) throws IOException {

        PDPageContentStream contentStream = pageRenderer.getContentStream();
        // 初始变换：将原点移动到打印区域的左上角。printArea.y是距页顶的距离，需换算为PDF自页底起的坐标
        contentStream.transform(Matrix.getTranslateInstance((float) printArea.getX(), pageHeight - (float) printArea.getY()));

        // 初始变换下的设备页矩形（用户空间）：x∈[-Tx, paperW-Tx], y∈[printArea.y-pageHeight, printArea.y]
        double paperWidth = ExcelPrintHelper.getPaperSize(sheet.getPageSetup()).getWidth();
        double clipX = -printArea.getX();
        double clipY = printArea.getY() - pageHeight;
        double clipW = paperWidth;
        double clipH = pageHeight;

        ExcelPageSetup pageSetup = sheet.getPageSetup();
        if (pageSetup == null)
            return new java.awt.Rectangle((int) Math.floor(clipX), (int) Math.floor(clipY),
                    (int) Math.ceil(clipW), (int) Math.ceil(clipH));

        // fit缩放/居中的计算需把重复标题块的占位计入（内容绘制在(dx,-dy)偏移之后）
        double tableWidth = repeatDx + table.getTableWidth(sheet.defaultColumnWidth());
        double tableHeight = repeatDy + table.getTableHeight(sheet.defaultRowHeight());

        // 计算缩放比例
        double scale = 1.0f;
        if (pageSetup.getScale() != null) {
            scale = pageSetup.getScale() / 100.0f;
        }

        // 处理fitToWidth/fitToHeight
        if (Boolean.TRUE.equals(pageSetup.getFitToWidth()) || Boolean.TRUE.equals(pageSetup.getFitToHeight())) {
            double widthRatio = printArea.getWidth() / tableWidth;
            double heightRatio = printArea.getHeight() / tableHeight;

            if (Boolean.TRUE.equals(pageSetup.getFitToWidth()) && Boolean.TRUE.equals(pageSetup.getFitToHeight())) {
                scale = Math.min(widthRatio, heightRatio);
            } else if (Boolean.TRUE.equals(pageSetup.getFitToWidth())) {
                scale = widthRatio;
            } else {
                scale = heightRatio;
            }
        }

        // 应用缩放
        if (scale != 1.0f) {
            contentStream.transform(Matrix.getScaleInstance((float) scale, (float) scale));
        }

        // 处理居中
        double offsetX = 0;
        double offsetY = 0;

        if (Boolean.TRUE.equals(pageSetup.getHorizontalCentered())) {
            double scaledWidth = repeatDx + table.getTableWidth(sheet.defaultColumnWidth()) * scale;
            offsetX = (printArea.getWidth() - scaledWidth) / 2;
        }

        if (Boolean.TRUE.equals(pageSetup.getVerticalCentered())) {
            double scaledHeight = repeatDy + table.getTableHeight(sheet.defaultRowHeight()) * scale;
            offsetY = (printArea.getHeight() - scaledHeight) / 2;
        }

        if (offsetX != 0 || offsetY != 0) {
            contentStream.transform(Matrix.getTranslateInstance((float) offsetX, (float) offsetY));
        }

        // 缩放/居中后重算裁剪矩形：user = (dev - offset)/scale - T
        double invScale = 1.0 / scale;
        clipX = -offsetX * invScale - printArea.getX();
        clipY = -offsetY * invScale + printArea.getY() - pageHeight;
        clipW = paperWidth * invScale;
        clipH = pageHeight * invScale;
        return new java.awt.Rectangle((int) Math.floor(clipX), (int) Math.floor(clipY),
                (int) Math.ceil(clipW), (int) Math.ceil(clipH));
    }

    private void renderHeaderFooter(PdfPageRenderer pageRenderer, IExcelSheet sheet,
                                    RectangleBean printArea, float pageHeight) throws IOException {
        ExcelPageSetup pageSetup = sheet.getPageSetup();
        if (pageSetup == null)
            return;

        // 页眉位置（在打印区域上方）。printArea.y为距页顶距离，printArea.y-header是页眉区顶部，换算为PDF坐标即页眉区的底边
        if (pageSetup.getHeader() != null) {
            double height = ExcelPrintHelper.getHeaderHeight(sheet.getPageMargins());
            float headerY = (float) (pageHeight - printArea.getY());
            renderHeaderFooterContent(pageRenderer, pageSetup.getHeader(),
                    headerY,
                    (float) printArea.getWidth(), (float) height);
        }

        // 页脚位置（在打印区域下方）
        if (pageSetup.getFooter() != null) {
            double height = ExcelPrintHelper.getFooterHeight(sheet.getPageMargins());
            float footerY = (float) (pageHeight - printArea.getY() - printArea.getHeight() - height);

            renderHeaderFooterContent(pageRenderer, pageSetup.getFooter(),
                    footerY, (float) printArea.getWidth(), (float) height);
        }
    }


    private void renderHeaderFooterContent(PdfPageRenderer pageRenderer, ExcelHeaderFooter hf,
                                           float y, float width, float height) throws IOException {
        // 渲染左中右三部分内容
        if (StringHelper.isNotEmpty(hf.getLeft())) {
            String left = hf.getLeft();
            renderText(pageRenderer, left, 0, y, width / 3, height, hf.getStyle());
        }

        if (StringHelper.isNotEmpty(hf.getCenter())) {
            String center = hf.getCenter();
            renderText(pageRenderer, center, width / 3, y, width / 3, height, hf.getStyle());
        }

        if (StringHelper.isNotEmpty(hf.getRight())) {
            String right = hf.getRight();
            renderText(pageRenderer, right, 2 * width / 3, y, width / 3, height, hf.getStyle());
        }
    }

    private void renderText(PdfPageRenderer pageRenderer, String text, float x, float y, float width, float height,
                            ExcelStyle style) throws IOException {
        ExcelFont font = style == null ? null : style.getFont();
        PDFont pdFont = renderer.getFont(font);
        float fontSize = PdfStyleHelper.getFontSize(font);
        // 字形回退：base-14字体无法编码中文等字符时切换CJK回退字体
        pdFont = renderer.fontForText(text, pdFont);
        PdfStyleHelper.drawText(pageRenderer.getContentStream(), text, pdFont, fontSize, new PDRectangle(x, y, width, height), style);
    }

    private void renderTable(PdfPageRenderer pageRenderer, IExcelSheet sheet, ITableView table) throws IOException {
        new PdfTableRenderer(renderer, styleProvider, sheet.defaultColumnWidth(), sheet.defaultRowHeight())
                .renderTable(pageRenderer.getContentStream(), table);
    }
}
