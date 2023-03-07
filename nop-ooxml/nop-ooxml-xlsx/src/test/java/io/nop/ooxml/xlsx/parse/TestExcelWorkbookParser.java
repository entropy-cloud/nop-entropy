/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.ooxml.xlsx.parse;

import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.core.unittest.BaseTestCase;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelWorkbook;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestExcelWorkbookParser extends BaseTestCase {
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

        assertEquals("a=1\r\nb=2", wk.getSheets().get(0).getTable().getCell(0,0).getComment());
    }

    String normalize(String str) {
        List<String> list = StringHelper.stripedSplit(str, '\n');
        return StringHelper.join(list, "\n");
    }
}