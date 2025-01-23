package io.nop.core.resource;

import io.nop.commons.util.FileHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.impl.URLResource;
import io.nop.core.resource.zip.ZipResourceLocator;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestZipResource extends BaseTestCase {
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testJarURL() throws Exception {
        String path = FileHelper.getFileUrl(attachmentFile("a.zip!/a.txt"));

        // 可以用jar协议去读取普通的zip文件
        URL url = new URI("jar:" + path).toURL();
        URLResource resource = new URLResource("/a.txt", url);
        assertEquals("a.txt", resource.getName());
        assertEquals("ass", resource.readText());
    }

    @Test
    public void testZipResourceLocator() {
        ZipResourceLocator locator = ZipResourceLocator.INSTANCE;
        IResource resource = attachmentResource("a.zip");
        IResource entryResource = locator.getResource(resource.getPath() + "!/a.txt");
        assertEquals("ass", entryResource.readText());
    }
}
