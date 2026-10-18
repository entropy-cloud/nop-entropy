package io.nop.report.pdf.renderer;

import io.nop.api.core.beans.geometry.RectangleBean;
import io.nop.api.core.beans.geometry.SizeBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.IoHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.model.table.CellPosition;
import io.nop.core.model.table.CellRange;
import io.nop.core.model.table.ICellView;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.util.Matrix;

import java.io.IOException;
import java.util.List;

import static io.nop.report.pdf.ReportPdfErrors.ARG_PRINT_TITLE_RANGE;
import static io.nop.report.pdf.ReportPdfErrors.ERR_PDF_UNSUPPORTED_PRINT_TITLES;

public class PdfSheetRenderer {
    static final Logger LOG = LoggerFactory.getLogger(PdfSheetRenderer.class);

    private final PdfRenderer renderer;
    private final IExcelStyleProvider styleProvider;

    public PdfSheetRenderer(PdfRenderer renderer, IExcelStyleProvider styleProvider) {
        this.renderer = renderer;
        this.styleProvider = styleProvider;
    }

    public void renderSheet(IExcelSheet sheet) {
        // 入口处一次性解析重复标题声明（模型优先，非前缀形态显式报错），
        // 拆分预留与续页/侧页/角块渲染全部使用同一解析结果
        int[] repeat = resolveRepeatTitles(sheet);
        int repeatRows = repeat[0];
        int repeatCols = repeat[1];

        ExcelPageSetup pageSetup = sheet.getPageSetup();
        SizeBean pagerSize = ExcelPrintHelper.getPaperSize(pageSetup);
        RectangleBean printArea = ExcelPrintHelper.calculatePrintArea(pagerSize, sheet.getPageMargins());

        // 列分页语义裁定（WI2）：fitToWidth 为目标页数 int（0/缺省=宽度不约束）。宽度不约束与
        // N 页宽目标都需要列分页（后者正是达成目标的机制），故恒启用。
        boolean enableColumnPaging = true;

        ITableView table = sheet.getTable();

        // 重复块占位（页容量扣减与 fit 目标公式共用）
        double repeatWidth = repeatCols > 0 ? table.getRangeWidth(0, repeatCols - 1, sheet.defaultColumnWidth()) : 0;
        double repeatHeight = repeatRows > 0 ? table.getRangeHeight(0, repeatRows - 1, sheet.defaultRowHeight()) : 0;

        // WI2 统一缩放管线：入口一次计算 effective scale（fit 目标优先于 scale 百分比），
        // 分页几何按缩放后容量 printArea/scale 计算，绘制侧共用同一 scale
        double scale = resolveScale(sheet, pageSetup, printArea, repeatWidth, repeatHeight);
        scale = clampByMinFontSize(sheet, scale);

        // 第一遍：按缩放后完整页容量拆分。单页能放下则不做任何重复
        List<CellRange> tableRegions = TableSplitHelper.splitTable(table,
                Math.max(50, printArea.getWidth() / scale),
                Math.max(50, printArea.getHeight() / scale),
                sheet.defaultColumnWidth(), sheet.defaultRowHeight(), enableColumnPaging);
        if (tableRegions.size() <= 1) {
            renderSheet(sheet, pagerSize, printArea, null, repeatRows, repeatCols, scale);
            return;
        }

        // 需要分页：为续页的重复标题块预留空间后重新拆分，避免页尾内容溢出
        if (repeatHeight > 0 || repeatWidth > 0) {
            tableRegions = TableSplitHelper.splitTable(table,
                    Math.max(50, (printArea.getWidth() - repeatWidth) / scale),
                    Math.max(50, (printArea.getHeight() - repeatHeight) / scale),
                    sheet.defaultColumnWidth(), sheet.defaultRowHeight(), enableColumnPaging);
        }

        for (CellRange region : tableRegions) {
            renderSheet(sheet, pagerSize, printArea, region, repeatRows, repeatCols, scale);
        }
    }

    /**
     * fit 目标页数的浮点容差：scale 由 tableW 反推，容量恰为 tableW/N 时浮点误差会把恰好填满的
     * 第 N 段误判为溢出，多裂出一页。1e-9 相对容差让容量略大于内容，消除边界抖动且不改变可感知语义。
     */
    private static final double FIT_EPSILON = 1e-9;

