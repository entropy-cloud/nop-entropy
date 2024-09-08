/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xpl;

import io.nop.commons.concurrent.executor.DefaultScheduledExecutor;
import io.nop.commons.concurrent.executor.IRateLimitExecutor;
import io.nop.commons.concurrent.executor.RateLimitExecutorImpl;
import io.nop.commons.util.FileHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.resource.deps.ResourceDependencySet;
import io.nop.core.resource.impl.FileResource;
import io.nop.core.resource.watch.IFileWatchListener;
import io.nop.core.resource.watch.NioFileWatchService;
import io.nop.core.unittest.BaseTestCase;
import io.nop.xlang.xpl.impl.XplTaskResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestXplTask extends BaseTestCase {
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @DisplayName("监控xtask文件发生改变的时候自动重新运行")
    @Test
    public void testWatch() {
        DefaultScheduledExecutor scheduledExecutor = DefaultScheduledExecutor.newSingleThreadTimer("test");
        IRateLimitExecutor executor = new RateLimitExecutorImpl(scheduledExecutor);
        NioFileWatchService watchService = new NioFileWatchService();
        watchService.start();

        File resultDir = new File(getTargetDir(), "/test-classes/_vfs/test/tasks");
        FileHelper.writeText(new File(resultDir, "result.txt"), "abc", null);
        FileHelper.writeText(new File(resultDir, "b.xpl"), "<c:unit/>", null);

        XplTaskResult result = (XplTaskResult) ResourceComponentManager.instance()
                .loadComponentModel("/test/tasks/a.xtask");
        Object value = result.getReturnValue();

        result = (XplTaskResult) ResourceComponentManager.instance().loadComponentModel("/test/tasks/a.xtask");
        ResourceDependencySet deps = ResourceComponentManager.instance().getResourceDepends("/test/tasks/a.xtask");
        System.out.println(ResourceComponentManager.instance().dumpDependsSet(deps));

        assertTrue(!deps.getDepends().isEmpty());
        assertTrue(deps.getDepends().contains("classpath:_vfs/test/tasks/b.xpl"));

        // 重复加载会使用缓存，因此结果不变
        assertTrue(value == result.getReturnValue());

        IResource resultResource = VirtualFileSystem.instance().getResource("classpath:_vfs/test/tasks/result.txt");
        String resultTxt = ResourceHelper.readText(resultResource);
        assertEquals(resultTxt, value.toString());

        watchService.watch(resultDir.toPath(), path -> Files.isDirectory(path) || path.toString().endsWith(".xtask")
                || path.toString().endsWith(".xpl"), true, new IFileWatchListener() {
            @Override
            public void onFileChange(Path root, Path path) {
                System.out.println("file change:" + path);
                // fileChange回调的时候文件系统中的文件时间戳还没有被修改
                executor.debounce("refresh", 100, () -> {
                    ResourceComponentManager.instance().loadComponentModel("/test/tasks/a.xtask");
                    System.out.println("rerun a.xtask");
                });
            }

            @Override
            public void onFileCreate(Path root, Path path) {
                System.out.println("file create:" + path);
            }

            @Override
            public void onFileDelete(Path root, Path path) {
                System.out.println("file delete:" + path);
            }
        });

        IResource resource = new FileResource(new File(resultDir, "b.xpl"));
        long lastModified = resource.lastModified();
        XNode node = ResourceHelper.readXml(resource);
        node.appendChild(XNode.make("c:unit"));
        ResourceHelper.writeXml(resource, node);
        assertNotEquals(lastModified, resource.lastModified());
        System.out.println("modify b.xpl");

        try {
            Thread.sleep(500);
        } catch (Exception e) {
        }

        // 依赖文件修改导致xtask自动重新运行，产生不同的输出文件
        String text = ResourceHelper.readText(resultResource);
        if(text.equals(value.toString())){
            try {
                Thread.sleep(1000);
            } catch (Exception e) {
            }
            text = ResourceHelper.readText(resultResource);
        }
        assertNotEquals(text, value.toString());

        watchService.stop();
        scheduledExecutor.destroy();
    }
}
