package io.nop.ai.toolkit.api;

import io.nop.ai.toolkit.fs.IToolFileSystem;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;
import java.io.File;
import java.util.Map;

/**
 * Execution context passed to tool executors and interceptors for a single tool call.
 * <p>
 * Carries the sandboxed work directory, environment variables, expiry deadline,
 * cancellation token, the sandboxed file system, and the executor used for
 * asynchronous work. The default implementation ({@code ToolExecuteContext} in
 * nop-ai-toolkit) exposes immutable values (envs are unmodifiable).
 */
public interface IToolExecuteContext {
    /**
     * @return the sandboxed working directory of the tool call
     */
    File getWorkDir();

    /**
     * @return the environment variables available to the tool call (may be empty, never null)
     */
    Map<String, String> getEnvs();

    /**
     * @return the epoch-millis deadline after which the tool call is considered expired
     */
    long getExpireAt();

    /**
     * @return the cancellation token for the tool call (may be null when cancellation is not supported)
     */
    ICancelToken getCancelToken();

    /**
     * @return the sandboxed file system available to the tool call
     */
    IToolFileSystem getFileSystem();

    /**
     * @return the executor used for asynchronous work within the tool call
     */
    IThreadPoolExecutor getExecutor();
}
