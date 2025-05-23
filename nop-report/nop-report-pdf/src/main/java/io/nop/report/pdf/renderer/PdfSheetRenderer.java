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
import io.nop.excel.model.ExcelHeaderFooter;
import io.nop.excel.model.ExcelImage;
import io.nop.excel.model.ExcelPageSetup;
import io.nop.excel.model.ExcelStyle;
import io.nop.excel.model.IExcelSheet;
import io.nop.excel.model.IExcelStyleProvider;
import io.nop.excel.print.ExcelPrintHelper;
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
        } else {
            boolean enableColumnPaging = pageSetup == null || !Boolean.FALSE.equals(pageSetup.getFitToWidth());

            List<CellRange> tableRegions = TableSplitHelper.splitTable(sheet.getTable(),
                    printArea.getWidth(), printArea.getHeight(),
                    sheet.defaultColumnWidth(), sheet.defaultRowHeight(), enableColumnPaging);

            if (tableRegions.size() == 1) {
                renderSheet(sheet, pagerSize, printArea, null);
            } else {
                for (CellRange region : tableRegions) {
                    renderSheet(sheet, pagerSize, printArea, region);
                }
            }
        }
    }

    public void renderSheet(IExcelSheet sheet, SizeBean paperSize, RectangleBean printArea, CellRange region) {
        ITableView table;
        if (region == null) {
            table = sheet.getTable();
        } else {
            table = sheet.getTable().getSubTable(region).clip();
        }

        PdfPageRenderer pageRenderer = null;
        try {
            pageRenderer = renderer.addPage(PdfPrintHelper.toRectangle(paperSize));

            // 渲染页眉页脚
            renderHeaderFooter(pageRenderer, sheet, printArea);

            // 应用页面设置
            applyPageSetup(pageRenderer, sheet, table, printArea);

            renderTable(pageRenderer, sheet, table);

            renderImages(pageRenderer, sheet, printArea, region);

        } catch (IOException e) {
            throw NopException.wrap(e);
        } finally {
            IoHelper.safeClose(pageRenderer);
        }
    }

    private void renderImages(PdfPageRenderer pageRenderer, IExcelSheet sheet, RectangleBean printArea, CellRange region) {
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
            CellPosition pos = image.getAnchor().getStartPosition();
            if (pos == null)
                continue;

            if (region != null && !region.containsCell(pos.getRowIndex(), pos.getColIndex())) {
                continue;
            }

            image.calcSize(sheet);

            double imageX = image.getLeft() - startX;
            double imageY = image.getTop() - startY;

            PdfImageHelper.drawImage(renderer, pageRenderer.getContentStream(), image,
                    imageX, -imageY - image.getHeight(),
                    image.getWidth(), image.getHeight());
        }
    }

    private void applyPageSetup(PdfPageRenderer pageRenderer, IExcelSheet sheet, ITableView table, RectangleBean printArea) throws IOException {

        PDPageContentStream contentStream = pageRenderer.getContentStream();
        // 初始变换：将原点移动到打印区域的左上角
        contentStream.transform(Matrix.getTranslateInstance((float) printArea.getX(), (float) printArea.getEndY()));

        ExcelPageSetup pageSetup = sheet.getPageSetup();
        if (pageSetup == null)
            return;

        double tableWidth = table.getTableWidth(sheet.defaultColumnWidth());
        double tableHeight = table.getTableHeight(sheet.defaultRowHeight());

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
            double scaledWidth = table.getTableWidth(sheet.defaultColumnWidth()) * scale;
            offsetX = (printArea.getWidth() - scaledWidth) / 2;
        }

        if (Boolean.TRUE.equals(pageSetup.getVerticalCentered())) {
            double scaledHeight = table.getTableHeight(sheet.defaultRowHeight()) * scale;
            offsetY = (printArea.getHeight() - scaledHeight) / 2;
        }

        if (offsetX != 0 || offsetY != 0) {
            contentStream.transform(Matrix.getTranslateInstance((float) offsetX, (float) offsetY));
        }
    }

    private void renderHeaderFooter(PdfPageRenderer pageRenderer, IExcelSheet sheet,
                                    RectangleBean printArea) throws IOException {
        ExcelPageSetup pageSetup = sheet.getPageSetup();
        if (pageSetup == null)
            return;

        // 页眉位置（在打印区域上方）
        if (pageSetup.getHeader() != null) {
            double height = ExcelPrintHelper.getHeaderHeight(sheet.getPageMargins());
            float headerY = (float) (printArea.getY() - height);
            renderHeaderFooterContent(pageRenderer, pageSetup.getHeader(),
                    headerY,
                    (float) printArea.getWidth(), (float) height);
        }

        // 页脚位置（在打印区域下方）
        if (pageSetup.getFooter() != null) {
            double height = ExcelPrintHelper.getFooterHeight(sheet.getPageMargins());
            float footerY = (float) (printArea.getY() + printArea.getHeight() + height);

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
        PDFont font = renderer.getFont(style.getFont());
        float fontSize = PdfStyleHelper.getFontSize(style.getFont());
        PdfStyleHelper.drawText(pageRenderer.getContentStream(), text, font, fontSize, new PDRectangle(x, y, width, height), style);
    }

    private void renderTable(PdfPageRenderer pageRenderer, IExcelSheet sheet, ITableView table) throws IOException {
        new PdfTableRenderer(renderer, styleProvider, sheet.defaultColumnWidth(), sheet.defaultRowHeight())
                .renderTable(pageRenderer.getContentStream(), table);
    }
}
