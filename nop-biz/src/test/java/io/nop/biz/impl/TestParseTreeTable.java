package io.nop.biz.impl;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.IResource;
import io.nop.core.unittest.BaseTestCase;
import io.nop.ooxml.xlsx.util.ExcelHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestParseTreeTable extends BaseTestCase {
    @BeforeAll
    public static void beforeAll() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void afterAll() {
        CoreInitialization.destroy();
    }

    @Test
    public void testParse() {
        IResource resource = attachmentResource("test_imp.test.xlsx");
        Object bean = ExcelHelper.loadXlsxObject("/nop/test/imp/test.imp.xml", resource);
        assertEquals(attachmentJsonText("imp-result.json"), JsonTool.serialize(bean, true));
    }


    /**
     * 可以解析两种不同的Excel模板格式
     */
    @Test
    public void testParse2() {
        IResource resource = attachmentResource("test_imp2.test.xlsx");
        Object bean = ExcelHelper.loadXlsxObject("/nop/test/imp/test.imp.xml", resource);
        assertEquals(attachmentJsonText("imp-result.json"), JsonTool.serialize(bean, true));
    }
}
