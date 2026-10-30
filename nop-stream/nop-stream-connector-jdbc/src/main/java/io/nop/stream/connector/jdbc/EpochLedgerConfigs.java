/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.connector.jdbc;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;

import java.time.Duration;

import static io.nop.api.core.config.AppConfig.varRef;

/**
 * Plan 2511 C3-002 (R01 ledger-retention-policy SSOT member row, face token
 * {@code epochledger}): typed config keys for the JDBC epoch ledger
 * ({@code stream_epoch_ledger_v2}) retention cleanup. Parameter values are owned by
 * <code>ai-dev/design/concurrency/ledger-retention-policy.md</code> §3.1 — this
 * interface only mirrors the SSOT defaults; any revision goes through the SSOT, not
 * through this file.
 */
public interface EpochLedgerConfigs {
    SourceLocation s_loc = SourceLocation.fromClass(EpochLedgerConfigs.class);

    @Description("2PC epoch 台账行保留天数（0=显式禁用周期清理；负值装配期 fail-fast）")
    IConfigReference<Integer> CFG_LEDGER_RETENTION_DAYS = varRef(s_loc,
            "nop.stream.ledger.epochledger.retention-days", Integer.class, 30);

    @Description("台账周期清理触发周期（下限 60s，低于下限装配期 fail-fast）")
    IConfigReference<Duration> CFG_LEDGER_CLEANUP_INTERVAL = varRef(s_loc,
            "nop.stream.ledger.epochledger.cleanup-interval", Duration.class, Duration.ofHours(1));

    @Description("单轮清理按 committed_at 有界拾取并批删的最大行数（<=0 装配期 fail-fast）")
    IConfigReference<Integer> CFG_LEDGER_DELETE_BATCH_SIZE = varRef(s_loc,
            "nop.stream.ledger.epochledger.delete-batch-size", Integer.class, 1000);
}
