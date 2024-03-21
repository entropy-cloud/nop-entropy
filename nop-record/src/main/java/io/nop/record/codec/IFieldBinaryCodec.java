/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.record.codec;

import io.nop.record.input.IRecordBinaryInput;
import io.nop.record.output.IRecordBinaryOutput;

import java.nio.charset.Charset;

public interface IFieldBinaryCodec {
    Object decode(IRecordBinaryInput input, int length, Charset charset, IFieldCodecContext context);

    void encode(IRecordBinaryOutput output, Object value, int length, Charset charset, IFieldCodecContext context);
}