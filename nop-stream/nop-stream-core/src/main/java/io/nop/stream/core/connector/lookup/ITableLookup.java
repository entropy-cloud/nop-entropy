/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.connector.lookup;

import java.io.Serializable;

/**
 * The stream-side dimension-table access contract (WI14). Static dimension-table
 * lookups MUST go through the Nop platform data-access facades rather than
 * self-managed data sources (roadmap §III #8): application implementations bridge
 * this interface to {@code IJdbcTemplate} (nop-dao) or {@code IBatchLoader}
 * (nop-batch-core) and inject it into the streaming operator as a bean property.
 * nop-stream-core carries no data-access dependency — the facade lives in the
 * application composition.
 *
 * <p>Implementations must be Serializable when injected into operators that are
 * deep-copied per subtask.
 */
public interface ITableLookup extends Serializable {

    /**
     * Looks up one dimension-table record by key.
     *
     * @param key the lookup key extracted from the stream record
     * @return the record value, or null when the key has no dimension row
     */
    Object lookup(Object key);
}
