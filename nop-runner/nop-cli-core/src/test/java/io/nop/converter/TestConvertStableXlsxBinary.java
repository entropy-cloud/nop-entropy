package io.nop.converter;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.converter.registration.ConverterRegistrationBean;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.impl.FileResource;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.security.MessageDigest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@NopTestConfig
public class TestConvertStableXlsxBinary extends JunitAutoTestCase {

    @Inject
    ConverterRegistrationBean registrationBean;

    @Test
    public void testXmlToXlsxUsesSourceTimestampForStableBinary() throws Exception {
        File repoRoot = new File("../..").getCanonicalFile();
        IResource xmlResource = new FileResource(new File(repoRoot, "nop-auth/model/nop-auth.orm.xml"));
        IResource firstXlsx = getTargetResource("/stable/result-1.orm.xlsx");
        IResource secondXlsx = getTargetResource("/stable/result-2.orm.xlsx");

        DocumentConverterManager manager = DocumentConverterManager.instance();

        DocumentConvertOptions firstOptions = DocumentConvertOptions.create();
        firstOptions.setProperty(io.nop.converter.utils.DocConvertHelper.OPTION_ZIP_ENTRY_TIME, xmlResource.lastModified());
        manager.convertResource(xmlResource, firstXlsx, firstOptions);

        Thread.sleep(2200L);

        DocumentConvertOptions secondOptions = DocumentConvertOptions.create();
        secondOptions.setProperty(io.nop.converter.utils.DocConvertHelper.OPTION_ZIP_ENTRY_TIME, xmlResource.lastModified());
        manager.convertResource(xmlResource, secondXlsx, secondOptions);

        assertEquals(sha256(ResourceHelper.readBytes(firstXlsx)), sha256(ResourceHelper.readBytes(secondXlsx)));
    }

    private static String sha256(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : digest.digest(bytes)) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }
}
