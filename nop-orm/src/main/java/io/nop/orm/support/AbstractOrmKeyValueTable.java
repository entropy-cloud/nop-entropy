/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.support;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.commons.type.StdDataType;
import io.nop.orm.IOrmKeyValueTable;
import io.nop.orm.exceptions.OrmException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static io.nop.orm.OrmErrors.ARG_DATA_TYPE;
import static io.nop.orm.OrmErrors.ERR_ORM_UNSUPPORTED_DATA_TYPE;

public abstract class AbstractOrmKeyValueTable extends DynamicOrmEntity implements IOrmKeyValueTable {

    @Override
    public Object getValue() {
        StdDataType type = getFieldStdType();
        if (type == null)
            return getStringValue();

        switch (type) {
            case BOOLEAN:
                return getBooleanValue();
            case CHAR:
                return getCharValue();
            case BYTE:
                return getByteValue();
            case SHORT:
                return getShortValue();
            case INT:
                return getIntValue();
            case LONG:
                return getLongValue();
            case FLOAT:
                return getFloatValue();
            case DOUBLE:
                return getDoubleValue();
            case DECIMAL: {
                BigDecimal decimal = getDecimalValue();
                Byte scale = getDecimalScale();
                if (scale != null && decimal != null)
                    return decimal.setScale(scale.intValue());
                return decimal;
            }
            case BIGINT:
                return getBigIntValue();
            case STRING:
                return getStringValue();
            case DATE:
                return getDateValue();
            case DATETIME:
                return getDateTimeValue();
            case TIMESTAMP:
                return getTimestampValue();
            default:
                return getUnknownTypedValue(type);
        }
    }

    @Override
    public void setValue(Object fieldValue) {
        if (fieldValue == null) {
            setNullValue();
        } else {
            StdDataType type = StdDataType.fromJavaClass(fieldValue.getClass());
            if (type == StdDataType.ANY) {
                type = StdDataType.STRING;
            }
            Byte scale = 0;
            switch (type) {
                case BOOLEAN: {
                    setBooleanValue((Boolean) fieldValue);
                    break;
                }
                case CHAR: {
                    setCharValue((Character) fieldValue);
                    break;
                }
                case BYTE: {
                    setByteValue((Byte) fieldValue);
                    break;
                }
                case SHORT: {
                    setShortValue((Short) fieldValue);
                    break;
                }
                case INT: {
                    setIntValue((Integer) fieldValue);
                    break;
                }
                case LONG: {
                    setLongValue((Long) fieldValue);
                    break;
                }
                case FLOAT: {
                    setFloatValue((Float) fieldValue);
                    scale = null;
                    break;
                }
                case DOUBLE: {
                    setDoubleValue((Double) fieldValue);
                    scale = null;
                    break;
                }
                case DECIMAL: {
                    BigDecimal decimal = (BigDecimal) fieldValue;
                    setDecimalValue(decimal);
                    scale = (byte) decimal.scale();
                    break;
                }
                case BIGINT: {
                    setBigIntValue((BigInteger) fieldValue);
                    break;
                }
                case STRING: {
                    setStringValue(fieldValue.toString());
                    break;
                }
                case DATE: {
                    setDateValue((LocalDate) fieldValue);
                    break;
                }
                case DATETIME: {
                    setDateTimeValue((LocalDateTime) fieldValue);
                    break;
                }
                case TIMESTAMP: {
                    setTimestampValue((Timestamp) fieldValue);
                    break;
                }
                default: {
                    setUnknownTypedValue(type, fieldValue);
                    break;
                }
            }

            setDecimalScale(scale);
            setFieldType(type.ordinal());
        }
    }

    protected void setNullValue() {
        setDateValue(null);
        setStringValue(null);
        setDecimalValue(null);
        setTimestampValue(null);
        setDecimalScale((byte) 0);
    }

    public Boolean getBoolean() {
        return getBooleanValue();
    }

    public void setBoolean(Boolean value) {
        setBooleanValue(value);
        setFieldType(StdDataType.BOOLEAN.ordinal());
    }

    public Byte getByte() {
        return getByteValue();
    }

    public void setByte(Byte value) {
        setByteValue(value);
        setFieldType(StdDataType.BYTE.ordinal());
    }

    public Character getChar() {
        return getCharValue();
    }

    public void setChar(Character value) {
        setCharValue(value);
        setFieldType(StdDataType.CHAR.ordinal());
    }

    public Short getShort() {
        return getShortValue();
    }

    public void setShort(Short value) {
        setShortValue(value);
        setFieldType(StdDataType.SHORT.ordinal());
    }

    public Integer getInt() {
        return getIntValue();
    }

    public void setInt(Integer value) {
        setIntValue(value);
        setFieldType(StdDataType.INT.ordinal());
    }

    public Long getLong() {
        return getLongValue();
    }

    public void setLong(Long value) {
        setLongValue(value);
        setFieldType(StdDataType.LONG.ordinal());
    }

    public BigDecimal getDecimal() {
        BigDecimal decimal = getDecimalValue();
        Byte scale = getDecimalScale();
        if (scale != null && decimal != null)
            return decimal.setScale(scale.intValue());
        return decimal;
    }

    public void setDecimal(BigDecimal decimal) {
        setDecimalValue(decimal);
        setFieldType(StdDataType.DECIMAL.ordinal());
        setDecimalScale(decimal == null ? null : (byte) decimal.scale());
    }

