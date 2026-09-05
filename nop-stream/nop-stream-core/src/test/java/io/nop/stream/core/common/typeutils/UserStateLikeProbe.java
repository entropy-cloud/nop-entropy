/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.typeutils;

import java.io.Serializable;
import java.util.Objects;

/**
 * F-10a test fixture: a {@code Serializable} state class UNDER the {@code io.nop.*}
 * baseline prefix — must always round-trip through the JEP 290 whitelist filter.
 */
public class UserStateLikeProbe implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String value;

    public UserStateLikeProbe(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UserStateLikeProbe)) {
            return false;
        }
        return Objects.equals(value, ((UserStateLikeProbe) o).value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }
}
