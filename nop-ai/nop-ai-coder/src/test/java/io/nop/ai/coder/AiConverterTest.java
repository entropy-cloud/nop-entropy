package io.nop.ai.coder;

import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.converter.DocumentConverterManager;
import io.nop.converter.registration.ConverterRegistrationBean;
import io.nop.core.resource.IResource;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

public class AiConverterTest extends JunitBaseTestCase {
    @Inject
    ConverterRegistrationBean registrationBean;

    @Test
    public void testConvertOrm() {
        IResource aiOrmResource = attachmentResource("test.ai-orm.xml");
        IResource ormResource = getTargetResource("result/test.orm.xml");
        IResource xlsxResource = getTargetResource("result/test.orm.xlsx");
        IResource javaResource = getTargetResource("result/test.orm.java");

        DocumentConverterManager manager = DocumentConverterManager.instance();
        manager.convertResource(aiOrmResource, ormResource, true);
        manager.convertResource(aiOrmResource, xlsxResource, true);
        manager.convertResource(aiOrmResource, javaResource, true);
    }
}
