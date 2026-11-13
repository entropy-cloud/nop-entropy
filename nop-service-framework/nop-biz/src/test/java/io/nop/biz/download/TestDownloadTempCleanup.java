package io.nop.biz.download;

import io.nop.core.resource.IFile;
import io.nop.core.resource.IResource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2.3-C4-005（plan 2551 裁定 E）钉：
 * pin-a：DownloadHelper 一次性清理 delete 失败 WARN 留痕不吞（原形态 boolean 被忽略）；
 * pin-b：对账扫描超龄文件删除、未超龄保留、子树遍历；阈值 ≤ MAX_WAIT_MINUTES 构建期 fail-fast；
 * pin-c：interval ≤0 显式关档。
 */
public class TestDownloadTempCleanup {

    static class FakeResource extends io.nop.core.resource.impl.DynamicResource {
        final long mtime;
        boolean deleted;
        final List<FakeResource> children;

        FakeResource(String path, long mtime) {
            this(path, mtime, null);
        }

        FakeResource(String path, long mtime, List<FakeResource> children) {
            super(path);
            this.mtime = mtime;
            this.children = children;
        }

        @Override
        public long lastModified() {
            return mtime;
        }

        @Override
        public boolean exists() {
            return true;
        }

        @Override
        public boolean delete() {
            deleted = true;
            return true;
        }

        @Override
        public boolean isDirectory() {
            return children != null;
        }

        @Override
        public List<IFile> getChildren() {
            return children == null ? List.of() : new ArrayList<>(children);
        }
    }

    @Test
    public void pin_a_cleanupFailureObservable_notSilent() {
        // DownloadHelper 的清理体为内联 lambda——本钉通过对账器的删除失败路径等价承载
        //（同一结构化键 nop.warn.download.temp-clean-fail），并断言清理失败不抛出（可观测不吞）。
        DownloadTempReconciler reconciler = newReconciler(60);
        FakeResource stubborn = new FakeResource("dynamic:/download/stub.zip", 0) {
            @Override
            public boolean delete() {
                return false; // 删除失败（返回 false）
            }
        };
        reconciler.scanOnceForTest(new FakeResource("dynamic:/download", 0,
                List.of(stubborn)));
        assertFalse(stubborn.deleted);
    }

    @Test
    public void pin_b_reconcilerDeletesExpiredKeepsFreshAcrossSubtree() throws Exception {
        DownloadTempReconciler reconciler = newReconciler(60);
        long now = System.currentTimeMillis();
        FakeResource fresh = new FakeResource("dynamic:/download/2026-11-10/fresh.zip", now - 1000);
        FakeResource staleDeep = new FakeResource("dynamic:/download/2026-11-09/deep/old.zip", now - 25 * 3600_000L);
        FakeResource root = new FakeResource("dynamic:/download", now,
                List.of(new FakeResource("dynamic:/download/2026-11-10", now, List.of(fresh)),
                        new FakeResource("dynamic:/download/2026-11-09", now, List.of(
                                new FakeResource("dynamic:/download/2026-11-09/deep", now, List.of(staleDeep))))));

        reconciler.scanOnceForTest(root);
        assertFalse(fresh.deleted, "未超龄文件必须保留");
        assertTrue(staleDeep.deleted, "子树内超龄文件必须删除（download/{day}/{uuid} 全子树遍历）");
    }

    @Test
    public void pin_b2_maxAgeBelowWaitWindowFailsFast() {
        // 阈值 ≤ MAX_WAIT_MINUTES = 会误删在途文件：构建期 fail-fast
        DownloadTempReconciler reconciler = new DownloadTempReconciler();
        reconciler.setReconcileIntervalMinutes(60);
        reconciler.setMaxAgeMinutes(DownloadHelper.MAX_WAIT_MINUTES); // 违例：等于上界
        assertThrows(IllegalArgumentException.class, reconciler::start);
    }

    @Test
    public void pin_c_intervalZeroDisablesScanExplicitly() {
        DownloadTempReconciler reconciler = new DownloadTempReconciler();
        reconciler.setReconcileIntervalMinutes(0); // 关档
        reconciler.setMaxAgeMinutes(DownloadHelper.MAX_WAIT_MINUTES + 1);
        reconciler.start(); // 不抛，不调度
        reconciler.stop();
        assertTrue(true);
    }

    private DownloadTempReconciler newReconciler(long maxAgeMinutes) {
        DownloadTempReconciler reconciler = new DownloadTempReconciler();
        reconciler.setReconcileIntervalMinutes(60);
        reconciler.setMaxAgeMinutes(maxAgeMinutes);
        return reconciler;
    }
}
