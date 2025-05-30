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
import io.nop.record.model.IRecordFieldsMeta;
import io.nop.record.model.RecordFieldMeta;
import io.nop.record.model.RecordSimpleFieldMeta;

import static io.nop.record.RecordErrors.ARG_CODEC;
import static io.nop.record.RecordErrors.ARG_FIELD_NAME;
import static io.nop.record.RecordErrors.ARG_LENGTH;
import static io.nop.record.RecordErrors.ARG_MIN_LENGTH;
import static io.nop.record.RecordErrors.ERR_RECORD_FIELD_LENGTH_GREATER_THAN_MAX_VALUE;
import static io.nop.record.RecordErrors.ERR_RECORD_FIELD_LENGTH_LESS_THAN_MIN_VALUE;
import static io.nop.record.RecordErrors.ERR_RECORD_UNKNOWN_FIELD_CODEC;
import static io.nop.xlang.XLangErrors.ARG_MAX_LENGTH;

public class RecordMetaHelper {
    public static void checkMaxLen(int len, RecordSimpleFieldMeta field) {
        int max = field.safeGetMaxLen();
        if (max > 0 && len > max) {
            throw new NopException(ERR_RECORD_FIELD_LENGTH_GREATER_THAN_MAX_VALUE)
                    .param(ARG_FIELD_NAME, field.getName())
                    .param(ARG_LENGTH, len)
                    .param(ARG_MAX_LENGTH, max);
        }
    }

    public static void checkMinLen(int len, RecordSimpleFieldMeta field) {
        int min = field.safeGetMaxLen();
        if (min > 0 && len > min) {
            throw new NopException(ERR_RECORD_FIELD_LENGTH_LESS_THAN_MIN_VALUE)
                    .param(ARG_FIELD_NAME, field.getName())
                    .param(ARG_LENGTH, len)
                    .param(ARG_MIN_LENGTH, min);
        }
    }

    public static IFieldTextCodec resolveTextCodec(RecordSimpleFieldMeta field, FieldCodecRegistry registry) {
        IFieldTextCodec resolved = field.getResolvedTextCodec();
        if (resolved != null)
            return resolved;

        String codec = field.getCodec();
        if (codec == null)
            return null;

        resolved = registry.getTextCodec(codec, field);
        if (resolved == null)
            throw new NopException(ERR_RECORD_UNKNOWN_FIELD_CODEC)
                    .source(field)
                    .param(ARG_FIELD_NAME, field.getName())
                    .param(ARG_CODEC, codec);

        field.setResolvedTextCodec(resolved);
        return resolved;
    }

    public static IFieldBinaryCodec resolveBinaryCodec(RecordSimpleFieldMeta field, FieldCodecRegistry registry) {
        IFieldBinaryCodec resolved = field.getResolvedBinaryCodec();
        if (resolved != null)
            return resolved;

        String codec = field.getCodec();
        if (codec == null)
            return null;

        resolved = registry.getBinaryCodec(codec, field);
        if (resolved == null)
            throw new NopException(ERR_RECORD_UNKNOWN_FIELD_CODEC)
                    .source(field)
                    .param(ARG_FIELD_NAME, field.getName())
                    .param(ARG_CODEC, codec);

        field.setResolvedBinaryCodec(resolved);
        return resolved;
    }

    public static IFieldTagBinaryCodec resolveTagBinaryCodec(IRecordFieldsMeta field, FieldCodecRegistry registry) {
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

    public static IFieldTagTextCodec resolveTagTextCodec(IRecordFieldsMeta field, FieldCodecRegistry registry) {
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

    public static String padText(String str, RecordSimpleFieldMeta field) {
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
                if (field.isLeftPad()) {
                    str = StringHelper.leftPad(str, expected, paddingStr.charAt(0));
                } else {
                    str = StringHelper.rightPad(str, expected, paddingStr.charAt(0));
                }
            }
        }
        return str;
    }

    public static ByteString padBinary(ByteString str, RecordSimpleFieldMeta field) {
        int len = str.length();
        RecordMetaHelper.checkMaxLen(len, field);
        RecordMetaHelper.checkMinLen(len, field);

        int expected = field.getLength();
        if (expected > 0) {
            if (len == expected)
                return str;

            ByteString padding = field.getPadding();
            if (padding != null) {
                if (field.isLeftPad()) {
                    str = str.leftPad(expected, padding.at(0));
                } else {
                    str = str.rightPad(expected, padding.at(0));
                }
            }
        }
        return str;
    }


}
