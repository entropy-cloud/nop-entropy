package io.nop.xlang.xmeta.reflect;

import io.nop.api.core.annotations.meta.PropMeta;
import io.nop.core.reflect.bean.IBeanPropertyModel;
import io.nop.xlang.xmeta.ISchema;
import io.nop.xlang.xmeta.impl.SchemaImpl;

public class ReflectObjMetaParser {
    public static final ReflectObjMetaParser INSTANCE = new ReflectObjMetaParser();

    public ISchema parsePropSchema(IBeanPropertyModel propModel) {
        PropMeta propMeta = propModel.getAnnotation(PropMeta.class);
        if (propMeta != null)
            return buildSchemaFromPropMeta(propMeta);

        /*
        @Null	限制只能为null
        @NotNull	限制必须不为null
        @AssertFalse	限制必须为false
        @AssertTrue	限制必须为true
        @DecimalMax(value)	限制必须为一个不大于指定值的数字
        @DecimalMin(value)	限制必须为一个不小于指定值的数字
        @Digits(integer,fraction)	限制必须为一个小数，且整数部分的位数不能超过integer，小数部分的位数不能超过fraction
        @Future	限制必须是一个将来的日期
        @Max(value)	限制必须为一个不大于指定值的数字
        @Min(value)	限制必须为一个不小于指定值的数字
        @Past	限制必须是一个过去的日期
        @Pattern(value)	限制必须符合指定的正则表达式
        @Size(max,min)	限制字符长度必须在min到max之间
        @Past	验证注解的元素值（日期类型）比当前时间早
        @NotEmpty	验证注解的元素值不为null且不为空（字符串长度不为0、集合大小不为0）
        @NotBlank	验证注解的元素值不为空（不为null、去除首位空格后长度为0），不同于@NotEmpty，@NotBlank只应用于字符串且在比较时会去除字符串的空格
        @Email
         */

        return null;
    }

    public ISchema buildSchemaFromPropMeta(PropMeta propMeta) {
        SchemaImpl schema = new SchemaImpl();
        if (!propMeta.pattern().isEmpty())
            schema.setPattern(propMeta.pattern());
        if (!propMeta.stdDomain().isEmpty())
            schema.setStdDomain(propMeta.stdDomain());
        if (!propMeta.domain().isEmpty())
            schema.setDomain(propMeta.domain());
        if (propMeta.max() != Double.MAX_VALUE)
            schema.setMax(propMeta.max());
        if (propMeta.min() != Double.MIN_VALUE)
            schema.setMin(propMeta.min());
        if (propMeta.excludeMax())
            schema.setExcludeMax(true);

        if (propMeta.excludeMin())
            schema.setExcludeMin(true);

        if (!propMeta.dict().isEmpty())
            schema.setDict(propMeta.dict());

        if (propMeta.maxItems() != Integer.MAX_VALUE)
            schema.setMaxItems(propMeta.maxItems());

        if (propMeta.minItems() > 0)
            schema.setMinItems(propMeta.minItems());

        if (propMeta.minLength() > 0)
            schema.setMinLength(propMeta.minLength());

        if (propMeta.maxLength() != Integer.MAX_VALUE)
            schema.setMaxLength(propMeta.maxLength());

        if (!propMeta.displayName().isEmpty())
            schema.setDisplayName(propMeta.displayName());

        if (!propMeta.description().isEmpty())
            schema.setDescription(propMeta.description());

        if (!propMeta.orderProp().isEmpty())
            schema.setOrderProp(propMeta.orderProp());

        if (!propMeta.keyProp().isEmpty())
            schema.setKeyProp(propMeta.keyProp());

        if (propMeta.precision() >= 0)
            schema.setPrecision(propMeta.precision());

        if (propMeta.scale() >= 0)
            schema.setScale(propMeta.scale());

        if (propMeta.multipleOf() > 0)
            schema.setMultipleOf(propMeta.multipleOf());

        return schema;
    }
}
