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
}
