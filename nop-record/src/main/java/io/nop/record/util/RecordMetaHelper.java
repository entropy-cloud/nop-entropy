/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.record.util;

import io.nop.api.core.exceptions.NopException;
import io.nop.record.encoder.FieldEncoderRegistry;
import io.nop.record.encoder.IFieldTextEncoder;
import io.nop.record.model.RecordFieldMeta;

import static io.nop.record.RecordErrors.ARG_ENCODER;
import static io.nop.record.RecordErrors.ARG_FIELD_NAME;
import static io.nop.record.RecordErrors.ARG_LENGTH;
import static io.nop.record.RecordErrors.ERR_RECORD_FIELD_LENGTH_GREATER_THAN_MAX_VALUE;
import static io.nop.record.RecordErrors.ERR_RECORD_UNKNOWN_FIELD_ENCODER;
import static io.nop.xlang.XLangErrors.ARG_MAX_LENGTH;

public class RecordMetaHelper {
    public static void checkMaxLen(int len, RecordFieldMeta field) {
        int max = field.safeGetMaxLen();
        if (max > 0 && len > max) {
            throw new NopException(ERR_RECORD_FIELD_LENGTH_GREATER_THAN_MAX_VALUE)
                    .param(ARG_FIELD_NAME, field.getName())
                    .param(ARG_LENGTH, len)
                    .param(ARG_MAX_LENGTH, len);
        }
    }

    public static void checkMinLen(int len, RecordFieldMeta field) {
        int min = field.safeGetMaxLen();
        if (min > 0 && len > min) {
            throw new NopException(ERR_RECORD_FIELD_LENGTH_GREATER_THAN_MAX_VALUE)
                    .param(ARG_FIELD_NAME, field.getName())
                    .param(ARG_LENGTH, len)
                    .param(ARG_MAX_LENGTH, len);
        }
    }

    public static IFieldTextEncoder getEncoder(RecordFieldMeta field, FieldEncoderRegistry registry) {
        IFieldTextEncoder resolved = field.getResolvedTextEncoder();
        if (resolved != null)
            return resolved;

        String encoder = field.getEncoder();
        if (encoder == null)
            return null;

        resolved = registry.getTextEncoder(encoder);
        if (resolved == null)
            throw new NopException(ERR_RECORD_UNKNOWN_FIELD_ENCODER)
                    .source(field).param(ARG_FIELD_NAME, field.getName())
                    .param(ARG_ENCODER, encoder);

        field.setResolvedTextEncoder(resolved);
        return resolved;
    }
}
