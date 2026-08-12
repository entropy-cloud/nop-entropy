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
 * 脱敏后的凭证视图。敏感字段已替换为 {@code ****}，非敏感字段按规则截断，
 * 可安全用于展示或跨进程传输。
 */
public class MaskedCredential implements Serializable {
    private static final long serialVersionUID = 1L;

    private final Map<String, String> fields;

    public MaskedCredential(Map<String, String> fields) {
        this.fields = fields != null ? fields : Collections.emptyMap();
    }

    public Map<String, String> getFields() {
        return fields;
    }
}
