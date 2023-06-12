package io.nop.core.resource.deps;

import io.nop.api.core.resource.IResourceReference;
import io.nop.api.core.time.CoreMetrics;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

public class VirtualResourceDependencySet extends ResourceDependencySet {
    public VirtualResourceDependencySet(String resourcePath) {
        super(new MockReference(resourcePath));
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
