package io.nop.chart.report;

import io.nop.chart.export.ChartExportOptions;
import io.nop.chart.export.ChartExporter;
import io.nop.chart.export.ChartTypeRendererRegistry;
import io.nop.excel.model.ExcelChartModel;
import io.nop.excel.model.ExcelClientAnchor;
import io.nop.excel.model.ExcelImage;
import io.nop.excel.model.IExcelSheet;
import io.nop.excel.renderer.IChartImageProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Chart image provider over expanded report sheets (plan 2365 WI7): renders every
 * visible chart of the sheet through the nop-chart-export ECharts pipeline and
 * positions it by its anchor, mirroring ExcelImage.calcSize (plan 2364 D44: the
 * anchor is checked for null first — calcSize's makeAnchor would silently fabricate
 * an empty anchor and a 0x0 invisible image).
 *
 * <p>Registered as an IoC bean (report-chart-defaults.beans.xml + autoconfig); when
 * this module is not on the classpath the report renderers silently skip chart
 * rendering. Render failures surface as NopException (no silent no-op).</p>
 */
public class ExcelChartImageProvider implements IChartImageProvider {
    private static final Logger LOG = LoggerFactory.getLogger(ExcelChartImageProvider.class);

    private final ChartExporter chartExporter;

    public ExcelChartImageProvider() {
        this(new ChartExporter(ChartTypeRendererRegistry.getDefault()));
    }

    public ExcelChartImageProvider(ChartExporter chartExporter) {
        this.chartExporter = chartExporter;
    }

    @Override
    public List<ExcelImage> renderCharts(IExcelSheet sheet) {
        List<ExcelChartModel> charts = sheet.getCharts();
        if (charts == null || charts.isEmpty())
            return new ArrayList<>();

        ExpandedSheetCellRefResolver resolver = new ExpandedSheetCellRefResolver(sheet);
        List<ExcelImage> images = new ArrayList<>(charts.size());
        for (ExcelChartModel chart : charts) {
            images.add(renderChart(sheet, chart, resolver));
        }
        LOG.debug("nop.chart-report.render-charts: sheet={}, count={}", sheet.getName(), images.size());
        return images;
    }

    private ExcelImage renderChart(IExcelSheet sheet, ExcelChartModel chart,
                                   ExpandedSheetCellRefResolver resolver) {
        ChartExportOptions options = optionsFor(sheet, chart);
        byte[] png = chartExporter.exportToImage(chart, resolver, options);

        ExcelImage image = new ExcelImage();
        image.setImgType("png");
        image.setData(io.nop.commons.bytes.ByteString.of(png));

        ExcelClientAnchor anchor = chart.getAnchor();
        if (anchor != null) {
            double left = sheet.getCellLeft(anchor.getCol1()) + anchor.getDx1();
            double top = sheet.getCellTop(anchor.getRow1()) + anchor.getDy1();
            double x2 = sheet.getCellLeft(anchor.getCol2()) + anchor.getDx2();
            double y2 = sheet.getCellTop(anchor.getRow2()) + anchor.getDy2();
            image.setLeft(left);
            image.setTop(top);
            image.setWidth(Math.max(1, x2 - left));
            image.setHeight(Math.max(1, y2 - top));
        } else {
            image.setLeft(0);
            image.setTop(0);
            image.setWidth(options.getWidth());
            image.setHeight(options.getHeight());
        }
        return image;
    }

    /**
     * Render size follows the placement size (D44/WYSIWYG): anchor extent when
     * present, else the model's pt width/height, else the exporter defaults.
     */
    private ChartExportOptions optionsFor(IExcelSheet sheet, ExcelChartModel chart) {
        ChartExportOptions options = ChartExportOptions.defaultOptions();
        ExcelClientAnchor anchor = chart.getAnchor();
        if (anchor != null) {
            double left = sheet.getCellLeft(anchor.getCol1()) + anchor.getDx1();
            double top = sheet.getCellTop(anchor.getRow1()) + anchor.getDy1();
            double x2 = sheet.getCellLeft(anchor.getCol2()) + anchor.getDx2();
            double y2 = sheet.getCellTop(anchor.getRow2()) + anchor.getDy2();
            options.setWidth((int) Math.max(1, Math.round(x2 - left)));
            options.setHeight((int) Math.max(1, Math.round(y2 - top)));
        } else if (chart.getWidth() != null && chart.getHeight() != null) {
            options.setWidth((int) Math.max(1, Math.round(chart.getWidth())));
            options.setHeight((int) Math.max(1, Math.round(chart.getHeight())));
        }
        return options;
    }
}
