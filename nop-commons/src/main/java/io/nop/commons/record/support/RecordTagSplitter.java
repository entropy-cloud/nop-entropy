/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.record.support;

import io.nop.commons.record.IRecordSplitter;
import io.nop.commons.record.IRecordTagger;

import java.util.Collection;
import java.util.function.BiConsumer;

public class RecordTagSplitter<T> implements IRecordSplitter<T, T> {
    private final IRecordTagger<T> tagger;

    public RecordTagSplitter(IRecordTagger<T> tagger) {
        this.tagger = tagger;
    }

    @Override
    public void split(T record, BiConsumer<String, T> collector) {
        Collection<String> tags = tagger.getTags(record);
        if (tags != null) {
            for (String tag : tags) {
                collector.accept(tag, record);
            }
        }
    }
}