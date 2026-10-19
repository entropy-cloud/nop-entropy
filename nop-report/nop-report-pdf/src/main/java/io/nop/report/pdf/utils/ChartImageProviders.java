/**
 * Copyright (c) 2017-2024 Nop Platform.
 */
package io.nop.report.pdf.utils;

import io.nop.commons.bytes.ByteString;
import io.nop.excel.model.ExcelChartModel;
import io.nop.excel.model.IExcelSheet;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * 图表图片 provider 注册器（WI7）：null（缺省）使用内置桩 provider——合成灰底蓝框图案，
 * 打通 renderCharts 管线；真实 echarts provider 就绪后 setProvider 即插即用。
 *
 * <p>静态可变状态：测试替换后必须恢复 null（reuseForks 共享 JVM，防跨类泄漏）。</p>
 */
public final class ChartImageProviders {

    private static volatile IChartImageProvider provider;

    private ChartImageProviders() {
    }

    public static IChartImageProvider getProvider() {
        IChartImageProvider p = provider;
        return p != null ? p : StubHolder.STUB;
    }

    /**
     * 注册 provider；传 null 回落默认桩。
     */
    public static void setProvider(IChartImageProvider p) {
        provider = p;
    }

    static final class StubHolder {
        static final IChartImageProvider STUB = new StubProvider();
    }

    /** 默认桩：灰底蓝框 + 深色角标图案（视觉辨识管线产物） */
    static class StubProvider implements IChartImageProvider {
        @Override
        public ByteString renderChart(ExcelChartModel chart, IExcelSheet sheet, double widthPt, double heightPt) {
            int w = (int) Math.max(20, Math.round(widthPt * 2));
            int h = (int) Math.max(20, Math.round(heightPt * 2));
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            try {
                g.setColor(new Color(0xE8E8E8));
                g.fillRect(0, 0, w, h);
                g.setColor(new Color(0x3060C0));
                g.setPaint(new java.awt.GradientPaint(0, 0, new Color(0x3060C0), w, h, new Color(0x80A0E0)));
                int inset = Math.max(2, w / 20);
                g.fillRect(inset, inset, w - 2 * inset, h - 2 * inset);
                g.setColor(new Color(0x303030));
                int corner = Math.max(4, w / 10);
                g.fillRect(0, 0, corner, corner / 2);
            } finally {
                g.dispose();
            }
            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ImageIO.write(img, "png", bos);
                return ByteString.of(bos.toByteArray());
            } catch (IOException e) {
                return null;
            }
        }
    }
}
