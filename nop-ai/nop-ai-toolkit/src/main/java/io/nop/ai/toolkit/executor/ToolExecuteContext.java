package io.nop.ai.toolkit.executor;

import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.fs.IToolFileSystem;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;
import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ToolExecuteContext implements IToolExecuteContext {
    private final File workDir;
    private final Map<String, String> envs;
    private final long expireAt;
    private final ICancelToken cancelToken;
    private final IToolFileSystem fileSystem;
    private final IThreadPoolExecutor executor;

    public ToolExecuteContext(File workDir, Map<String, String> envs,
                               long expireAt, ICancelToken cancelToken,
                               IToolFileSystem fileSystem, IThreadPoolExecutor executor) {
        this.workDir = workDir;
        this.envs = envs != null ? Collections.unmodifiableMap(envs) : Collections.emptyMap();
        this.expireAt = expireAt;
        this.cancelToken = cancelToken;
        this.fileSystem = fileSystem;
        this.executor = executor;
    }



    @Override
    public File getWorkDir() {
        return workDir;
    }

    @Override
    public Map<String, String> getEnvs() {
        return envs;
    }

    @Override
    public long getExpireAt() {
        return expireAt;
    }

    @Override
    public ICancelToken getCancelToken() {
        return cancelToken;
    }

    @Override
    public IToolFileSystem getFileSystem() {
        return fileSystem;
    }

    @Override
    public IThreadPoolExecutor getExecutor() {
        return executor;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private File workDir = new File(".").getAbsoluteFile();
        private Map<String, String> envs = new HashMap<>();
        private long expireAt = Long.MAX_VALUE;
        private ICancelToken cancelToken;
        private IToolFileSystem fileSystem;
        private IThreadPoolExecutor executor;

        public Builder workDir(File workDir) {
            this.workDir = workDir;
            return this;
        }

        public Builder env(String key, String value) {
            this.envs.put(key, value);
            return this;
        }

        public Builder envs(Map<String, String> envs) {
            this.envs.putAll(envs);
            return this;
        }

        public Builder expireAt(long expireAt) {
            this.expireAt = expireAt;
            return this;
        }

        public Builder cancelToken(ICancelToken cancelToken) {
            this.cancelToken = cancelToken;
            return this;
        }

        public Builder fileSystem(IToolFileSystem fileSystem) {
            this.fileSystem = fileSystem;
            return this;
        }

        public Builder executor(IThreadPoolExecutor executor) {
            this.executor = executor;
            return this;
        }

        public IToolExecuteContext build() {
            return new ToolExecuteContext(workDir, envs, expireAt, cancelToken, fileSystem, executor);
        }
    }
}