    /**
     * 解析 effective scale：{@code scale} 百分比为基础值；fit 模式（任一维目标页数 >0）覆盖。
     * 容量口径与两遍拆分一致：分页时每页内容容量为 {@code (print - repeat)/scale}
     * （重复标题块在所有页预留），故目标 N 页的约束为 {@code scale <= N*(print-repeat)/table}。
     * fit 只缩小不放大（Excel 语义，小表不会被放大到填满页面）；空表（宽或高为 0）不缩放。
     */
    private double resolveScale(IExcelSheet sheet, ExcelPageSetup pageSetup, RectangleBean printArea,
                                double repeatWidth, double repeatHeight) {
        double scale = 1.0;
        if (pageSetup != null && pageSetup.getScale() != null)
            scale = pageSetup.getScale() / 100.0;

        if (pageSetup == null || !pageSetup.isFitMode())
            return scale;

        ITableView table = sheet.getTable();
        double tableWidth = table.getTableWidth(sheet.defaultColumnWidth());
        double tableHeight = table.getTableHeight(sheet.defaultRowHeight());
        if (tableWidth <= 0 || tableHeight <= 0)
            return 1.0;

        double fitScale = Double.MAX_VALUE;
        Integer fitW = pageSetup.getFitToWidth();
        Integer fitH = pageSetup.getFitToHeight();
        if (fitW != null && fitW > 0) {
            double capW = pageCapacity(printArea.getWidth(), repeatWidth);
            fitScale = Math.min(fitScale, fitW * capW / tableWidth);
        }
        if (fitH != null && fitH > 0) {
            double capH = pageCapacity(printArea.getHeight(), repeatHeight);
            fitScale = Math.min(fitScale, fitH * capH / tableHeight);
        }
        return Math.min(1.0, fitScale * (1 - FIT_EPSILON));
    }

    /** 单页内容容量（扣重复块预留）；重复块大于页内容区时退化为不扣（渲染已属退化场景）。 */
    private double pageCapacity(double printSize, double repeatSize) {
        double cap = printSize - repeatSize;
        return cap > 0 ? cap : printSize;
    }

    /**
     * 最小可读字号收敛：缩放导致表格最小字号低于 {@code nop.report.pdf.min-font-size-pt} 阈值时，
     * WARN 告警并把 scale 收敛到 threshold/minFont（目标页数让位于可读性）。scale>=1（放大/原大）不限制。
     */
    private double clampByMinFontSize(IExcelSheet sheet, double scale) {
        float threshold = ReportPdfConfigs.CFG_PDF_MIN_FONT_SIZE.get();
        if (threshold <= 0 || scale >= 1.0)
            return scale;

        float minFont = scanMinFontSize(sheet);
        double effective = minFont * scale;
        if (effective < threshold) {
            LOG.warn("nop.report.pdf.min-font-size-clamped:minFont={},scale={},effective={},threshold={} "
                    + "-- fit/scale target relaxed to keep readability", minFont, scale, effective, threshold);
            return threshold / minFont;
        }
        return scale;
    }

    /**
     * 扫描表格全部单元格样式的最小字号（口径与渲染同源：{@link PdfStyleHelper#normalizeFontSize}）。
     */
    private float scanMinFontSize(IExcelSheet sheet) {
        float min = Float.MAX_VALUE;
        IExcelStyleProvider styleProvider = this.styleProvider;
        ITableView table = sheet.getTable();
        int rowCount = table.getRowCount();
        int colCount = table.getColCount();
        for (int i = 0; i < rowCount; i++) {
            for (int j = 0; j < colCount; j++) {
                ICellView cell = table.getCell(i, j);
                if (cell == null)
                    continue;
                ExcelStyle style = styleProvider == null ? null : styleProvider.getStyle(cell.getStyleId());
                io.nop.excel.model.ExcelFont font = style != null ? style.getFont() : null;
                if (font == null && styleProvider != null)
                    font = styleProvider.getDefaultFont();
                min = Math.min(min, PdfStyleHelper.normalizeFontSize(font != null ? font.getFontSize() : null));
            }
        }
        // 空表/无样式单元格：回退缺省字号
        return min == Float.MAX_VALUE ? PdfStyleHelper.DEFAULT_FONT_SIZE : min;
    }

