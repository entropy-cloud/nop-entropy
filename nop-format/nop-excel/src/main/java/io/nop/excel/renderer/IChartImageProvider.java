package io.nop.excel.renderer;

import io.nop.excel.model.ExcelImage;
import io.nop.excel.model.IExcelSheet;

import java.util.List;

/**
 * SPI for rendering a sheet's charts to images (plan 2365): implemented by the
 * optional chart-rendering module (nop-chart-report); the HTML report renderers
 * consult {@link ChartImageProviderRegistry#getProvider()} and silently skip chart
 * rendering when no provider is present on the classpath.
 *
 * <p>The signature only uses nop-excel types so this interface adds no module
 * dependencies; produced images must carry position/size in pt and a non-null
 * imgType (e.g. "png", from which the data-URL mime type is derived).</p>
 */
public interface IChartImageProvider {

    /**
     * Render all charts of the given sheet.
     *
     * @return the rendered chart images (never null), in chart order; an empty list
     *         means the sheet has no renderable charts.
     */
    List<ExcelImage> renderCharts(IExcelSheet sheet);
}
