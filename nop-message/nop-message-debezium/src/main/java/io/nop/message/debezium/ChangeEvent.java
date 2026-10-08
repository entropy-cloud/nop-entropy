/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.message.debezium;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.stream.ChangeSemanticView;
import io.nop.api.core.stream.RowKind;

import java.util.Map;

/**
 * 表示一个 CDC 变更事件
 */
@DataBean
public class ChangeEvent implements ChangeSemanticView {

    /**
     * 事件元数据
     */
    private final ChangeEventMetadata metadata;

    /**
     * 操作类型: c (create/insert), u (update), d (delete), r (read/snapshot)
     */
    private final String operation;

    /**
     * 变更前的数据 (仅 update 和 delete 操作)
     */
    private final Map<String, Object> before;

    /**
     * 变更后的数据 (仅 create 和 update 操作)
     */
    private final Map<String, Object> after;

    /**
     * 主键值
     */
    private final Map<String, Object> key;

    /**
     * 事件时间戳 (毫秒)
     */
    private final long timestamp;

    public ChangeEvent(ChangeEventMetadata metadata, String operation,
                       Map<String, Object> before, Map<String, Object> after,
                       Map<String, Object> key, long timestamp) {
        this.metadata = metadata;
        this.operation = operation;
        this.before = before;
        this.after = after;
        this.key = key;
        this.timestamp = timestamp;
    }

    public ChangeEventMetadata getMetadata() {
        return metadata;
    }

    public String getOperation() {
        return operation;
    }

    public Map<String, Object> getBefore() {
        return before;
    }

    public Map<String, Object> getAfter() {
        return after;
    }

    public Map<String, Object> getKey() {
        return key;
    }

    public long getTimestamp() {
        return timestamp;
    }

    /**
     * 是否是插入操作
     */
    public boolean isCreate() {
        return "c".equals(operation);
    }

    /**
     * 是否是更新操作
     */
    public boolean isUpdate() {
        return "u".equals(operation);
    }

    /**
     * 是否是删除操作
     */
    public boolean isDelete() {
        return "d".equals(operation);
    }

    /**
     * 是否是快照读取
     */
    public boolean isRead() {
        return "r".equals(operation);
    }

    @Override
    public String toString() {
        return "ChangeEvent{" +
                "metadata=" + metadata +
                ", operation='" + operation + '\'' +
                ", key=" + key +
                ", timestamp=" + timestamp +
                '}';
    }
    /**
     * WI7 (plan 2336): engine-level change-semantics view. The stream engine stamps
     * the built StreamRecord with this metadata at source emission, making the change
     * kind and table identity visible to the engine and its sinks without
     * connector-specific coupling. c/r map to INSERT, u to UPDATE_AFTER, d to DELETE;
     * a null/unknown operation maps to INSERT (the original operation still rides the
     * value for sinks to reject on their own contract).
     */
    @Override
    public RowKind getRowKind() {
        if ("u".equals(operation)) {
            return RowKind.UPDATE_AFTER;
        }
        if ("d".equals(operation)) {
            return RowKind.DELETE;
        }
        // c / r / null / unknown -> insert-only append
        return RowKind.INSERT;
    }

    @Override
    public String getTableId() {
        return metadata == null ? null : metadata.getTable();
    }

}