    /**
     * 解析重复标题声明：pageSetup 的 repeatTitleRows/repeatTitleCols（来自模板 Print_Titles）优先，
     * 缺失时回退全局配置缺省。模型声明必须为前缀形态（从首行/首列开始），否则显式报错——
     * 非前缀是模板级不支持形态，与是否实际分页无关。
     */
    private int[] resolveRepeatTitles(IExcelSheet sheet) {
        ExcelPageSetup pageSetup = sheet.getPageSetup();
        return new int[]{
                resolveTitleCount(pageSetup == null ? null : pageSetup.getRepeatTitleRows(),
                        ReportPdfConfigs.CFG_PDF_REPEAT_HEADER_ROWS.get(), sheet.getTable().getRowCount()),
                resolveTitleCount(pageSetup == null ? null : pageSetup.getRepeatTitleCols(),
                        ReportPdfConfigs.CFG_PDF_REPEAT_KEY_COLUMNS.get(), sheet.getTable().getColCount())
        };
    }

    private int resolveTitleCount(String range, int configDefault, int totalCount) {
        if (StringHelper.isEmpty(range)) {
            int n = Math.max(0, configDefault);
            return Math.min(n, Math.max(0, totalCount - 1));
        }
        int[] r = ExcelPrintHelper.parseTitleRange(range);
        if (!ExcelPrintHelper.isPrefixRange(r))
            throw new NopException(ERR_PDF_UNSUPPORTED_PRINT_TITLES).param(ARG_PRINT_TITLE_RANGE, range);
        return Math.min(r[1], Math.max(0, totalCount - 1));
    }

    private double repeatRowsHeight(IExcelSheet sheet, CellRange region, int repeatRows) {
        int k = Math.min(repeatRows, region.getFirstRowIndex());
        return k > 0 ? sheet.getTable().getRangeHeight(0, k - 1, sheet.defaultRowHeight()) : 0;
    }

    private double repeatColsWidth(IExcelSheet sheet, CellRange region, int repeatCols) {
        int m = Math.min(repeatCols, region.getFirstColIndex());
        return m > 0 ? sheet.getTable().getRangeWidth(0, m - 1, sheet.defaultColumnWidth()) : 0;
    }

    public void renderSheet(IExcelSheet sheet, SizeBean paperSize, RectangleBean printArea, CellRange region) {
        int[] repeat = resolveRepeatTitles(sheet);
        double scale = resolveScale(sheet, sheet.getPageSetup(), printArea,
                repeat[1] > 0 ? sheet.getTable().getRangeWidth(0, repeat[1] - 1, sheet.defaultColumnWidth()) : 0,
                repeat[0] > 0 ? sheet.getTable().getRangeHeight(0, repeat[0] - 1, sheet.defaultRowHeight()) : 0);
        scale = clampByMinFontSize(sheet, scale);
        renderSheet(sheet, paperSize, printArea, region, repeat[0], repeat[1], scale);
    }

    private void renderSheet(IExcelSheet sheet, SizeBean paperSize, RectangleBean printArea, CellRange region,
                             int repeatRows, int repeatCols, double scale) {
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
            double dx = repeatColsTable != null ? repeatColsWidth(sheet, region, repeatCols) : 0;
            double dy = repeatRowsTable != null ? repeatRowsHeight(sheet, region, repeatRows) : 0;

            // 应用页面设置，并取得设备页矩形在当前用户空间的裁剪矩形（供图片裁剪）
            java.awt.Rectangle pageClipRect = applyPageSetup(pageRenderer, sheet, table, printArea, pageHeight, dx, dy, scale);

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
                                              double repeatDx, double repeatDy, double scale) throws IOException {

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

        // 缩放比例由 renderSheet 入口统一解析（fit 目标优先于 scale 百分比，含最小字号收敛），
        // 分页几何与绘制共用同一 scale，避免页容量与绘制口径分叉
        if (scale != 1.0) {
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
        // （先归一化控制字符/不换行空格，避免可归一化字符触发缺字形错误）
        pdFont = renderer.fontForText(PdfStyleHelper.normalizeControlChars(text), pdFont);
        PdfStyleHelper.drawText(pageRenderer.getContentStream(), text, pdFont, fontSize, new PDRectangle(x, y, width, height), style);
    }

    private void renderTable(PdfPageRenderer pageRenderer, IExcelSheet sheet, ITableView table) throws IOException {
        new PdfTableRenderer(renderer, styleProvider, sheet.defaultColumnWidth(), sheet.defaultRowHeight())
                .renderTable(pageRenderer.getContentStream(), table);
    }
}
