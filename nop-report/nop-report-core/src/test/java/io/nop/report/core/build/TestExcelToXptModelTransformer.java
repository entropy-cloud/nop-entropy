/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.core.build;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.excel.model.ExcelChartModel;
import io.nop.excel.model.ExcelImage;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelTable;
import io.nop.excel.model.ExcelWorkbook;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestExcelToXptModelTransformer extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testParseImageModelSkipsNoConfigImage() {
        ExcelWorkbook wk = new ExcelWorkbook();
        ExcelSheet sheet = new ExcelSheet();
        sheet.setName("S1");
        sheet.setTable(new ExcelTable());
        wk.addSheet(sheet);

        ExcelImage img1 = new ExcelImage();
        img1.setName("img1");
        img1.setDescription("no extended config here");

        ExcelImage img2 = new ExcelImage();
        img2.setName("img2");
        img2.setDescription("img----\ntestExpr=true");

        sheet.setImages(Arrays.asList(img1, img2));

        ExcelToXptModelTransformer.INSTANCE.transform(wk);

        // 排在前面的图片没有扩展配置时，不应中断后续图片的解析
        assertNotNull(img2.getTestExpr());
    }

    @Test
    public void testParseChartModelSkipsNoConfigChart() {
        ExcelWorkbook wk = new ExcelWorkbook();
        ExcelSheet sheet = new ExcelSheet();
        sheet.setName("S1");
        sheet.setTable(new ExcelTable());
        wk.addSheet(sheet);

        ExcelChartModel chart1 = new ExcelChartModel();
        chart1.setName("chart1");
        chart1.setDescription("plain chart");

        ExcelChartModel chart2 = new ExcelChartModel();
        chart2.setName("chart2");
        chart2.setDescription("chart----");

        sheet.setCharts(Arrays.asList(chart1, chart2));

        ExcelToXptModelTransformer.INSTANCE.transform(wk);

        // 没有扩展配置的图表描述保持原样
        assertEquals("plain chart", chart1.getDescription());
        // 带扩展配置的图表应解析出动态绑定配置，且描述被截断到----之前
        assertNotNull(chart2.getDynamicBindings());
        assertEquals("chart", chart2.getDescription());
    }
}
