package io.nop.converter;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.converter.registration.ConverterRegistrationBean;
import io.nop.converter.utils.DocConvertHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

//@Disabled
@NopTestConfig
public class DocumentConverterTest extends JunitAutoTestCase {

    @Inject
    ConverterRegistrationBean registrationBean;

    @Test
    public void testConvert() {
        IResource xlsxResource = inputResource("/test.orm.xlsx");
        IResource xmlResource = getTargetResource("/test.orm.xml");
        IResource workbookResource = getTargetResource("/test.orm.workbook.xml");
        IResource htmlResource = getTargetResource("/test.orm.html");

        DocumentConverterManager manager = DocumentConverterManager.instance();

        DocumentConvertOptions options = DocumentConvertOptions.create();

        manager.convertResource(xlsxResource, xmlResource, options);

        outputText("test.orm.xml", ResourceHelper.readText(xmlResource));

        manager.convertResource(xlsxResource, workbookResource, options);
        outputText("test.orm.workbook.xml", ResourceHelper.readText(workbookResource));

        manager.convertResource(workbookResource, htmlResource, options);
        outputText("test.orm.html", ResourceHelper.readText(htmlResource));
    }

    @Test
    public void testConvertSameType() {
        IResource resource = inputResource("test-ext.orm.xml");
        IResource outResource = getTargetResource("test-ext.orm.xml");
        DocumentConverterManager manager = DocumentConverterManager.instance();
        DocumentConvertOptions options = DocumentConvertOptions.create();
        manager.convertResource(resource, outResource, options);
        outputText("test-ext.orm.xml", ResourceHelper.readText(outResource));
    }

    @Test
    public void testMerge() {
        IResource xmlResource = inputResource("/test.orm.xml");
        IResource xlsxResource = getTargetResource("/result/test.orm.xlsx");

        IResource mergedResource = getTargetResource("/result/merged.orm.xml");
        DocumentConverterManager manager = DocumentConverterManager.instance();
        DocumentConvertOptions options = DocumentConvertOptions.create();
        manager.convertResource(xmlResource, xlsxResource, options);

        DocConvertHelper.mergeAndConvertResources(Arrays.asList(xlsxResource, xmlResource), mergedResource);
        outputText("merged.orm.xml", ResourceHelper.readText(mergedResource));
    }
}
