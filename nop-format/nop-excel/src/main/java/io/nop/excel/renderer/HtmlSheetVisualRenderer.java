package io.nop.excel.renderer;

import io.nop.commons.util.StringHelper;
import io.nop.excel.model.ExcelImage;
import io.nop.excel.model.IExcelSheet;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Renders a sheet's visual overlays (images + charts) as CSS background layers on
 * the sheet div (plan 2365). Chart images come from the optional
 * {@link IChartImageProvider}; when no provider is present only the sheet's own
 * images are rendered, exactly as before this class existed.
 */
public final class HtmlSheetVisualRenderer {

    private HtmlSheetVisualRenderer() {
    }

    /**
     * Render the sheet's images plus, when a chart image provider is available, its
     * charts as one merged background stack. Writes nothing when there is nothing to
     * render.
     */
    public static void renderVisualOverlays(IExcelSheet sheet, String sheetId, Writer out) throws IOException {
        List<ExcelImage> images = sheet.getImages();
        List<ExcelImage> effective = images;

        IChartImageProvider provider = ChartImageProviderRegistry.getProvider();
        if (provider != null && sheet.hasChart()) {
            List<ExcelImage> charts = provider.renderCharts(sheet);
            if (charts != null && !charts.isEmpty()) {
                effective = new ArrayList<>(images == null ? new ArrayList<>() : images);
                effective.addAll(charts);
            }
        }

        if (effective != null && !effective.isEmpty())
            renderImages(sheetId, out, effective);
    }

    /**
     * Existing image rendering (moved from HtmlReportRendererFactory, List
     * parameterized): one style block with CSS background layers sized in pt.
     */
    public static void renderImages(String sheetId, Writer out, List<ExcelImage> images) throws IOException {
        out.write("<style>\n");
        out.write("#");
        out.write(sheetId);
        out.write("{\n");

        double minHeight = 0;
        double minWidth = 0;

        boolean notPrint = false;
        for (ExcelImage image : images) {
            if (image.getData() == null)
                continue;
            if (!image.isPrint()) {
                notPrint = true;
            }

            minHeight = Math.max(minHeight, image.getTop() + image.getHeight());
            minWidth = Math.max(minWidth, image.getLeft() + image.getWidth());
        }
        out.write("min-height:" + minHeight + "pt;\n");
        out.write("min-width:" + minWidth + "pt;\n");
        outputBackground(images.stream().filter(image -> image.getData() != null), out);
        out.write("}\n");

        // 有不打印的图片
        if (notPrint) {
            out.write("@media print {\n");
            out.write("#");
            out.write(sheetId);
            out.write("{\n");
            outputBackground(images.stream().filter(image -> image.getData() != null && image.isPrint()), out);
            out.write("}\n");
            out.write("}\n");
        }

        out.write("</style>\n");
    }

    public static void outputBackground(Stream<ExcelImage> images, Writer out) throws IOException {
        List<String> urls = new ArrayList<>();
        List<String> sizes = new ArrayList<>();
        List<String> posList = new ArrayList<>();
        List<String> repeats = new ArrayList<>();

        images.forEach(image -> {
            String url = image.getData().toDataUrl(image.getMimeType());
            urls.add("url(" + url + ")");
            sizes.add(image.getWidth() + "pt " + image.getHeight() + "pt");
            posList.add(image.getLeft() + "pt " + image.getTop() + "pt");
            repeats.add("no-repeat");
        });

        out.write("background-image:");
        if (urls.isEmpty()) {
            out.write("none;\n");
            return;
        }

        out.write(StringHelper.join(urls, ","));
        out.write(";\n");
        out.write("background-size:");
        out.write(StringHelper.join(sizes, ","));
        out.write(";\n");
        out.write("background-position:");
        out.write(StringHelper.join(posList, ","));
        out.write(";\n");
        out.write("background-repeat:");
        out.write(StringHelper.join(repeats, ","));
        out.write(";\n");
    }
}
