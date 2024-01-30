/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dataset.binder;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.bytes.ByteString;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static io.nop.api.core.ApiErrors.ARG_INDEX;
import static io.nop.api.core.convert.ConvertHelper.toBigDecimal;
import static io.nop.api.core.convert.ConvertHelper.toBoolean;
import static io.nop.api.core.convert.ConvertHelper.toByte;
import static io.nop.api.core.convert.ConvertHelper.toBytes;
import static io.nop.api.core.convert.ConvertHelper.toDouble;
import static io.nop.api.core.convert.ConvertHelper.toFloat;
import static io.nop.api.core.convert.ConvertHelper.toInt;
import static io.nop.api.core.convert.ConvertHelper.toLocalDate;
import static io.nop.api.core.convert.ConvertHelper.toLocalDateTime;
import static io.nop.api.core.convert.ConvertHelper.toLong;
import static io.nop.api.core.convert.ConvertHelper.toShort;
import static io.nop.api.core.convert.ConvertHelper.toTimestamp;

public interface IDataParameters {
    default Object getNativeConnection() {
        return null;
    }

    Object getObject(int index);

    void setObject(int index, Object value);

    default void setObject(int index, Object value, int targetType) {
        setObject(index, value);
    }

    default NopException handleConvertError(ErrorCode errorCode, int index) {
        return new NopException(errorCode).param(ARG_INDEX, index);
    }

    default boolean isNull(int index) {
        return getObject(index) == null;
    }

    default void setNull(int index) {
        setObject(index, null);
    }

    default Boolean getBoolean(int index) {
        return toBoolean(getObject(index), err -> handleConvertError(err, index));
    }

    default void setBoolean(int index, Boolean value) {
        setObject(index, value);
    }

    default String getString(int index) {
        return ConvertHelper.toString(getObject(index), err -> handleConvertError(err, index));
    }

    default String getJsonString(int index) {
        return getString(index);
    }

    default void setString(int index, String value) {
        setObject(index, value);
    }

    default void setJsonString(int index, String value) {
        setString(index, value);
    }

    default Integer getInt(int index) {
        return toInt(getObject(index), err -> handleConvertError(err, index));
    }

    default void setInt(int index, Integer value) {
        setObject(index, value);
    }

    default Short getShort(int index) {
        return toShort(getObject(index), err -> handleConvertError(err, index));
    }

    default void setShort(int index, Short value) {
        setObject(index, value);
    }

    default Byte getByte(int index) {
        return toByte(getObject(index), err -> handleConvertError(err, index));
    }

    default void setByte(int index, Byte value) {
        setObject(index, value);
    }

    default Double getDouble(int index) {
        return toDouble(getObject(index), err -> handleConvertError(err, index));
    }

    default void setDouble(int index, Double value) {
        setObject(index, value);
    }

    default Float getFloat(int index) {
        return toFloat(getObject(index), err -> handleConvertError(err, index));
    }

    default void setFloat(int index, Float value) {
        setObject(index, value);
    }

    default byte[] getBytes(int index) {
        return toBytes(getObject(index), err -> handleConvertError(err, index));
    }

    default void setBytes(int index, byte[] bytes) {
        setObject(index, bytes);
    }

    default ByteString getByteString(int index) {
        byte[] bytes = getBytes(index);
        if (bytes == null)
            return null;
        return ByteString.of(bytes);
    }

    default void setByteString(int index, ByteString value) {
        byte[] bytes = value == null ? null : value.toByteArray();
        setBytes(index, bytes);
    }

    default Long getLong(int index) {
        return toLong(getObject(index), err -> handleConvertError(err, index));
    }

    default void setLong(int index, Long value) {
        setObject(index, value);
    }

    default BigDecimal getBigDecimal(int index) {
        return toBigDecimal(getObject(index), err -> handleConvertError(err, index));
    }

    default void setBigDecimal(int index, BigDecimal value) {
        setObject(index, value);
    }

    default LocalTime getLocalTime(int index) {
        return ConvertHelper.toLocalTime(getObject(index), err -> handleConvertError(err, index));
    }

    default void setLocalTime(int index, LocalTime value) {
        setObject(index, value);
    }

    default LocalDate getLocalDate(int index) {
        return toLocalDate(getObject(index), err -> handleConvertError(err, index));
    }

    default void setLocalDate(int index, LocalDate value) {
        setObject(index, value);
    }

    default LocalDateTime getLocalDateTime(int index) {
        return toLocalDateTime(getObject(index), err -> handleConvertError(err, index));
    }

    default void setLocalDateTime(int index, LocalDateTime value) {
        setObject(index, value);
    }

    default Timestamp getTimestamp(int index) {
        return toTimestamp(getObject(index), err -> handleConvertError(err, index));
    }

    default Instant getInstant(int index) {
        Timestamp timestamp = getTimestamp(index);
        if (timestamp == null)
            return null;
        return timestamp.toInstant();
    }


    default void setTimestamp(int index, Timestamp value) {
        setObject(index, value);
    }
}