/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core.loader;

import io.nop.batch.core.BatchConfigs;
import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchLoaderProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 1.7-C5-004（plan 2545 W-04）：provider 返回 List 时该 List 被本 loader 引用直至任务结束
 * （常驻时长 = 任务全程，内存 = 整表）。这是能力声明而非缺陷，但此前 owner doc 未声明该
 * 内存代价也无守卫——现于构造期对超大 List 打 WARN（可观测），并在 owner doc 声明包络。
 * 上界缺省 100 万条（≈ 数百 MB 量级），可经 {@code nop.batch.list-loader.warn-size} 调整；
 * 只告警不拒绝（拒绝会破坏既有部署的合法大表导出）。
 */
public class ListBatchLoader<S, C> implements IBatchLoaderProvider.IBatchLoader<S> {
    static final Logger LOG = LoggerFactory.getLogger(ListBatchLoader.class);

    /** 常驻 List 规模的 WARN 阈值（缺省 100 万条）。 */
    static final int DEFAULT_WARN_SIZE = 1_000_000;

    private final List<S> list;

    private int offset = 0;

    public ListBatchLoader(List<S> list) {
        this.list = list == null ? Collections.emptyList() : list;
        int warnSize = BatchConfigs.CFG_BATCH_LIST_LOADER_WARN_SIZE.get();
        if (warnSize > 0 && this.list.size() >= warnSize) {
            LOG.warn("nop.batch.list-loader-large-resident-list:size={},warnSize={}",
                    this.list.size(), warnSize);
        }
    }


    @Override
    public synchronized List<S> load(int batchSize, IBatchChunkContext context) {
        if (offset >= list.size())
            return Collections.emptyList();

        int n = Math.min(list.size() - offset, batchSize);
        List<S> ret = new ArrayList<>(list.subList(offset, offset + n));
        offset += n;
        return ret;
    }
}
