package io.nop.converter;

import io.nop.api.core.annotations.autotest.EnableSnapshot;
import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.converter.registration.ConverterRegistrationBean;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

//@Disabled
@NopTestConfig(localDb = true,debug=true)
public class DocumentConverterTest extends JunitAutoTestCase {

    @Inject
    ConverterRegistrationBean registrationBean;

    @EnableSnapshot
    @Test
    public void testConvert() {
        IResource xlsxResource = inputResource("/test.orm.xlsx");
        IResource xmlResource = getTargetResource("/test.orm.xml");
        IResource workbookResource = getTargetResource("/test.orm.workbook.xml");
        IResource htmlResource = getTargetResource("/test.orm.html");

        DocumentConverterManager manager = DocumentConverterManager.instance();

        manager.convertResource(xlsxResource, xmlResource, false);

        outputText("test.orm.xml", ResourceHelper.readText(xmlResource));

        manager.convertResource(xlsxResource, workbookResource, false);
        outputText("test.orm.workbook.xml", ResourceHelper.readText(workbookResource));

        manager.convertResource(workbookResource, htmlResource, false);
        outputText("test.orm.html", ResourceHelper.readText(htmlResource));
    }
}
