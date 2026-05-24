/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution.transport;

import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;

/**
 * 统一消息信封格式，用于跨 TaskManager 通信。
 *
 * <p>携带 fencing token 和 epoch id 以支持 fencing 机制，
 * 通过 type 字段标识载荷类型，valueType 字段记录 StreamRecord 载荷的具体 Java 类型名。
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

    /** fencing token，用于标识当前活跃的 job/master */
    private String fencingToken;

    /** epoch id，单调递增，配合 fencing token 实现双 epoch fencing */
    private long epochId;

    /** 信封类型：STREAM_RECORD, CHECKPOINT_BARRIER, WATERMARK, WATERMARK_STATUS, CONTROL */
    private String type;

    /** 载荷的 Java 类型名（仅 STREAM_RECORD 使用） */
    private String valueType;

    /** 序列化后的载荷数据 */
    private Object payload;

    public StreamMessageEnvelope() {
    }

    public StreamMessageEnvelope(String fencingToken, long epochId, String type, String valueType, Object payload) {
        this.fencingToken = fencingToken;
        this.epochId = epochId;
        this.type = type;
        this.valueType = valueType;
        this.payload = payload;
    }

    public String getFencingToken() {
        return fencingToken;
    }

    public void setFencingToken(String fencingToken) {
        this.fencingToken = fencingToken;
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

    @Override
    public String toString() {
        return "StreamMessageEnvelope{" +
                "fencingToken='" + fencingToken + '\'' +
                ", epochId=" + epochId +
                ", type='" + type + '\'' +
                ", valueType='" + valueType + '\'' +
                ", payload=" + payload +
                '}';
    }
}
