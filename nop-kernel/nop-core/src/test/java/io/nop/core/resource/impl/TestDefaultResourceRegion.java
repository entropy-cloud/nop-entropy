package io.nop.core.resource.impl;

import io.nop.api.core.beans.LongRangeBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.FileHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static io.nop.core.CoreErrors.ERR_RESOURCE_NOT_CONTAINS_FILE_RANGE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestDefaultResourceRegion {
    static File file;
    static final String CONTENT = "0123456789abcdef";

    @BeforeAll
    public static void setup() throws Exception {
        file = new File("target/test-resource-region.txt");
        Files.write(file.toPath(), CONTENT.getBytes(StandardCharsets.UTF_8));
    }

    @AfterAll
    public static void cleanup() {
        file.delete();
    }

    private DefaultResourceRegion region(long offset, long limit) {
        FileResource resource = new FileResource(FileHelper.getFileUrl(file), file);
        return new DefaultResourceRegion(resource, new LongRangeBean(offset, limit));
    }

    @Test
    public void testReadFullRange() throws Exception {
        DefaultResourceRegion region = region(0, CONTENT.length());
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        region.writeToStream(os);
        assertEquals(CONTENT, os.toString("UTF-8"));
        assertEquals(0, region.getRange().getOffset());
    }

    @Test
    public void testReadPartialRangeWithOffset() throws Exception {
        // offset=5, limit=6 -> "56789a"
        DefaultResourceRegion region = region(5, 6);
        byte[] bytes = region.getInputStream().readAllBytes();
        assertEquals(CONTENT.substring(5, 11), new String(bytes, StandardCharsets.UTF_8));
    }

    @Test
    public void testOffsetBeyondSizeThrows() {
        NopException e = assertThrows(NopException.class, () -> region(CONTENT.length() + 5, 1).getInputStream());
        assertEquals(ERR_RESOURCE_NOT_CONTAINS_FILE_RANGE.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testRangeEndBeyondSizeThrows() {
        // offset+limit 超过文件长度应报错
        NopException e = assertThrows(NopException.class, () -> region(10, 100).getInputStream());
        assertEquals(ERR_RESOURCE_NOT_CONTAINS_FILE_RANGE.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testToStringContainsRangeAndResource() {
        String text = region(0, 4).toString();
        assertTrue(text.contains("ResourceRegion"));
        assertTrue(text.contains("test-resource-region.txt"));
    }

    @Test
    public void testNullRangeRejected() {
        FileResource resource = new FileResource(FileHelper.getFileUrl(file), file);
        assertThrows(IllegalArgumentException.class, () -> new DefaultResourceRegion(resource, null),
                "null range should be rejected by Guard");
    }
}
