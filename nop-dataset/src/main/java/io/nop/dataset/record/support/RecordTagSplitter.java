/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dataset.record.support;

import io.nop.dataset.record.IRecordSplitter;
import io.nop.dataset.record.IRecordTagger;

import java.util.Collection;
import java.util.function.BiConsumer;

public class RecordTagSplitter<T, C> implements IRecordSplitter<T, T, C> {
    private final IRecordTagger<T, C> tagger;

    public RecordTagSplitter(IRecordTagger<T, C> tagger) {
        this.tagger = tagger;
    }

    @Override
    public void split(T record, BiConsumer<String, T> collector, C context) {
        Collection<String> tags = tagger.getTags(record, context);
        if (tags != null) {
            for (String tag : tags) {
                collector.accept(tag, record);
            }
        }
    }
}