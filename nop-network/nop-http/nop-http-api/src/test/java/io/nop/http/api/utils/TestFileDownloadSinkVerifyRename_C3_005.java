package io.nop.http.api.utils;

import io.nop.http.api.client.DownloadOptions;
import io.nop.http.api.client.IHttpOutputFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C3-005 (plan 2522, error-delivered-as-success family #4): with no checksum source,
 * verifyAndRename must still rename the .part into the target file — the defect
 * returns early and the download reports success while the target file never appears
 * (the .part stays behind).
 * 当前即红 = 先行失败测试（非并发零 trial 义务）。
 */
public class TestFileDownloadSinkVerifyRename_C3_005 {

    @TempDir
    Path tempDir;

    private IHttpOutputFile outputOf(Path target) {
        // toFile() returning the target enables file mode when resume is on
        return new IHttpOutputFile() {
            @Override
            public File toFile() {
                return target.toFile();
            }

            @Override
            public java.io.OutputStream getOutputStream() {
                try {
                    return Files.newOutputStream(target);
                } catch (IOException e) {
                    throw io.nop.api.core.exceptions.NopException.adapt(e);
                }
            }
        };
    }

    @Test
    public void noChecksumSourceStillRenamesPartToTarget() throws IOException {
        Path target = tempDir.resolve("content.bin");
        DownloadOptions options = new DownloadOptions();
        options.setResume(true);

        FileDownloadSink sink = new FileDownloadSink(outputOf(target), options, null);
        byte[] content = "hello download".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        sink.begin(200, null, content.length);
        sink.write(content, 0, content.length);
        sink.finish();
        sink.verifyAndRename(null);
        sink.close();

        assertTrue(Files.exists(target),
                "C3-005: verifyAndRename(null) must rename the .part into the target file"
                        + " (defect: early return — success reported, target never produced)");
        assertEquals(content.length, Files.size(target), "renamed target must hold the payload");
        File part = FileTransferHelper.partFileOf(target.toFile());
        if (part != null) {
            assertFalse(part.exists(), "the .part must be gone after the rename");
        }
    }

    @Test
    public void checksumMismatchStillDeletesPart() throws IOException {
        Path target = tempDir.resolve("guarded.bin");
        DownloadOptions options = new DownloadOptions();
        options.setResume(true);

        FileDownloadSink sink = new FileDownloadSink(outputOf(target), options, null);
        byte[] content = "corrupt payload".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        sink.begin(200, null, content.length);
        sink.write(content, 0, content.length);
        sink.finish();

        FileTransferHelper.ExpectedChecksum expected = FileTransferHelper.ExpectedChecksum
                .of(FileTransferHelper.SHA256, "deadbeef");

        boolean threw = false;
        try {
            sink.verifyAndRename(expected);
        } catch (Exception e) {
            threw = true;
        }
        assertTrue(threw, "checksum mismatch must throw");
        assertFalse(Files.exists(target), "failed checksum must not produce the target");
        File part = FileTransferHelper.partFileOf(target.toFile());
        if (part != null)
            assertFalse(part.exists(), "failed checksum must delete the .part (existing semantics)");
    }
}
