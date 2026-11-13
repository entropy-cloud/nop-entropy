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
import org.junit.jupiter.api.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * xtask链路测试：watch→debounce→checkChanged→模型重载整条链（C10-005+N3验收前置，plan 2500恢复）。
 * 类级@Disabled已解除，改为方法级处置：
 * <li>testCheckChangedInvalidationRerunsTask：checkChanged失效链活测试（依赖修改→判据检出→模型重载→xtask重跑），
 *     绕开watch/debounce触发形态的等效链路（plan 2500 F3备选分支），本批恢复执行；</li>
 * <li>testWatch：方法级@Disabled（F3预案，C1-004批外缺陷隔离，非整类回退）——反事实基线验证@8cc5e219e2
 *     （零产品改动、仅移除类级@Disabled）testWatch同样在同点assertNotEquals失败（重跑未发生），
 *     失败面为watch→debounce触发管道（C1-004 debounce非原子，归R4-13 open，不在本批成员面），
 *     与本批checkChanged判据修复无关。R4-13对端回归义务传导：R4-13修复debounce原子性后应解除本方法隔离
 *     （连同基线证据 _tmp/phase1b-xpltask-baseline-counterfactual.log 复核）。</li>
 */
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
    @Disabled("C1-004批外缺陷方法级隔离（plan 2500 F3预案）：watch→debounce触发管道失败为基线既有"
            + "（反事实基线@8cc5e219e2仅移除类级@Disabled即同点失败，见类注记），归R4-13；"
            + "checkChanged失效链断言由本类其余断言+nop-core TestResourceCacheInvalidationChain等效链路测试承载")
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
        assertTrue(deps.getDependPaths().contains("classpath:_vfs/test/tasks/b.xpl"));

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
        System.out.println("b.xpl:"+new Timestamp(lastModified));
        XNode node = ResourceHelper.readXml(resource);
        node.appendChild(XNode.make("c:unit"));
        ResourceHelper.writeXml(resource, node);
        assertNotEquals(lastModified, resource.lastModified());
        System.out.println("modify b.xpl：" + new Timestamp(resource.lastModified()));

        try {
            Thread.sleep(500);
        } catch (Exception e) {
            // 批前既有：watch→debounce链路等待被中断时按超时语义继续（测试线程随即收尾），有意忽略
        }

        // 依赖文件修改导致xtask自动重新运行，产生不同的输出文件
        String text = ResourceHelper.readText(resultResource);
        if(text.equals(value.toString())){
            try {
                Thread.sleep(1000);
            } catch (Exception e) {
                // 批前既有：重试等待被中断时按超时语义继续，有意忽略
            }
            text = ResourceHelper.readText(resultResource);
        }
        assertNotEquals(text, value.toString());

        watchService.stop();
        scheduledExecutor.destroy();
    }

    private static void spinAtLeast(long durationMillis) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < durationMillis) {
            // busy-wait（零sleep确定性，等待refresh-min-interval门间隔）
        }
    }

    /**
     * checkChanged失效链活测试（C10-005冒烟，plan 2500恢复）：依赖文件真实修改→载体判据检出→模型重载→xtask重跑。
     * 绕开watch/debounce触发形态（C1-004隔离面），直接经loadComponentModel门路径走完整失效链
     */
    @DisplayName("依赖修改经checkChanged判据触发xtask重跑")
    @Test
    public void testCheckChangedInvalidationRerunsTask() {
        File resultDir = new File(getTargetDir(), "/test-classes/_vfs/test/tasks");
        FileHelper.writeText(new File(resultDir, "result.txt"), "abc", null);
        FileHelper.writeText(new File(resultDir, "b.xpl"), "<c:unit/>", null);

        XplTaskResult result = (XplTaskResult) ResourceComponentManager.instance()
                .loadComponentModel("/test/tasks/a.xtask");
        Object value = result.getReturnValue();

        // 依赖登记断言：xtask依赖b.xpl经编译路径钉定
        ResourceDependencySet deps = ResourceComponentManager.instance().getResourceDepends("/test/tasks/a.xtask");
        assertTrue(!deps.getDepends().isEmpty());
        assertTrue(deps.getDependPaths().contains("classpath:_vfs/test/tasks/b.xpl"));

        // 缓存命中：重复装载同实例
        result = (XplTaskResult) ResourceComponentManager.instance().loadComponentModel("/test/tasks/a.xtask");
        assertTrue(value == result.getReturnValue());

        // 依赖文件真实修改（内容+mtime变更）
        IResource resource = new FileResource(new File(resultDir, "b.xpl"));
        long lastModified = resource.lastModified();
        XNode node = ResourceHelper.readXml(resource);
        node.appendChild(XNode.make("c:unit"));
        ResourceHelper.writeXml(resource, node);
        assertNotEquals(lastModified, resource.lastModified());

        // 越过refresh-min-interval门间隔后重新装载：checkChanged检出变更→模型重载（新实例）→xtask重跑生成新result
        spinAtLeast(130);
        XplTaskResult reloaded = (XplTaskResult) ResourceComponentManager.instance()
                .loadComponentModel("/test/tasks/a.xtask");
        assertNotSame(value, reloaded.getReturnValue());
        IResource resultResource = VirtualFileSystem.instance().getResource("classpath:_vfs/test/tasks/result.txt");
        String text = ResourceHelper.readText(resultResource);
        assertNotEquals(text, value.toString());
    }
}
