/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.connector;

import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.exceptions.StreamException;

import java.util.List;

/**
 * WI4 (plan 2329): read-only data-sampling contract for source endpoints, consumed by
 * the {@link StreamDataSampler} driver behind the {@code sample} maintenance command.
 *
 * <p><strong>Side-effect budget</strong> (same red-line family as the dry-run
 * connectivity probes, connectors.md pre-submit table): a sample is a read-only scan —
 * no records are committed anywhere, no split is assigned, no consumer/subscription is
 * created, and no offset is advanced. Endpoints whose contract cannot guarantee this
 * (all sinks, the message family, debezium's embedded engine, the batch loader's
 * no-consumption red line) must NOT implement this interface — the sampler reports
 * them as an explicit SKIP with a reason instead of returning an empty list.
 *
 * <p>Record type is endpoint-owned ({@code file} yields {@code String} text lines);
 * {@code limit} is the global upper bound across the endpoint's whole scan, with no
 * cross-file ordering promise beyond the endpoint's native discovery order.
 */
public interface SampleableSource {

    /**
     * Reads up to {@code limit} records (read-only; see the side-effect budget above).
     *
     * @throws StreamException typed ({@code ERR_STREAM_NULL_ARG}) when {@code limit <= 0}
     */
    List<Object> sample(int limit);
}
