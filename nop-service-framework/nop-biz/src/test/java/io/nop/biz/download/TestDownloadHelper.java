package io.nop.biz.download;

import io.nop.api.core.beans.WebContentBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.IoHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.zip.IZipInput;
import io.nop.core.resource.zip.IZipOutput;
import io.nop.core.resource.zip.IZipTool;
import io.nop.core.resource.zip.ZipOptions;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    private static class TrackingOutputStream extends OutputStream {
        boolean closed;

        @Override
        public void write(int b) {
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    @Test
    public void testNewZipOutputFailureClosesUnderlyingStream() throws Exception {
        TrackingOutputStream os = new TrackingOutputStream();

        IResource resource = (IResource) java.lang.reflect.Proxy.newProxyInstance(
                TestDownloadHelper.class.getClassLoader(),
                new Class[]{IResource.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getPath":
                            return "test-resource";
                        case "getOutputStream":
                            return os;
                        case "delete":
                            return true;
                        default:
                            if (method.getReturnType() == boolean.class)
                                return false;
                            return null;
                    }
                });

        IZipTool brokenTool = new IZipTool() {
            @Override
            public IZipOutput newZipOutput(OutputStream out, ZipOptions options) {
                throw new java.io.UncheckedIOException(new IOException("zip tool init fail"));
            }

            @Override
            public io.nop.core.resource.zip.IZipInput newZipInput(InputStream in, ZipOptions options) {
                return null;
            }
        };

        // 修复前：newZipOutput抛错时底层os未关闭，文件描述符泄漏
        assertThrows(RuntimeException.class,
                () -> DownloadHelper.downloadZip(resource, brokenTool, "test.zip", 1, out -> {
                }, null));
        assertTrue(os.closed, "underlying output stream should be closed on failure");
    }
}
