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
import io.nop.record.reader.ITextDataReader;
import io.nop.record.serialization.ModelBasedTextRecordDeserializer;

public class ModelBasedTextRecordInput<T> extends AbstractModelBasedRecordInput<ITextDataReader, T> {

    public ModelBasedTextRecordInput(ITextDataReader in, RecordFileMeta fileMeta, boolean useStreaming,
                                     IFieldCodecContext context, FieldCodecRegistry registry) {
        super(in, fileMeta, useStreaming, context, new ModelBasedTextRecordDeserializer(registry));
    }

    public ModelBasedTextRecordInput(ITextDataReader in, RecordFileMeta fileMeta, boolean useStreaming) {
        this(in, fileMeta, useStreaming, new DefaultFieldCodecContext(fileMeta::getType), FieldCodecRegistry.DEFAULT);
    }

    public ModelBasedTextRecordInput(ITextDataReader in, RecordFileMeta fileMeta) {
        this(in, fileMeta, false);
    }
}
