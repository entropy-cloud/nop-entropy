/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.pdf.renderer;

import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.context.IEvalContext;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelWorkbook;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI10：png renderType——报表 → 逐页 PNG（zip 输出）。
 */
public class TestPngExport extends JunitBaseTestCase {

    @Test
    public void testPngZipExport() throws Exception {
        ExcelWorkbook wb = new ExcelWorkbook();
        wb.setSheets(new ArrayList<>());
        io.nop.excel.model.ExcelSheet sheet = new io.nop.excel.model.ExcelSheet();
        sheet.setName("s1");
        io.nop.excel.model.ExcelTable table = new io.nop.excel.model.ExcelTable();
        java.util.List<io.nop.excel.model.ExcelRow> rows = new java.util.ArrayList<>();
        for (int i = 0; i < 3; i++) {
            io.nop.excel.model.ExcelRow row = new io.nop.excel.model.ExcelRow();
            io.nop.excel.model.ExcelCell cell = new io.nop.excel.model.ExcelCell();
            cell.setValue("v" + i);
            row.setCells(java.util.Collections.singletonList(cell));
            rows.add(row);
        }
        table.setRows(rows);
        sheet.setTable(table);
        wb.setSheets(new ArrayList<>(java.util.Collections.singletonList(sheet)));

        ExcelSheet finalSheet = sheet;
        PngReportRenderer png = new PngReportRenderer(wb, (ctx, consumer) -> consumer.accept(finalSheet, ctx));
        File out = new File("target/png-export.zip");
        png.generateToFile(out, io.nop.xlang.api.XLang.newEvalScope());

        assertTrue(out.exists());
        assertTrue(out.length() > 0);

        int count = 0;
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(java.nio.file.Files.readAllBytes(out.toPath())))) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                assertTrue(entry.getName().startsWith("page-"), entry.getName());
                byte[] header = new byte[8];
                int read = zin.readNBytes(header, 0, 8);
                assertTrue(read >= 8);
                // PNG 魔数
                assertEquals(0x89, header[0] & 0xFF);
                assertEquals('P', header[1]);
                assertEquals('N', header[2]);
                assertEquals('G', header[3]);
                count++;
            }
        }
        assertTrue(count >= 1, "at least one page");
        // png 渲染器经 PngReportRendererFactory 注册（nopReportRenderer_png）
    }
}
