/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:   https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.impl.FileResource;
import io.nop.core.resource.impl.UnknownResource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestResourceHelper {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testName() {
        assertEquals("test", ResourceHelper.getName("c:/a/test"));
        assertEquals("test", ResourceHelper.getName("c:test"));
    }

    @Test
    public void testCheckValidPath(){
        ResourceHelper.checkNormalVirtualPath("/_delta/default/nop/web/xlib/web/impl_GenPage.xpl");
    }

    @Test
    public void testGetResourceRejectsDotSegments() throws Exception {
        File dir = Files.createTempDirectory("nop-resource-test").toFile();
        try {
            IFile file = new FileResource(dir);
            // 整段 ".."/"." 不能作为 relativeName，否则 FileResource 会逃逸到父目录
            assertThrows(IllegalArgumentException.class, () -> file.getResource(".."));
            assertThrows(IllegalArgumentException.class, () -> file.getResource("."));
            assertThrows(IllegalArgumentException.class, () -> file.getResource("a/.."));
            assertThrows(IllegalArgumentException.class, () -> file.getResource("./.."));

            assertNotNull(file.getResource("a/b.txt"));
        } finally {
            FileHelper.deleteAll(dir);
        }
    }

    @Test
    public void testResolveResourceInDirRejectsParentEscape() {
        assertThrows(NopException.class, () -> ResourceHelper.resolveResourceInDir("/data/app", ".."));
        assertThrows(NopException.class, () -> ResourceHelper.resolveResourceInDir("/data/app", "./.."));
        assertThrows(NopException.class, () -> ResourceHelper.resolveResourceInDir("/data/app", "a/../b"));

        // 正常文件名仍然解析到dir内部
        IResource resource = ResourceHelper.resolveResourceInDir("/data/app", "a/b.txt");
        assertEquals("/data/app/a/b.txt", resource.getStdPath());
    }

    @Test
    public void testResolveRelativeResourceRejectParent() {
        IResource base = new FileResource(new File("/data/app/base.txt"));
        assertThrows(NopException.class, () -> ResourceHelper.resolveRelativeResource(base, "..", false));
        assertThrows(NopException.class, () -> ResourceHelper.resolveRelativeResource(base, "a/../b", false));
    }

    @Test
    public void testReadBytesRejectsOversizedResource() {
        UnknownResource resource = new UnknownResource("virtual:/test/large.bin") {
            @Override
            public long length() {
                return Integer.MAX_VALUE + 1L;
            }

            @Override
            public InputStream getInputStream() {
                return new ByteArrayInputStream(StringHelper.EMPTY_BYTES);
            }
        };
        // 超过int范围的资源不能直接做 new byte[(int)length] 截断
        assertThrows(NopException.class, () -> ResourceHelper.readBytes(resource));
    }

    @Test
    public void testFileResourceMkdirs() throws Exception {
        File dir = Files.createTempDirectory("nop-mkdirs-test").toFile();
        try {
            FileResource resource = new FileResource(new File(dir, "a/b"));
            // 目录不存在时应创建目录并返回true
            assertTrue(resource.mkdirs());
            assertTrue(new File(dir, "a/b").isDirectory());

            // 目录已存在时返回false（与File.mkdirs语义一致）
            assertFalse(resource.mkdirs());

            // 已存在同名文件时是明确的错误
            File file = new File(dir, "ex.txt");
            assertTrue(file.createNewFile());
            assertThrows(NopException.class, () -> new FileResource(file).mkdirs());
        } finally {
            FileHelper.deleteAll(dir);
        }
    }
}
