package io.nop.orm.support;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.orm.OrmConstants;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;

import static io.nop.orm.OrmErrors.ARG_PROP_NAME;

public class DynamicOrmKeyValueTable extends AbstractOrmKeyValueTable {
    @Override
    public String getFieldName() {
        int propId = orm_propId(OrmConstants.PROP_NAME_fieldName);
        return (String) orm_propValue(propId);
    }

    @Override
    public void setFieldName(String fieldName) {
        int propId = orm_propId(OrmConstants.PROP_NAME_fieldName);
        orm_propValue(propId, fieldName);
    }

    @Override
    public Integer getFieldType() {
        int propId = orm_propId(OrmConstants.PROP_NAME_fieldType);
        return ConvertHelper.toInteger(orm_propValue(propId),
                err -> this.newError(err).param(ARG_PROP_NAME, OrmConstants.PROP_NAME_fieldType));
    }

    @Override
    public void setFieldType(Integer fieldType) {
        int propId = orm_propId(OrmConstants.PROP_NAME_fieldType);
        orm_propValue(propId, fieldType);
    }


    /**
     * 如果派生类不覆盖此函数，则表示丢弃scale信息
     *
     * @return
     */
    @Override
    protected Byte getDecimalScale() {
        int propId = orm_propId(OrmConstants.PROP_NAME_decimalScale);
        return ConvertHelper.toByte(orm_propValue(propId),
                err -> this.newError(err).param(ARG_PROP_NAME, OrmConstants.PROP_NAME_decimalScale));
    }

    @Override
    protected void setDecimalScale(Byte scale) {
        int propId = orm_propId(OrmConstants.PROP_NAME_decimalScale);
        orm_propValue(propId, scale);
    }

    @Override
    protected String getStringValue() {
        int propId = orm_propId(OrmConstants.PROP_NAME_stringValue);
        return ConvertHelper.toString(orm_propValue(propId),
                err -> this.newError(err).param(ARG_PROP_NAME, OrmConstants.PROP_NAME_stringValue));
    }

    @Override
    protected void setStringValue(String value) {
        int propId = orm_propId(OrmConstants.PROP_NAME_stringValue);
        orm_propValue(propId, value);
    }

    @Override
    protected BigDecimal getDecimalValue() {
        int propId = orm_propId(OrmConstants.PROP_NAME_decimalValue);
        return ConvertHelper.toBigDecimal(orm_propValue(propId),
                err -> this.newError(err).param(ARG_PROP_NAME, OrmConstants.PROP_NAME_decimalValue));
    }

    @Override
    protected void setDecimalValue(BigDecimal value) {
        int propId = orm_propId(OrmConstants.PROP_NAME_decimalValue);
        orm_propValue(propId, value);
    }

    @Override
    protected LocalDate getDateValue() {
        int propId = orm_propId(OrmConstants.PROP_NAME_dateValue);
        return ConvertHelper.toLocalDate(orm_propValue(propId),
                err -> this.newError(err).param(ARG_PROP_NAME, OrmConstants.PROP_NAME_dateValue));
    }

    @Override
    protected void setDateValue(LocalDate value) {
        int propId = orm_propId(OrmConstants.PROP_NAME_dateValue);
        orm_propValue(propId, value);
    }

    @Override
    protected Timestamp getTimestampValue() {
        int propId = orm_propId(OrmConstants.PROP_NAME_timestampValue);
        return ConvertHelper.toTimestamp(orm_propValue(propId),
                err -> this.newError(err).param(ARG_PROP_NAME, OrmConstants.PROP_NAME_timestampValue));
    }

    @Override
    protected void setTimestampValue(Timestamp value) {
        int propId = orm_propId(OrmConstants.PROP_NAME_timestampValue);
        orm_propValue(propId, value);
    }

}
