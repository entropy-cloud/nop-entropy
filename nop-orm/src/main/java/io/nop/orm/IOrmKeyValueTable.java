/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm;

import io.nop.commons.type.StdDataType;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 支持纵表存储
 */
public interface IOrmKeyValueTable extends IOrmEntity {
    String getFieldName();

    void setFieldName(String fieldName);

    /**
     * 字段类型必须是StdDataType枚举项的ordinal值
     */
    Integer getFieldType();

    void setFieldType(Integer fieldType);

    default StdDataType getFieldStdType() {
        Integer type = getFieldType();
        if (type == null)
            return null;
        return StdDataType.values()[type];
    }

    Object getValue();

    void setValue(Object fieldValue);

    Character getChar();

    void setChar(Character value);

    Boolean getBoolean();

    void setBoolean(Boolean value);

    Byte getByte();

    void setByte(Byte value);

    Short getShort();

    void setShort(Short value);

    Integer getInt();

    void setInt(Integer value);

    Long getLong();

    void setLong(Long value);

    String getString();

    void setString(String value);

    Float getFloat();

    void setFloat(Float value);

    Double getDouble();

    void setDouble(Double value);

    BigDecimal getDecimal();

    void setDecimal(BigDecimal value);

    LocalDate getDate();

    void setDate(LocalDate value);

    LocalDateTime getDatetime();

    void setDatetime(LocalDateTime value);

    Timestamp getTimestamp();

    void setTimestamp(Timestamp value);

    // BigDecimal getDecimalValue();
    //
    // void setDecimalValue(BigDecimal value);
    //
    // Byte getDecimalScale();
    //
    // void setDecimalScale(Byte scale);
    //
    // String getStringValue();
    //
    // void setStringValue(String value);
    //
    // LocalDate getDateValue();
    //
    // void setDateValue(LocalDate dateValue);
    //
    // Timestamp getTimestampValue();
    //
    // void setTimestampValue(Timestamp value);
}