/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.util;

import com.google.common.collect.Range;
import io.nop.commons.env.PlatformEnv;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestFileHelper {

    @Test
    public void testCurrentPath() {
        File file = new File("", "precompile");
        System.out.println(file.getAbsolutePath());

        File file2 = new File(".", "precompile");
        System.out.println(file2.getAbsolutePath());
    }

    @Test
    public void testPath() throws Exception {
        if (PlatformEnv.isWindows()) {
            File file = new File("/c:/temp/");
            assertEquals("c:\\temp", file.getCanonicalPath().toLowerCase());
        }
    }

    @Test
    public void testFileUrl() throws Exception {
        URI uri = new URI("file:/c:/test.txt");
        assertEquals(null, uri.getHost());
        assertEquals("file:/c:/test.txt", uri.toString());
        assertEquals("/c:/test.txt", uri.getPath());

        uri = new URI("file:///c:/test.txt");
        assertEquals(null, uri.getHost());
        assertEquals("file:///c:/test.txt", uri.toString());
        assertEquals("/c:/test.txt", uri.getPath());
    }

    @Test
    public void testCanonicalUrl() {
        File file = new File("C:/test.txt");
        assertEquals(FileHelper.getFileUrl(file), file.toURI().toString());
    }

    @Test
    public void testRename() {
        File file = FileHelper.getAttachmentFile(TestFileHelper.class, "test.txt");
        FileHelper.writeText(file, "aa", null);
        assertTrue(file.exists());

        assertEquals("aa", FileHelper.readText(file, null));
        assertEquals("test.txt", file.getName());

        File target = new File(file.getParentFile(), "b.txt");
        target.delete();

        boolean b = file.renameTo(target);
        assertTrue(b, target + ",exists=" + target.exists());

        assertTrue(target.exists());
        assertEquals("test.txt", file.getName());
    }

    @Test
    public void testGetJarFile() {
        File file = FileHelper.getJarFile(Range.class);
        System.out.println(file.getAbsolutePath());
        assertTrue(file.exists());
    }
}