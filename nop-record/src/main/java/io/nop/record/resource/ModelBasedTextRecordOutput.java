/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.record.resource;

import io.nop.commons.aggregator.CompositeAggregatorProvider;
import io.nop.commons.aggregator.IAggregatorProvider;
import io.nop.record.codec.FieldCodecRegistry;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.codec.impl.DefaultFieldCodecContext;
import io.nop.record.model.RecordFileMeta;
import io.nop.record.serialization.ModelBasedBinaryRecordSerializer;
import io.nop.record.writer.IRecordTextWriter;

public class ModelBasedTextRecordOutput<T> extends AbstractModelBasedRecordOutput<IRecordTextWriter, T> {

    public ModelBasedTextRecordOutput(IRecordTextWriter out, RecordFileMeta fileMeta,
                                      IFieldCodecContext context, FieldCodecRegistry registry,
                                      IAggregatorProvider aggregatorProvider) {
        super(out, fileMeta, context, new ModelBasedBinaryRecordSerializer(registry), aggregatorProvider);
    }

    public ModelBasedTextRecordOutput(IRecordTextWriter out, RecordFileMeta fileMeta) {
        this(out, fileMeta, new DefaultFieldCodecContext(fileMeta::getType), FieldCodecRegistry.DEFAULT, CompositeAggregatorProvider.defaultProvider());
    }
}
