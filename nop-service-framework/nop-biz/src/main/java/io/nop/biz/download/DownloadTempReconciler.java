/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.biz.download;

import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.core.resource.IFile;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 2.3-C4-005（plan 2551 裁定 E）：下载临时文件的平台级兜底对账。一次性清理任务挂进程内
 * globalTimer——进程重启/清理失败（原形态删除失败静默）都会遗留文件永驻。本对账器周期扫描
 * {@code temp:/download} 整树（含 download/{day}/{uuid} 子树），删除 mtime 超过
 * {@code nop.download.temp-max-age} 的遗留文件；删除失败/异常 WARN 留痕。
 *
 * <p>约束（R2-n-2 锚定）：temp-max-age 必须 &gt; {@link DownloadHelper#MAX_WAIT_MINUTES}
 * （在途下载等待窗上界）——构建期校验违例 fail-fast，防止对账误删在途文件。
 *
 * <p>缺省关档：interval ≤ 0 时不启动扫描（显式语义，非静默跳过）。
 */
@Singleton
public class DownloadTempReconciler {
    private static final Logger LOG = LoggerFactory.getLogger(DownloadTempReconciler.class);

    public static final String TEMP_ROOT = "/download";

    /** 扫描间隔分钟（键 nop.download.temp-reconcile-interval，缺省 60；≤0=关档） */
    private long reconcileIntervalMinutes = 60;

    /** 遗留文件年龄阈值分钟（键 nop.download.temp-max-age，缺省 24h；必须 > MAX_WAIT_MINUTES） */
    private long maxAgeMinutes = 24 * 60;

    private Future<?> scanFuture;

    @InjectValue("@cfg:nop.download.temp-reconcile-interval|60")
    public void setReconcileIntervalMinutes(long reconcileIntervalMinutes) {
        this.reconcileIntervalMinutes = reconcileIntervalMinutes;
    }

    @InjectValue("@cfg:nop.download.temp-max-age|1440")
    public void setMaxAgeMinutes(long maxAgeMinutes) {
        this.maxAgeMinutes = maxAgeMinutes;
    }

    @PostConstruct
    public void start() {
        if (maxAgeMinutes <= DownloadHelper.MAX_WAIT_MINUTES) {
            // 约束校验 fail-fast：阈值低于在途下载等待窗上界会误删在途文件
            throw new IllegalArgumentException(
                    "nop.download.temp-max-age must be greater than DownloadHelper.MAX_WAIT_MINUTES ("
                            + DownloadHelper.MAX_WAIT_MINUTES + "): " + maxAgeMinutes);
        }
        if (reconcileIntervalMinutes <= 0) {
            // 显式关档（非静默跳过）
            LOG.info("nop.download.temp-reconcile-disabled");
            return;
        }
        scanFuture = GlobalExecutors.globalTimer().executeOn(GlobalExecutors.globalWorker())
                .scheduleWithFixedDelay(this::scanOnce, reconcileIntervalMinutes, reconcileIntervalMinutes,
                        TimeUnit.MINUTES);
    }

    @PreDestroy
    public void stop() {
        if (scanFuture != null) {
            scanFuture.cancel(false);
            scanFuture = null;
        }
    }

    /** 单轮对账（包级可见供测试直调）：深度优先遍历 temp:/download 整树 */
    void scanOnce() {
        IResource root = ResourceHelper.getTempResource(TEMP_ROOT);
        scanOnceForTest(root);
    }

    /** 测试缝：显式根扫描 */
    void scanOnceForTest(IResource root) {
        try {
            long expireBefore = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(maxAgeMinutes);
            if (root instanceof IFile) {
                scanFile((IFile) root, expireBefore, 0);
            } else if (root.exists() && !root.isDirectory()) {
                deleteExpired(root, expireBefore);
            }
        } catch (Throwable e) {
            // 扫描自身异常不得杀死周期链（periodic-task insulation 契约）
            LOG.warn("nop.warn.download.temp-reconcile-fail", e);
        }
    }

    private void scanFile(IFile dir, long expireBefore, int depth) {
        if (depth > 16) {
            // 深度护栏：目录树异常深时停止（留 WARN，不静默）
            LOG.warn("nop.warn.download.temp-reconcile-depth-limit:path={}", dir.getPath());
            return;
        }
        if (dir.isDirectory()) {
            List<IFile> children = dir.getChildren();
            if (children != null) {
                for (IFile child : children) {
                    scanFile(child, expireBefore, depth + 1);
                }
            }
            return;
        }
        deleteExpired(dir, expireBefore);
    }

    private void deleteExpired(IResource resource, long expireBefore) {
        long mtime = resource.lastModified();
        if (mtime > 0 && mtime < expireBefore) {
            try {
                if (!resource.delete()) {
                    LOG.warn("nop.warn.download.temp-clean-fail:path={},reason=reconcile-delete-returned-false",
                            resource.getPath());
                } else {
                    LOG.debug("nop.download.temp-reconcile-deleted:path={}", resource.getPath());
                }
            } catch (Throwable e) {
                LOG.warn("nop.warn.download.temp-clean-fail:path={}", resource.getPath(), e);
            }
        }
    }
}
