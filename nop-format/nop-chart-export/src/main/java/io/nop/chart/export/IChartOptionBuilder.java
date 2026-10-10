package io.nop.chart.export;

import io.nop.excel.chart.constants.ChartType;
import io.nop.excel.chart.model.ChartModel;
import io.nop.excel.chart.util.ChartDataSet;
import io.nop.excel.resolver.ICellRefResolver;

import java.util.List;

/**
 * Contract for building an ECharts option tree from a parsed {@link ChartModel}.
 *
 * <p>Implementations are registered in {@link ChartTypeRendererRegistry} keyed by
 * {@link ChartType}. A builder produces the full option map for its chart type
 * (palette, title, legend, coordinates and series); export-time settings such as
 * background color or image size are applied by the exporter, not here.</p>
 *
 * <p>Implementations must fail fast with {@code NopException} and
 * {@link ChartExportErrors} error codes on unsupported inputs; silent fallbacks
 * that would change chart semantics are not allowed.</p>
 */
public interface IChartOptionBuilder {

    ChartType getSupportedType();

    boolean supports(ChartType type);

    /**
     * Build the ECharts option tree (Map/List structure, JSON-serializable) for the
     * given chart model.
     *
     * @param chartModel parsed chart model, never null
     * @param resolver   resolves cell references for titles and data, never null
     * @param dataSets   series data resolved from the chart model via ChartDataResolver
     * @return option tree as Map/List structure
     */
    Object buildOption(ChartModel chartModel, ICellRefResolver resolver, List<ChartDataSet> dataSets);
}
