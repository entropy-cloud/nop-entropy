/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.record.resource;

import io.nop.record.codec.FieldCodecRegistry;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.codec.impl.DefaultFieldCodecContext;
import io.nop.record.model.RecordFileMeta;
import io.nop.record.reader.IBinaryDataReader;
import io.nop.record.serialization.ModelBasedBinaryRecordDeserializer;

public class ModelBasedBinaryRecordInput<T> extends AbstractModelBasedRecordInput<IBinaryDataReader, T> {

    public ModelBasedBinaryRecordInput(IBinaryDataReader in, RecordFileMeta fileMeta,
                                       IFieldCodecContext context, FieldCodecRegistry registry) {
        super(in, fileMeta, context, new ModelBasedBinaryRecordDeserializer(registry));
    }

    public ModelBasedBinaryRecordInput(IBinaryDataReader in, RecordFileMeta fileMeta) {
        this(in, fileMeta, new DefaultFieldCodecContext(fileMeta::getType), FieldCodecRegistry.DEFAULT);
    }
}
