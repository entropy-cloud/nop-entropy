package io.nop.core.resource.deps;

import io.nop.api.core.resource.IResourceReference;
import io.nop.api.core.time.CoreMetrics;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 判据值二元组(lastModified, recordedAt)承载与簿记规则单测（plan 2500裁定①/编辑点4）：
 * 宽限锚仅在lastModified值变化时重置（重读同值不重锚），防止热条目宽限窗口永续。
 */
public class TestResourceDependencySet {

    static class FixedReference implements IResourceReference {
        final String path;
        final long mtime;

        FixedReference(String path, long mtime) {
            this.path = path;
            this.mtime = mtime;
        }

        @Override
        public String getPath() {
            return path;
        }

        @Override
        public long lastModified() {
            return mtime;
        }

        @Override
        public long length() {
            return 0;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(new byte[0]);
        }
    }

    @Test
    public void testConstructionRecordsGraceAnchor() {
        long before = CoreMetrics.currentTimeMillis();
        ResourceDependencySet deps = new ResourceDependencySet(new FixedReference("/test/a.model", 1000L));
        long after = CoreMetrics.currentTimeMillis();

        assertEquals(1000L, deps.getLastModified());
        // 构造即锚定：recordedAt为判据值记录时刻
        assertTrue(deps.getRecordedAt() >= before && deps.getRecordedAt() <= after);
    }

    @Test
    public void testUpdateLastModifiedReanchorsOnlyOnValueChange() {
        ResourceDependencySet deps = new ResourceDependencySet(new FixedReference("/test/a.model", 1000L));
        long anchor1 = deps.getRecordedAt();

        // 重读同值：不重锚
        deps.updateLastModified(1000L, anchor1 + 10000L);
        assertEquals(1000L, deps.getLastModified());
        assertEquals(anchor1, deps.getRecordedAt());

        // 值变化：重锚
        deps.updateLastModified(2000L, anchor1 + 20000L);
        assertEquals(2000L, deps.getLastModified());
        assertEquals(anchor1 + 20000L, deps.getRecordedAt());
    }

    @Test
    public void testSetLastModifiedRoutesThroughAnchoredBookkeeping() {
        ResourceDependencySet deps = new ResourceDependencySet(new FixedReference("/test/a.model", 1000L));
        long anchor1 = deps.getRecordedAt();

        // 同值涂新（isDependencyChanged涂新点的常态）：锚不重置
        deps.setLastModified(1000L);
        assertEquals(anchor1, deps.getRecordedAt());
    }

    @Test
    public void testCopyCarriesRecordedAt() {
        ResourceDependencySet deps = new ResourceDependencySet(new FixedReference("/test/a.model", 1000L));
        deps.updateLastModified(2000L, 123456789L);

        ResourceDependencySet copy = deps.copy();
        assertEquals(2000L, copy.getLastModified());
        assertEquals(123456789L, copy.getRecordedAt());
    }
}
