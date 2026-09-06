/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.store;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestDefaultVirtualFileSystemRefresh {

    private static boolean isZipClosed(ZipFile zipFile) {
        try {
            zipFile.size();
            return false;
        } catch (IllegalStateException e) {
            // zip file closed
            return true;
        }
    }

    /**
     * refresh必须先构建并发布新store、最后才关闭旧的zipFiles。
     * 如果先关闭旧zip再重建，构建窗口期内读线程通过volatile读到旧store并访问
     * 其中的ZipEntryResource会抛出zip file closed异常
     */
    @Test
    public void testRefreshClosesOldZipOnlyAfterRebuild() throws Exception {
        File zipFile = File.createTempFile("nop-core-test", ".zip");
        zipFile.deleteOnExit();
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zipFile))) {
            // 创建空zip
        }

        try (ZipFile oldZip = new ZipFile(zipFile)) {
            AtomicBoolean closedDuringRebuild = new AtomicBoolean();

            DefaultVirtualFileSystem vfs = new DefaultVirtualFileSystem() {
                @Override
                protected void buildResourceStore() {
                    // 模拟重建耗时期间检查旧zip是否已被提前关闭
                    closedDuringRebuild.set(isZipClosed(oldZip));
                }
            };

            Field field = DefaultVirtualFileSystem.class.getDeclaredField("zipFiles");
            field.setAccessible(true);
            field.set(vfs, List.of(oldZip));

            vfs.refresh(false);

            assertFalse(closedDuringRebuild.get(), "old zip files must not be closed while rebuilding resource store");
            assertTrue(isZipClosed(oldZip), "old zip files should be closed after refresh");
        }
    }
}
