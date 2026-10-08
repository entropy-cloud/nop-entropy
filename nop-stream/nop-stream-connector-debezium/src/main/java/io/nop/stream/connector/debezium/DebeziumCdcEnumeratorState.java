/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.debezium;

import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * WI2 (plan 2343 Phase 3): enumerator checkpoint state — the tables NOT yet handed to
 * any reader. Assigned tables live in the readers' split state (D-3: restore truth), so
 * a restored enumerator only re-offers never-assigned tables.
 */
@DataBean
public class DebeziumCdcEnumeratorState implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<String> unassignedTables = new ArrayList<>();
    private int nextSubtaskIndex;

    public DebeziumCdcEnumeratorState() {
    }

    public DebeziumCdcEnumeratorState(List<String> unassignedTables, int nextSubtaskIndex) {
        this.unassignedTables = unassignedTables;
        this.nextSubtaskIndex = nextSubtaskIndex;
    }

    public List<String> getUnassignedTables() {
        return unassignedTables;
    }

    public void setUnassignedTables(List<String> unassignedTables) {
        this.unassignedTables = unassignedTables;
    }

    public int getNextSubtaskIndex() {
        return nextSubtaskIndex;
    }

    public void setNextSubtaskIndex(int nextSubtaskIndex) {
        this.nextSubtaskIndex = nextSubtaskIndex;
    }
}
