package io.nop.report.demo;

import io.nop.api.core.ApiConfigs;
import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.autotest.junit.JunitAutoTestCase;
import jakarta.inject.Inject;

import io.nop.report.demo.biz.ReportDemoBizModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI12：barcodeFormat=CODE_128 ext prop 渲染条码图片（demo 全链路：模板 ext props → QRCODE 函数 → 图片嵌入）。
 * 模板：/base/11-打印条码和二维码.xpt.xlsx（含 qr:barcodeFormat=CODE_128 配置）。
 */
@NopTestConfig
public class TestBarcode128Render extends JunitAutoTestCase {

    @Inject
    ReportDemoBizModel reportDemo;

    @Test
    public void testBarcode128Render() {
        setTestConfig(ApiConfigs.CFG_EXCEPTION_FILL_STACKTRACE, true);
        String reportName = "/base/11-打印条码和二维码.xpt.xlsx";
        String html = reportDemo.renderHtml(reportName);
        assertNotNull(html);
        assertTrue(html.contains("data:image"), "barcode/qrcode image embedded in html");
    }
}
