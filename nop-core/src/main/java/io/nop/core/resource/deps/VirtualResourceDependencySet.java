/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.deps;

import io.nop.api.core.resource.IResourceReference;
import io.nop.api.core.time.CoreMetrics;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

public class VirtualResourceDependencySet extends ResourceDependencySet {
    public VirtualResourceDependencySet(String resourcePath) {
        // 这个依赖集合在ResourceCacheEntry中使用，cacheEntry中的path不一定是资源路径，可能只是约定的一个key
        super(new MockReference("virtual:" + resourcePath));
    }

    public boolean isMock() {
        return true;
    }

    static class MockReference implements IResourceReference {
        private final String path;
        private final long lastModified = CoreMetrics.currentTimeMillis();

        public MockReference(String path) {
            this.path = path;
        }

        @Override
        public String getPath() {
            return path;
        }

        @Override
        public long lastModified() {
            return lastModified;
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
}
