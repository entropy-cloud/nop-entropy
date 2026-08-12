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
 */
public class CredentialData implements Serializable {
    private static final long serialVersionUID = 1L;

    private final Map<String, Object> fields;

    public CredentialData(Map<String, Object> fields) {
        this.fields = fields != null ? fields : Collections.emptyMap();
    }

    public Map<String, Object> getFields() {
        return fields;
    }

    public Object getField(String name) {
        return fields.get(name);
    }
}
