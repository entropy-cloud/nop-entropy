/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.convert;

import io.nop.api.core.ApiErrors;
import io.nop.api.core.annotations.core.GlobalInstance;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@GlobalInstance
public class SysConverterRegistry {
    static final Logger LOG = LoggerFactory.getLogger(SysConverterRegistry.class);

    static final SysConverterRegistry _INSTANCE = new SysConverterRegistry();

    public static SysConverterRegistry instance() {
        return _INSTANCE;
    }

    private Map<Class<?>, ITypeConverter> converters = new HashMap<>();
    private Map<String, TargetTypeConverter> namedConverters = new HashMap<>();

    private IConverter unknownTypeConverter;

    {
        registerConverter("toPrimitiveByte", byte.class, ConvertHelper::toPrimitiveByte);
        registerConverter("toPrimitiveBoolean", boolean.class, ConvertHelper::toPrimitiveBoolean);
        registerConverter("toPrimitiveChar", char.class, ConvertHelper::toPrimitiveChar);
        registerConverter("toPrimitiveShort", short.class, ConvertHelper::toPrimitiveShort);
        registerConverter("toPrimitiveInt", int.class, ConvertHelper::toPrimitiveInt);
        registerConverter("toPrimitiveLong", long.class, ConvertHelper::toPrimitiveLong);
        registerConverter("toPrimitiveFloat", float.class, ConvertHelper::toPrimitiveFloat);
        registerConverter("toPrimitiveDouble", double.class, ConvertHelper::toPrimitiveDouble);

        registerConverter("toVoid", Void.class, ConvertHelper::toVoid);

        registerConverter("toBoolean", Boolean.class, ConvertHelper::toBoolean);
        registerNamedConverter("toFalsy", new TargetTypeConverter(Boolean.class, ConvertHelper::toFalsy));
        registerNamedConverter("toTruthy", new TargetTypeConverter(Boolean.class, ConvertHelper::toTruthy));

        registerConverter("toBytes", byte[].class, ConvertHelper::toBytes);
        registerConverter("toByte", Byte.class, ConvertHelper::toByte);
        registerConverter("toChar", Character.class, ConvertHelper::toChar);
        registerConverter("toString", String.class, ConvertHelper::toString);
        registerConverter("toShort", Short.class, ConvertHelper::toShort);
        registerConverter("toInt", Integer.class, ConvertHelper::toInt);
        registerConverter("toLong", Long.class, ConvertHelper::toLong);
        registerConverter("toFloat", Float.class, ConvertHelper::toFloat);
        registerConverter("toDouble", Double.class, ConvertHelper::toDouble);
        registerConverter("toBigDecimal", BigDecimal.class, ConvertHelper::toBigDecimal);
        registerConverter("toLocalDate", LocalDate.class, ConvertHelper::toLocalDate);
        registerConverter("toLocalDateTime", LocalDateTime.class, ConvertHelper::toLocalDateTime);
        registerConverter("toTimestamp", Timestamp.class, ConvertHelper::toTimestamp);
        registerConverter("toDuration", Duration.class, ConvertHelper::toDuration);
        registerConverter("toLocalTime", LocalTime.class, ConvertHelper::toLocalTime);
        registerConverter("toNumber", Number.class, ConvertHelper::toNumber);
        registerNamedConverter("toStripedString", new TargetTypeConverter(String.class, ConvertHelper::toStripedString));
        registerConverter("toList", List.class, ConvertHelper::toList);
        registerConverter("toCollection", Collection.class, ConvertHelper::toCollection);
        registerConverter("toSet", Set.class, ConvertHelper::toSet);
        registerConverter("toMap", Map.class, ConvertHelper::toMap);
        registerNamedConverter("toCsvSet", new TargetTypeConverter(Set.class, ConvertHelper::toCsvSet));
        registerNamedConverter("toCsvList", new TargetTypeConverter(List.class, ConvertHelper::toCsvList));
        registerNamedConverter("toCsvSetString", new TargetTypeConverter(String.class, ConvertHelper::toCsvSetString));
        registerNamedConverter("toCsvListString", new TargetTypeConverter(String.class, ConvertHelper::toCsvListString));
        registerConverter("toBigInteger", BigInteger.class, ConvertHelper::toBigInteger);
        registerConverter("toIterator", Iterator.class, ConvertHelper::toIterator);
        registerConverter("toMonthDay", MonthDay.class, ConvertHelper::toMonthDay);
    }

    public ITypeConverter getConverterByType(Class<?> clazz) {
        return converters.get(clazz);
    }

    public TargetTypeConverter getConverterByName(String name) {
        return namedConverters.get(name);
    }

    public void removeConverterByType(Class<?> targetClass) {
        converters.remove(targetClass);
    }

    public void removeConverterByName(String name) {
        namedConverters.remove(name);
    }

    public void unregisterTypeConverter(String name, Type targetType, ITypeConverter converter) {
        converters.remove(targetType, converter);

        TargetTypeConverter old = namedConverters.get(name);
        if (old != null && old.getConverter() == converter) {
            namedConverters.remove(name, old);
        }
    }

    public void registerUnknownTypeConverter(IConverter converter) {
        unknownTypeConverter = converter;
    }

    public IConverter getUnknownTypeConverter() {
        return unknownTypeConverter;
    }

    public void registerConverter(String name, Class<?> targetType, ITypeConverter converter) {
        LOG.trace("nop.api.convert.register-converter:name={},targetType{}", name, targetType);
        Guard.notEmpty(name, "nop.err.api.convert.empty-converter-name");
        Guard.notNull(targetType, "nop.err.api.convert.null-target-type");
        Guard.notNull(converter, "nop.err.api.convert.null-converter");
        if (converters.containsKey(targetType)) {
            throw new NopException(ApiErrors.ERR_CONVERTER_ALREADY_REGISTERED)
                    .param(ApiErrors.ARG_TARGET_TYPE, targetType.getTypeName()).param(ApiErrors.ARG_NAME, name);
        }
        converters.put(targetType, converter);
        namedConverters.put(name, new TargetTypeConverter(targetType, converter));
    }

    public void registerNamedConverter(String name, TargetTypeConverter converter) {
        LOG.trace("nop.api.convert.register-converter:name={}", name);
        Guard.notEmpty(name, "nop.err.api.convert.empty-converter-name");
        Guard.notNull(name, "nop.err.api.convert.null-converter");
        namedConverters.put(name, converter);
    }
}