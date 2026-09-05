/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package com.example.stream;

import java.io.Serializable;

/**
 * F-10a (plan 2026-09-04-1326-3) test fixture: a user-defined state class in a
 * THIRD-PARTY package (outside the {@code io.nop.*}/JDK baseline of
 * {@code StreamDeserializationFilter}). Deliberately NOT under {@code io.nop.} so the
 * native-deserialization whitelist can be exercised honestly: without the escape hatch
 * this class must be REJECTED; with its prefix declared via
 * {@code nop.stream.state.deserialize.allowed-prefixes} it must restore.
 */
public class UserStatePayload implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String value;

    public UserStatePayload(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
