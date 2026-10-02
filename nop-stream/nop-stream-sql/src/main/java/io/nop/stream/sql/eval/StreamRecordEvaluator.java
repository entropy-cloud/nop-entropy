/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.sql.eval;

import java.io.Serializable;

/**
 * A compiled scalar expression over one stream record (WI9). Implementations are
 * Serializable — they ride inside operator chains that are deep-copied per subtask via
 * Java serialization.
 *
 * <p>Records may be {@link Map}s (column key lookup) or beans (property getter
 * lookup); missing columns fail fast, never silently return null.
 */
@FunctionalInterface
public interface StreamRecordEvaluator extends Serializable {

    /**
     * Evaluates the compiled expression against one record.
     *
     * @param record the stream record (Map or bean shape)
     * @return the evaluation result, possibly null (null-propagating semantics)
     */
    Object eval(Object record);
}
