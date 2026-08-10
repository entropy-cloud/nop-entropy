package io.nop.datav.service.chatbi;

import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.fs.IToolFileSystem;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;

import java.io.File;
import java.util.Collections;
import java.util.Map;

/**
 * ChatBI 最小 {@link IToolExecuteContext} 实现（裁定 C + 裁定 G）。
 *
 * <p>DB 查询类 executor（datav-list/describe/query）不依赖 {@code workDir}/{@code envs}/
 * {@code fileSystem}/{@code executor} 字段（仅可能用 {@code cancelToken}），故这些字段安全置为
 * null/空。参考 {@code SimpleToolExecuteContext}（nop-ai-agent）的轻量实现模式，但 ChatBI 不引入
 * nop-ai-agent 依赖。</p>
 *
 * <p>{@code getCompactionArchiveReader()} 继承接口的默认 UOE 实现（read-ref 工具不适用于 ChatBI）。</p>
 *
 * <p><b>D6-1b 扩展（裁定 G）</b>：新增 {@code operator} 字段，携带当前调用者身份，供
 * {@link DatavGenerateDashboardExecutor} 经强转读取并手动填充实体的 {@code createdBy}/{@code updatedBy}
 * 审计列。operator 是 ChatBI 循环在 {@code run()} 入参中接收、构建 context 时写入的（per-request），
 * 本 context 是 per-call 新建的，故线程安全。</p>
 */
public class ChatBiToolExecuteContext implements IToolExecuteContext {

    private final ICancelToken cancelToken;
    private final String operator;

    public ChatBiToolExecuteContext() {
        this(null, null);
    }

    public ChatBiToolExecuteContext(ICancelToken cancelToken) {
        this(cancelToken, null);
    }

    /**
     * @param cancelToken 取消令牌
     * @param operator    当前调用者身份（裁定 G；查询路径可传 null，生成路径必传以填充 createdBy）
     */
    public ChatBiToolExecuteContext(ICancelToken cancelToken, String operator) {
        this.cancelToken = cancelToken;
        this.operator = operator;
    }

    /**
     * 返回当前调用者身份（D6-1b 裁定 G）。生成路径 executor 经强转读取此值填充实体审计列。
     *
     * @return operator；未设置时返回 null（查询路径不依赖此字段）
     */
    public String getOperator() {
        return operator;
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
