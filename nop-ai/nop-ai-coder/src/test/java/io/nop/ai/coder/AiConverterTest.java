package io.nop.ai.coder;

import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.converter.DocumentConvertOptions;
import io.nop.converter.DocumentConverterManager;
import io.nop.converter.registration.ConverterRegistrationBean;
import io.nop.core.resource.IResource;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class AiConverterTest extends JunitBaseTestCase {
    @Inject
    ConverterRegistrationBean registrationBean;

    @Test
    public void testConvertOrm() {
        IResource aiOrmResource = attachmentResource("test.ai-orm.xml");
        IResource ormResource = getTargetResource("result/test.orm.xml");
        IResource xlsxResource = getTargetResource("result/test.orm.xlsx");
        IResource javaResource = getTargetResource("result/test.orm.java");

        DocumentConvertOptions options = DocumentConvertOptions.create().allowChained();
        DocumentConverterManager manager = DocumentConverterManager.instance();
        manager.convertResource(aiOrmResource, ormResource, options);
        manager.convertResource(aiOrmResource, xlsxResource, options);
        manager.convertResource(aiOrmResource, javaResource, options);

        String ormXml = ormResource.readText();
        assertTrue(ormXml.contains("<orm"), "orm.xml should contain orm root: " + firstLine(ormXml));
        assertTrue(ormXml.contains("<entity"), "orm.xml should contain entities");
        assertTrue(ormXml.contains("tableName"), "orm.xml should contain normalized table names");

        assertTrue(xlsxResource.exists(), "converted xlsx should exist");
        assertTrue(xlsxResource.length() > 0, "converted xlsx should not be empty");

        String javaCode = javaResource.readText();
        assertTrue(javaCode.contains("class"), "converted java should contain class declarations");
    }

    @Test
    public void testConvertXDef() {
        IResource resource = getResource("/nop/ai/schema/coder/workbook.xdef");
        IResource toResource = getTargetResource("result/workbook.ai-xdef.xml");

        DocumentConverterManager manager = DocumentConverterManager.instance();
        DocumentConvertOptions options = DocumentConvertOptions.create();

        manager.convertResource(resource, toResource, options);
        assertTrue(toResource.readText().contains("horizontalAlign=\"enum:general|left|center|right|fill|justify|centerSelection|distributed\""));
    }

    @Test
    public void testConvertExcel(){
        IResource resource = attachmentResource("test.workbook.xml");
        IResource toResource = getTargetResource("result/result.xlsx");

        DocumentConverterManager manager = DocumentConverterManager.instance();
        DocumentConvertOptions options = DocumentConvertOptions.create();

        manager.convertResource(resource,toResource, options);

        assertTrue(toResource.exists(), "converted xlsx should exist");
        assertTrue(toResource.length() > 0, "converted xlsx should not be empty");
    }

    private static String firstLine(String text) {
        int pos = text.indexOf('\n');
        return pos < 0 ? text : text.substring(0, pos);
    }
}
