/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.record.encoder;

import io.nop.record.input.IRecordBinaryInput;
import io.nop.record.input.IRecordInputContext;
import io.nop.record.model.RecordFieldMeta;
import io.nop.record.output.IRecordOutputContext;

public interface IFieldBinaryEncoder {
    Object decode(IRecordBinaryInput input, RecordFieldMeta fieldMeta, IRecordInputContext context);

    void encode(IRecordBinaryInput output, Object value, RecordFieldMeta fieldMeta, IRecordOutputContext context);
}