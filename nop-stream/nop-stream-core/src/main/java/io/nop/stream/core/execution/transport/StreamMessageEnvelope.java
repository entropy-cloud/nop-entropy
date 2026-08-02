/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution.transport;

import java.io.Serializable;

import io.nop.api.core.annotations.data.DataBean;

/**
 * 统一消息信封格式，用于跨 TaskManager 通信。
 *
 * <p>携带单调递增的 fencing epoch（{@code epochId}）以支持 fencing 机制，
 * 通过 type 字段标识载荷类型，valueType 字段记录 StreamRecord 载荷的具体 Java 类型名。
 *
 * <p><strong>Stage 39 fencing 统一</strong>：原复合表示（{@code String fencingToken} +
 * {@code long epochId} 双键过滤）已收敛为单一 {@code long epochId} 单调 fencing epoch 比较。
 * 该 epoch 同时编码 leadership 切换与同 leader 内 recovery（见
 * {@code JobCoordinator.deriveHaFencingEpoch}），数据面仅按该 long 值等值/比较过滤，
 * 同时满足「stale leader 旧 epoch 被拒」与「同 leader 上一轮 recovery task 被拒」两个不变量。
 */
@DataBean
public class StreamMessageEnvelope implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 信封类型常量 */
    public static final String TYPE_STREAM_RECORD = "STREAM_RECORD";
    public static final String TYPE_CHECKPOINT_BARRIER = "CHECKPOINT_BARRIER";
    public static final String TYPE_WATERMARK = "WATERMARK";
    public static final String TYPE_WATERMARK_STATUS = "WATERMARK_STATUS";
    public static final String TYPE_CONTROL = "CONTROL";

    /**
     * 单调 fencing epoch。leadership 切换与同 leader 内 recovery 均推进该值
     * （见 {@code JobCoordinator}）。数据面按该 long 值过滤 stale envelope。
     */
    private long epochId;

    /** 信封类型：STREAM_RECORD, CHECKPOINT_BARRIER, WATERMARK, WATERMARK_STATUS, CONTROL */
    private String type;

    /** 载荷的 Java 类型名（仅 STREAM_RECORD 使用） */
    private String valueType;

    /** 序列化后的载荷数据 */
    private Object payload;

    /** StreamRecord 的时间戳（仅 STREAM_RECORD 使用） */
    private long timestamp;

    /** StreamRecord 是否设置了时间戳（仅 STREAM_RECORD 使用） */
    private boolean hasTimestamp;

    public StreamMessageEnvelope() {
    }

    public StreamMessageEnvelope(long epochId, String type, String valueType, Object payload) {
        this(epochId, type, valueType, payload, 0, false);
    }

    public StreamMessageEnvelope(long epochId, String type, String valueType,
                                  Object payload, long timestamp, boolean hasTimestamp) {
        this.epochId = epochId;
        this.type = type;
        this.valueType = valueType;
        this.payload = payload;
        this.timestamp = timestamp;
        this.hasTimestamp = hasTimestamp;
    }

    public long getEpochId() {
        return epochId;
    }

    public void setEpochId(long epochId) {
        this.epochId = epochId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getValueType() {
        return valueType;
    }

    public void setValueType(String valueType) {
        this.valueType = valueType;
    }

    public Object getPayload() {
        return payload;
    }

    public void setPayload(Object payload) {
        this.payload = payload;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isHasTimestamp() {
        return hasTimestamp;
    }

    public void setHasTimestamp(boolean hasTimestamp) {
        this.hasTimestamp = hasTimestamp;
    }

    @Override
    public String toString() {
        return "StreamMessageEnvelope{" +
                "epochId=" + epochId +
                ", type='" + type + '\'' +
                ", valueType='" + valueType + '\'' +
                ", payload=" + payload +
                '}';
    }
}
