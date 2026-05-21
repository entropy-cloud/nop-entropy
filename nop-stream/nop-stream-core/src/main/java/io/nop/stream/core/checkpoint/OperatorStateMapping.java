/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint;

import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;
import java.util.Objects;

@DataBean
public class OperatorStateMapping implements Serializable {

    private static final long serialVersionUID = 1L;

    private final int operatorIndex;
    private final String operatorStateKey;
    private final String keyedStateStorageKey;
    private final boolean isTwoPhaseCommit;

    public OperatorStateMapping(int operatorIndex, String operatorStateKey,
                                String keyedStateStorageKey, boolean isTwoPhaseCommit) {
        this.operatorIndex = operatorIndex;
        this.operatorStateKey = operatorStateKey;
        this.keyedStateStorageKey = keyedStateStorageKey;
        this.isTwoPhaseCommit = isTwoPhaseCommit;
    }

    public OperatorStateMapping() {
        this(0, "operator-0", null, false);
    }

    public int getOperatorIndex() { return operatorIndex; }
    public String getOperatorStateKey() { return operatorStateKey; }
    public String getKeyedStateStorageKey() { return keyedStateStorageKey; }
    public boolean isTwoPhaseCommit() { return isTwoPhaseCommit; }
    public boolean hasKeyedState() { return keyedStateStorageKey != null; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OperatorStateMapping that = (OperatorStateMapping) o;
        return operatorIndex == that.operatorIndex &&
                isTwoPhaseCommit == that.isTwoPhaseCommit &&
                Objects.equals(operatorStateKey, that.operatorStateKey) &&
                Objects.equals(keyedStateStorageKey, that.keyedStateStorageKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operatorIndex, operatorStateKey, keyedStateStorageKey, isTwoPhaseCommit);
    }

    @Override
    public String toString() {
        return "OperatorStateMapping{" +
                "operatorIndex=" + operatorIndex +
                ", operatorStateKey='" + operatorStateKey + '\'' +
                ", keyedStateStorageKey='" + keyedStateStorageKey + '\'' +
                ", isTwoPhaseCommit=" + isTwoPhaseCommit +
                '}';
    }
}
