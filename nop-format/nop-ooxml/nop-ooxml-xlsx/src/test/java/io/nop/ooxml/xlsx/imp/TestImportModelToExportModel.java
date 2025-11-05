package io.nop.ooxml.xlsx.imp;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.unittest.BaseTestCase;
import io.nop.excel.imp.model.ImportModel;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.ooxml.xlsx.util.ExcelHelper;
import io.nop.xlang.xdsl.DslModelHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class TestImportModelToExportModel extends BaseTestCase {
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testTransform() {
        IResource resource = VirtualFileSystem.instance().getResource("/test/test-imp-to-excel.imp.xml");
        ImportModel model = (ImportModel) DslModelHelper.loadDslModel(resource);
        ImportModelToExportModel transform = new ImportModelToExportModel();
        ExcelWorkbook wk = transform.build(model);

        IResource targetResource = getTargetResource("test-imp-to-excel.xlsx");
        ExcelHelper.saveExcel(targetResource, wk);
    }
}
