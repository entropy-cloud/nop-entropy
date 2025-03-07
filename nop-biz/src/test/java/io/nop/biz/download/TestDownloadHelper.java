package io.nop.biz.download;

import io.nop.api.core.beans.WebContentBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.IoHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.zip.IZipInput;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestDownloadHelper extends BaseTestCase {

    @BeforeAll
    public static void beforeAll() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void afterAll() {
        CoreInitialization.destroy();
    }

    @Test
    public void testDownload() throws IOException {
        WebContentBean result = DownloadHelper.downloadZip("test.zip", 1, out -> {
            try {
                out.addDataEntry("a.txt", "abc".getBytes(StandardCharsets.UTF_8));
                out.addDataEntry("b.txt", "def".getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                throw NopException.adapt(e);
            }
        }, null);

        File resource = (File) result.getContent();
        IZipInput zipIn = ResourceHelper.getZipTool().getZipInputForFile(resource, null);
        zipIn.unzipToLocalDir(getTargetFile("test"), null);
        IoHelper.safeCloseObject(zipIn);

        String text = FileHelper.readText(getTargetFile("test/a.txt"), null);
        assertEquals("abc", text);
    }
}