    public LocalDate getDate() {
        return getDateValue();
    }

    public void setDate(LocalDate date) {
        setDateValue(date);
        setFieldType(StdDataType.DATE.ordinal());
    }

    public LocalDateTime getDatetime() {
        return getDateTimeValue();
    }

    public void setDatetime(LocalDateTime value) {
        setDateTimeValue(value);
        setFieldType(StdDataType.DATETIME.ordinal());
    }

    public Timestamp getTimestamp() {
        return getTimestampValue();
    }

    public void setTimestamp(Timestamp value) {
        setTimestampValue(value);
        setFieldType(StdDataType.TIMESTAMP.ordinal());
    }

    public Float getFloat() {
        return getFloatValue();
    }

    public void setFloat(Float value) {
        setFloatValue(value);
        setDecimalScale(null);
        setFieldType(StdDataType.FLOAT.ordinal());
    }

    public Double getDouble() {
        return getDoubleValue();
    }

    public void setDouble(Double value) {
        setDoubleValue(value);
        setDecimalScale(null);
        setFieldType(StdDataType.DOUBLE.ordinal());
    }

    public String getString() {
        return getStringValue();
    }

    public void setString(String value) {
        setStringValue(value);
        setFieldType(StdDataType.STRING.ordinal());
    }

    protected Boolean getBooleanValue() {
        Number value = getNumberValue();
        return value != null && value.intValue() == 1;
    }

    protected void setBooleanValue(Boolean value) {
        setDecimalValue(value == null ? null : ConvertHelper.booleanToBigDecimal(value));
    }

    protected Character getCharValue() {
        String str = getStringValue();
        return str == null || str.isEmpty() ? null : str.charAt(0);
    }

    protected void setCharValue(Character value) {
        setStringValue(value == null ? null : value.toString());
    }

    protected Byte getByteValue() {
        Number value = getNumberValue();
        return value == null ? null : value.byteValue();
    }

    protected void setByteValue(Byte value) {
        setDecimalValue(value == null ? null : BigDecimal.valueOf(value.longValue()));
    }

    protected Short getShortValue() {
        Number value = getNumberValue();
        return value == null ? null : value.shortValue();
    }

    protected void setShortValue(Short value) {
        setDecimalValue(value == null ? null : BigDecimal.valueOf(value.longValue()));
    }

    protected Integer getIntValue() {
        Number value = getNumberValue();
        return value == null ? null : value.intValue();
    }

    protected void setIntValue(Integer value) {
        setDecimalValue(value == null ? null : BigDecimal.valueOf(value.longValue()));
    }

    protected Long getLongValue() {
        Number value = getNumberValue();
        return value == null ? null : value.longValue();
    }

    protected void setLongValue(Long value) {
        setDecimalValue(value == null ? null : BigDecimal.valueOf(value.longValue()));
    }

    protected BigInteger getBigIntValue() {
        BigDecimal value = getDecimalValue();
        return value == null ? null : value.toBigInteger();
    }

    protected void setBigIntValue(BigInteger value) {
        setDecimalValue(value == null ? null : new BigDecimal(value));
    }

    protected Float getFloatValue() {
        Number value = getNumberValue();
        return value == null ? null : value.floatValue();
    }

    protected void setFloatValue(Float value) {
        setDecimalValue(value == null ? null : BigDecimal.valueOf(value.floatValue()));
    }

    protected Double getDoubleValue() {
        Number value = getNumberValue();
        return value == null ? null : value.doubleValue();
    }

    protected void setDoubleValue(Double value) {
        setDecimalValue(value == null ? null : BigDecimal.valueOf(value.doubleValue()));
    }

    protected Number getNumberValue() {
        return getDecimalValue();
    }

    protected LocalDateTime getDateTimeValue() {
        Timestamp value = getTimestampValue();
        return value == null ? null : value.toLocalDateTime();
    }

    protected void setDateTimeValue(LocalDateTime value) {
        setTimestampValue(value == null ? null : ConvertHelper.localDateTimeToTimestamp(value));
    }

    /**
     * 如果派生类不覆盖此函数，则表示丢弃scale信息
     *
     * @return
     */
    protected Byte getDecimalScale() {
        return null;
    }

    protected void setDecimalScale(Byte scale) {
    }

    protected String getStringValue() {
        return null;
    }

    protected void setStringValue(String value) {
    }

    protected BigDecimal getDecimalValue() {
        return null;
    }

    protected void setDecimalValue(BigDecimal value) {

    }

    protected LocalDate getDateValue() {
        return null;
    }

    protected void setDateValue(LocalDate value) {

    }

    protected Timestamp getTimestampValue() {
        return null;
    }

    protected void setTimestampValue(Timestamp value) {
    }

    protected Object getUnknownTypedValue(StdDataType type) {
        throw new OrmException(ERR_ORM_UNSUPPORTED_DATA_TYPE).param(ARG_DATA_TYPE, type);
    }

    protected void setUnknownTypedValue(StdDataType type, Object value) {
        throw new OrmException(ERR_ORM_UNSUPPORTED_DATA_TYPE).param(ARG_DATA_TYPE, type);
    }

    @Override
    public void orm_unsetRef(String propName) {

    }

    @Override
    public boolean orm_refLoaded(String propName) {
        return false;
    }
}