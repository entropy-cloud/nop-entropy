/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.testing;

import java.io.Serializable;
import java.util.Objects;

/**
 * WI13 test-only join payload: a keyed record ({@code key} drives the equi-join,
 * {@code val} identifies the row). Lives under {@code io.nop.stream.} so tagged
 * join records survive the transport class-name whitelist.
 */
public final class JoinTestRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String key;
    private final String val;

    public JoinTestRecord(String key, String val) {
        this.key = key;
        this.val = val;
    }

    public String getKey() {
        return key;
    }

    public String getVal() {
        return val;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof JoinTestRecord)) {
            return false;
        }
        JoinTestRecord that = (JoinTestRecord) o;
        return Objects.equals(key, that.key) && Objects.equals(val, that.val);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, val);
    }

    @Override
    public String toString() {
        return key + ":" + val;
    }
}
