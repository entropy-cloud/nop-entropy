/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.credential.api;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;

/**
 * 解密后的凭证数据。包装凭证字段名到字段值的映射（明文，仅存在于服务进程内）。
 *
 * <p>该对象是明文边界之内的唯一解密产物，不应跨进程传输或序列化到日志。
 *
 * <p><b>W16-impl SPI 增量</b>：新增可选 {@code typeName} 属性（凭证实例的类型名，additive、
 * 向后兼容——旧构造路径 typeName 为 null）。消费方经 {@code getCredential} 拿到的数据可据此做
 * 家族错型校验（如 "yunpian-sms 凭证误用于 tencent-sms 渠道"的精确判定，而非仅靠字段缺失
 * 间接失败）。
 */
public class CredentialData implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String typeName;
    private final Map<String, Object> fields;

    public CredentialData(Map<String, Object> fields) {
        this(null, fields);
    }

    public CredentialData(String typeName, Map<String, Object> fields) {
        this.typeName = typeName;
        this.fields = fields != null ? fields : Collections.emptyMap();
    }

    /**
     * 凭证实例的类型名（如 {@code tencent-sms}）；旧构造路径为 null。
     */
    public String getTypeName() {
        return typeName;
    }

    public Map<String, Object> getFields() {
        return fields;
    }

    public Object getField(String name) {
        return fields.get(name);
    }
}
