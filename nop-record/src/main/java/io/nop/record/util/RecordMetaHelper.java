/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.record.util;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.bytes.ByteString;
import io.nop.commons.util.StringHelper;
import io.nop.record.codec.FieldCodecRegistry;
import io.nop.record.codec.IFieldBinaryCodec;
import io.nop.record.codec.IFieldTagBinaryCodec;
import io.nop.record.codec.IFieldTagTextCodec;
import io.nop.record.codec.IFieldTextCodec;
import io.nop.record.model.RecordFieldMeta;

import static io.nop.record.RecordErrors.ARG_CODEC;
import static io.nop.record.RecordErrors.ARG_FIELD_NAME;
import static io.nop.record.RecordErrors.ARG_LENGTH;
import static io.nop.record.RecordErrors.ERR_RECORD_FIELD_LENGTH_GREATER_THAN_MAX_VALUE;
import static io.nop.record.RecordErrors.ERR_RECORD_UNKNOWN_FIELD_CODEC;
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

    public static IFieldTextCodec resolveTextCodec(RecordFieldMeta field, FieldCodecRegistry registry) {
        IFieldTextCodec resolved = field.getResolvedTextCodec();
        if (resolved != null)
            return resolved;

        String codec = field.getCodec();
        if (codec == null)
            return null;

        resolved = registry.getTextCodec(codec);
        if (resolved == null)
            throw new NopException(ERR_RECORD_UNKNOWN_FIELD_CODEC)
                    .source(field)
                    .param(ARG_FIELD_NAME, field.getName())
                    .param(ARG_CODEC, codec);

        field.setResolvedTextCodec(resolved);
        return resolved;
    }

    public static IFieldBinaryCodec resolveBinaryCodec(RecordFieldMeta field, FieldCodecRegistry registry) {
        IFieldBinaryCodec resolved = field.getResolvedBinaryCodec();
        if (resolved != null)
            return resolved;

        String codec = field.getCodec();
        if (codec == null)
            return null;

        resolved = registry.getBinaryCodec(codec);
        if (resolved == null)
            throw new NopException(ERR_RECORD_UNKNOWN_FIELD_CODEC)
                    .source(field)
                    .param(ARG_FIELD_NAME, field.getName())
                    .param(ARG_CODEC, codec);

        field.setResolvedBinaryCodec(resolved);
        return resolved;
    }

    public static IFieldTagBinaryCodec resolveTagBinaryCodec(RecordFieldMeta field, FieldCodecRegistry registry) {
        IFieldTagBinaryCodec resolved = field.getResolvedTagBinaryCodec();
        if (resolved != null)
            return resolved;

        String codec = field.getTagsCodec();
        if (codec == null)
            return null;

        resolved = registry.getTagBinaryCodec(codec);
        if (resolved == null)
            throw new NopException(ERR_RECORD_UNKNOWN_FIELD_CODEC)
                    .source(field)
                    .param(ARG_FIELD_NAME, field.getName())
                    .param(ARG_CODEC, codec);

        field.setResolvedTagBinaryCodec(resolved);
        return resolved;
    }

    public static IFieldTagTextCodec resolveTagTextCodec(RecordFieldMeta field, FieldCodecRegistry registry) {
        IFieldTagTextCodec resolved = field.getResolvedTagTextCodec();
        if (resolved != null)
            return resolved;

        String codec = field.getTagsCodec();
        if (codec == null)
            return null;

        resolved = registry.getTagTextCodec(codec);
        if (resolved == null)
            throw new NopException(ERR_RECORD_UNKNOWN_FIELD_CODEC)
                    .source(field)
                    .param(ARG_FIELD_NAME, field.getName())
                    .param(ARG_CODEC, codec);

        field.setResolvedTagTextCodec(resolved);
        return resolved;
    }

    public static String padText(String str, RecordFieldMeta field) {
        int len = str.length();
        RecordMetaHelper.checkMaxLen(len, field);
        RecordMetaHelper.checkMinLen(len, field);

        int expected = field.getLength();
        if (expected > 0) {
            if (len == expected)
                return str;

            ByteString padding = field.getPadding();
            if (padding != null) {
                String paddingStr = padding.toString(field.getCharset());
                str = StringHelper.rightPad(str, expected, paddingStr.charAt(0));
            }
        }
        return str;
    }

    public static ByteString padBinary(ByteString str, RecordFieldMeta field) {
        int len = str.length();
        RecordMetaHelper.checkMaxLen(len, field);
        RecordMetaHelper.checkMinLen(len, field);

        int expected = field.getLength();
        if (expected > 0) {
            if (len == expected)
                return str;

            ByteString padding = field.getPadding();
            if (padding != null) {
                str = str.rightPad(expected, padding.at(0));
            }
        }
        return str;
    }
}
