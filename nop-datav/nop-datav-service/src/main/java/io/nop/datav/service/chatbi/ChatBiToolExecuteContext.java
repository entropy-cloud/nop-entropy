package io.nop.datav.service.chatbi;

import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.fs.IToolFileSystem;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;

import java.io.File;
import java.util.Collections;
import java.util.Map;

/**
 * ChatBI 最小 {@link IToolExecuteContext} 实现（裁定 C）。
 *
 * <p>DB 查询类 executor（datav-list/describe/query）不依赖 {@code workDir}/{@code envs}/
 * {@code fileSystem}/{@code executor} 字段（仅可能用 {@code cancelToken}），故这些字段安全置为
 * null/空。参考 {@code SimpleToolExecuteContext}（nop-ai-agent）的轻量实现模式，但 ChatBI 不引入
 * nop-ai-agent 依赖。</p>
 *
 * <p>{@code getCompactionArchiveReader()} 继承接口的默认 UOE 实现（read-ref 工具不适用于 ChatBI）。</p>
 */
public class ChatBiToolExecuteContext implements IToolExecuteContext {

    private final ICancelToken cancelToken;

    public ChatBiToolExecuteContext() {
        this(null);
    }

    public ChatBiToolExecuteContext(ICancelToken cancelToken) {
        this.cancelToken = cancelToken;
    }

    @Override
    public File getWorkDir() {
        return null;
    }

    @Override
    public Map<String, String> getEnvs() {
        return Collections.emptyMap();
    }

    @Override
    public long getExpireAt() {
        return 0L;
    }

    @Override
    public ICancelToken getCancelToken() {
        return cancelToken;
    }

    @Override
    public IToolFileSystem getFileSystem() {
        return null;
    }

    @Override
    public IThreadPoolExecutor getExecutor() {
        return null;
    }
}
