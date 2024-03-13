package io.nop.ooxml.xlsx.parse;

import io.nop.api.core.json.JSON;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.unittest.BaseTestCase;
import io.nop.excel.imp.ImportExcelParser;
import io.nop.excel.imp.model.ImportModel;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class TestImportExcelParser extends BaseTestCase {
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testMultipleAsMap() {
        IResource resource = attachmentResource("test-msg.xlsx");
        ImportModel importModel = (ImportModel) ResourceComponentManager.instance().loadComponentModel("/test/test-msg.imp.xml");

        ExcelWorkbook wk = new ExcelWorkbookParser().parseFromResource(resource);
        Object obj = new ImportExcelParser(importModel, XLang.newCompileTool()).parseFromWorkbook(wk);
        System.out.println(JSON.serialize(obj, true));
    }
}
