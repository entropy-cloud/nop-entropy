/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.xlsx.parse;

import io.nop.commons.util.StringHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.core.resource.impl.FileResource;
import io.nop.core.unittest.BaseTestCase;
import io.nop.excel.model.ExcelCell;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelTable;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.ooxml.xlsx.output.ExcelTemplate;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestExcelWorkbookParser extends BaseTestCase {
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testParse() {
        IResource resource = new ClassPathResource("classpath:xlsx/format-demo.xlsx");
        ExcelWorkbook wk = new ExcelWorkbookParser().parseFromResource(resource);

        String dumpStr = "";
        for (ExcelSheet sheet : wk.getSheets()) {
            dumpStr += sheet.getTable().toDebugString();
            dumpStr += "\n\n";
            sheet.getTable().dump(sheet.getName());
        }
        assertEquals(normalize(attachmentText("format-demo.txt")), normalize(dumpStr));

        assertEquals("a=1\r\nb=2", wk.getSheets().get(0).getTable().getCell(0, 0).getComment());
    }

    String normalize(String str) {
        List<String> list = StringHelper.stripedSplit(str, '\n');
        return StringHelper.join(list, "\n");
    }

    /**
     * 测试特殊字符
     */
    @Test
    public void testSymbol() {
        IResource resource = new ClassPathResource("classpath:xlsx/test-symbol.xlsx");
        ExcelWorkbook wk = new ExcelWorkbookParser().parseFromResource(resource);
        new ExcelTemplate(wk).generateToFile(getTargetFile("test-symbol.xlsx"), XLang.newEvalScope());
    }

    @Test
    public void testFormula() {
        IResource resource = new ClassPathResource("classpath:xlsx/test-formula.xlsx");
        ExcelWorkbook wk = new ExcelWorkbookParser().parseFromResource(resource);
        File targetFile = getTargetFile("test-formula.xlsx");
        new ExcelTemplate(wk).generateToFile(targetFile, XLang.newEvalScope());

        wk = new ExcelWorkbookParser().parseFromResource(new FileResource(targetFile));
        ExcelCell cell = (ExcelCell) wk.getSheets().get(0).getTable().getCell(0, 0);
        assertEquals("SUM(B2:C2)", cell.getFormula());
    }

    @Test
    public void testHyperlink() {
        IResource resource = new ClassPathResource("classpath:xlsx/test-link.xlsx");
        ExcelWorkbook wk = new ExcelWorkbookParser().parseFromResource(resource);
        File targetFile = getTargetFile("test-link.xlsx");
        new ExcelTemplate(wk).generateToFile(targetFile, XLang.newEvalScope());

        ExcelTable table = wk.getSheets().get(0).getTable();
        assertEquals("ref:nop_wf_instance!A1", table.getCell(1, 1).getLinkUrl());
    }
}