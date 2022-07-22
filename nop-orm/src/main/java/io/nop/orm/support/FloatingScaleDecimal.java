package io.nop.orm.support;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;

import java.math.BigDecimal;

/**
 * 数据库中的decimal数据类型的小数位数是固定的，但是某些应用中我们需要保留输入数据的小数位数，例如要求用户输入3位小数，则显示3位小数，
 * 输入2位小数，则显示两位小数。此时，我们可以通过两个字段来保存数据，一个具有足够精度的DECIMAL类型用于保存字段值，同时增加一个scale字段来保存
 * 原始数据的小数位数。
 */
public class FloatingScaleDecimal extends AbstractOrmComponent {
    public static final String PROP_NAME_value = "value";
    public static final String PROP_NAME_scale = "scale";

    /**
     * 获取到的BigDecimal对象的scale已经规范化为scale字段所保存的值
     */
    public BigDecimal getNormalizedValue() {
        BigDecimal value = getValue();
        if (value == null)
            return null;
        byte scale = getScale();
        return value.setScale(scale);
    }

    public void setNormalizedValue(BigDecimal value) {
        if (value == null) {
            setValue(null);
        } else {
            setValue(value);
            setScale((byte) value.scale());
        }
    }

    public BigDecimal getValue() {
        return ConvertHelper.toBigDecimal(internalGetPropValue(PROP_NAME_value));
    }

    public void setValue(BigDecimal value) {
        internalSetPropValue(PROP_NAME_value, value);
    }

    /**
     * 原始数据的小数位数
     */
    public byte getScale() {
        return ConvertHelper.toPrimitiveByte(internalGetPropValue(PROP_NAME_scale), NopException::new);
    }

    public void setScale(byte scale) {
        internalSetPropValue(PROP_NAME_scale, scale);
    }
}