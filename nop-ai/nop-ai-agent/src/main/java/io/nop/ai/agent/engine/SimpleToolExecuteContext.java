package io.nop.ai.agent.engine;

import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.fs.IToolFileSystem;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;

import java.io.File;
import java.util.Collections;
import java.util.Map;

public class SimpleToolExecuteContext implements IToolExecuteContext {

    private final File workDir;
    private final Map<String, String> envs;
    private final long expireAt;
    private final ICancelToken cancelToken;
    private final IToolFileSystem fileSystem;
    private final IThreadPoolExecutor executor;

    public SimpleToolExecuteContext(File workDir, IToolFileSystem fileSystem, IThreadPoolExecutor executor) {
        this(workDir, Collections.emptyMap(), 0L, null, fileSystem, executor);
    }

    public SimpleToolExecuteContext(File workDir,
                                   Map<String, String> envs,
                                   long expireAt,
                                   ICancelToken cancelToken,
                                   IToolFileSystem fileSystem,
                                   IThreadPoolExecutor executor) {
        this.workDir = workDir;
        this.envs = envs != null ? envs : Collections.emptyMap();
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
}
