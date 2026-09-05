/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.connector;

/**
 * Item 20 (P-REQ-13, pre-submit-validation-design.md D3-②): additive connectivity
 * capability marker for connector endpoints (same pattern as {@link DrainableSource}).
 * Endpoints that can verify their external system reachability without starting the
 * job implement this interface; the dry-run driver dispatches on it first.
 *
 * <p>Contract:
 * <ul>
 *   <li>Must not start the endpoint's data path (no engine, no consumption cursor).</li>
 *   <li>Must leave no residue the job's normal run would not create itself — only
 *       idempotent expected objects (ledger table via DDL, output directory) are
 *       allowed (D3-⑥ red line).</li>
 *   <li>Unreachable/misconfigured external system must throw — never return silently.</li>
 * </ul>
 *
 * <p>Implementing families: jdbc-2pc sink, file 2PC sink, batch-loader source,
 * debezium-cdc source (construction/parameter level), batch-consumer sink
 * (construction-level already verified). Families without a probe contract (message
 * source/sink, third-party endpoints) simply do not implement it — the dry-run report
 * carries an explicit "not supported" item for them.
 */
public interface ConnectivityCheckable {

    /**
     * Verifies connectivity to the external system / validity of the endpoint
     * configuration without starting the data path.
     *
     * @throws Exception when the external system is unreachable or the configuration
     *                   is invalid (fail-fast; the dry-run driver converts this into
     *                   a structured failed probe item)
     */
    void checkConnection() throws Exception;
}
