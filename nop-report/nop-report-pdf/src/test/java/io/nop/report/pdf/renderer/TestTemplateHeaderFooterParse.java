/**
 * Copyright (c) 2017-2024 Nop Platform.
 */
package io.nop.report.pdf.renderer;

import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.ooxml.xlsx.parse.ExcelWorkbookParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI4 缺陷回归：xlsx 模板 headerFooter 此前在主解析路径被整段丢弃。
 */
public class TestTemplateHeaderFooterParse extends JunitBaseTestCase {

    @Test
    public void testTemplateHeaderFooterLoadedIntoModel() {
        ExcelWorkbook workbook = new ExcelWorkbookParser()
                .parseFromResource(new ClassPathResource("classpath:_vfs/test/test-pdf.xpt.xlsx"));
        ExcelSheet sheet = workbook.getSheets().get(0);
        assertNotNull(sheet.getPageSetup(), "headerFooter element should create pageSetup");
        assertNotNull(sheet.getPageSetup().getFooter(), "template oddFooter must load into model (dropped before WI4)");
        assertEquals("&\"SimSun,Plain\"&9 第1页/共3页", sheet.getPageSetup().getFooter().getCenter());
        assertNull(sheet.getPageSetup().getFooter().getLeft());
        assertNull(sheet.getPageSetup().getFooter().getRight());
        assertTrue(sheet.getPageSetup().getDifferentFirst() == null
                || !sheet.getPageSetup().getDifferentFirst());
    }
}
