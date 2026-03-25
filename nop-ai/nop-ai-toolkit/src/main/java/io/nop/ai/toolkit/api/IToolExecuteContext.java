package io.nop.ai.toolkit.api;

import io.nop.ai.toolkit.fs.IToolFileSystem;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;
import java.io.File;
import java.util.Map;

public interface IToolExecuteContext {
    File getWorkDir();

    Map<String, String> getEnvs();

    long getExpireAt();

    ICancelToken getCancelToken();

    IToolFileSystem getFileSystem();

    IThreadPoolExecutor getExecutor();
}
