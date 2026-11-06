/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.rocksdb;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;

import static io.nop.api.core.config.AppConfig.varRef;

/**
 * plan 2511 C5-002 (adjudication ⑤): RocksDB state-backend envelope configs.
 *
 * <p>The incremental SST checkpoint path is the default; a deployment that
 * explicitly opts out into the full-scan JSON path gets a materialize-entry
 * bound guard — above the bound the snapshot fails typed with guidance to
 * return to the incremental path (explicit failure over periodic OOM).
 */
public interface NopStreamRocksdbConfigs {
    SourceLocation s_loc = SourceLocation.fromClass(NopStreamRocksdbConfigs.class);

    @Description("Maximum number of state entries the full-scan (non-incremental) "
            + "RocksDB checkpoint may materialize into the heap before it fails "
            + "typed with guidance to use the incremental path. <= 0 disables the "
            + "guard (explicit self-assumed OOM risk).")
    IConfigReference<Long> SNAPSHOT_MATERIALIZE_MAX_ENTRIES =
            varRef(s_loc, "nop.stream.rocksdb.snapshot-materialize-max-entries", Long.class, 100000L);

    /**
     * CRA 8.43 R4-03 W-09 (C8-013, adjudication D.2): SST checksum policy for
     * incremental checkpoints.
     *
     * <ul>
     * <li>{@code new-only} (default) — only new or size-changed SST files are
     * SHA-256 hashed each checkpoint; unchanged files reuse the previous hash
     * (SST immutability contract: an SST file never mutates under a fixed
     * name, so (name, size) equality is an honest reuse basis). Turns the
     * incremental path's checksum cost from O(total SST volume) into
     * O(increment).</li>
     * <li>{@code full} — the pre-existing behavior: every SST file is re-hashed
     * every checkpoint (per-checkpoint full re-verification integrity tier,
     * opt-out).</li>
     * </ul>
     *
     * <p>Integrity trade-off of {@code new-only}: the checkpoint checksum
     * verifies new files per checkpoint and relies on the native read layer to
     * surface corruption of previously-verified files. Any other value is
     * rejected typed at snapshot time.
     */
    @Description("SST checksum policy for incremental checkpoints: new-only "
            + "(default; hash only new/size-changed SST files, reuse previous "
            + "hashes under the SST immutability contract) or full (re-hash "
            + "every SST file each checkpoint).")
    IConfigReference<String> SNAPSHOT_HASH_POLICY =
            varRef(s_loc, "nop.stream.rocksdb.snapshot-hash-policy", String.class, "new-only");
}
