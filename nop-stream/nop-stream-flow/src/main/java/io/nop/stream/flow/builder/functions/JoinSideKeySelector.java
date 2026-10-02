/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.builder.functions;

import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.model.JoinSideRecord;

/**
 * WI13: routes the union of both side-tagged join inputs by their pre-computed
 * equi-key (evaluated once in {@link JoinSideTagFunction} — this selector reads
 * the value; same-key records from either side land on the same keyed subtask).
 */
public final class JoinSideKeySelector implements KeySelector<JoinSideRecord<Object>, Object> {

    private static final long serialVersionUID = 1L;

    public static final JoinSideKeySelector INSTANCE = new JoinSideKeySelector();

    private JoinSideKeySelector() {
    }

    @Override
    public Object getKey(JoinSideRecord<Object> value) {
        return value.getEquiKey();
    }
}
