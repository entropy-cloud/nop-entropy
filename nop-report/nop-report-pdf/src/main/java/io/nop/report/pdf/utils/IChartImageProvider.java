/**
 * Copyright (c) 2017-2024 Nop Platform.
 */
package io.nop.report.pdf.utils;

import io.nop.commons.bytes.ByteString;
import io.nop.excel.model.ExcelChartModel;
import io.nop.excel.model.IExcelSheet;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

/**
 * 图表图片 provider SPI（WI7）：把图表渲染为 PNG 字节供 PDF 绘制。
 *
 * <p>真实实现由 echarts-server-render-roadmap 交付（GraalJS 服务端渲染）；当前默认桩
 * {@link ChartImageProviders} 输出合成占位图，打通 renderCharts 管线。SPI 只约定 PNG 字节——
 * SVG 矢量产物由 provider 自行栅格化（PDFBox 3 原生不支持 SVG）。</p>
 */
public interface IChartImageProvider {

    /**
     * 渲染图表为 PNG 字节。
     *
     * @param chart    图表模型（含锚点）
     * @param sheet    所属工作表
     * @param widthPt  渲染区宽（pt）
     * @param heightPt 渲染区高（pt）
     * @return PNG 字节；null 表示无法渲染（调用方 WARN 跳过）
     */
    ByteString renderChart(ExcelChartModel chart, IExcelSheet sheet, double widthPt, double heightPt);
}
