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
import io.nop.record.writer.IRecordBinaryWriter;

public class ModelBasedBinaryRecordOutput<T> extends AbstractModelBasedRecordOutput<IRecordBinaryWriter, T> {

    public ModelBasedBinaryRecordOutput(IRecordBinaryWriter out, RecordFileMeta fileMeta,
                                        IFieldCodecContext context, FieldCodecRegistry registry,
                                        IAggregatorProvider aggregatorProvider) {
        super(out, fileMeta, context, new ModelBasedBinaryRecordSerializer(registry), aggregatorProvider);
    }

    public ModelBasedBinaryRecordOutput(IRecordBinaryWriter out, RecordFileMeta fileMeta) {
        this(out, fileMeta, new DefaultFieldCodecContext(fileMeta::getType), FieldCodecRegistry.DEFAULT, CompositeAggregatorProvider.defaultProvider());
    }
}
