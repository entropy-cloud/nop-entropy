/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xmeta;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.type.IGenericType;
import io.nop.xlang.XLangErrors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.nop.xlang.XLangErrors.ARG_PROP_NAME;

public interface IObjSchema extends ISchemaNode {

    String getClassName();

    default String getPackageName() {
        return StringHelper.packageName(getClassName());
    }

    default String getPackagePath() {
        String pkgName = getPackageName();
        if (pkgName == null)
            return null;
        return pkgName.replace('.', '/');
    }

    default String getSimpleClassName() {
        return StringHelper.simpleClassName(getClassName());
    }

    /**
     * union类型根据此字段的值来确定子类型
     */
    String getTypeValue();

    void setTypeValue(String typeValue);

    boolean isAbstract();

    boolean isInterface();

    IGenericType getType();

    IGenericType getExtendsType();

    List<IGenericType> getImplementsTypes();

    /**
     * 最少有多少个属性
     */
    Integer getMinProperties();

    /**
     * 最多有多少个属性
     */
    Integer getMaxProperties();

    String getUniqueProp();

    default boolean isMapType() {
        IGenericType type = getType();
        if (type == null)
            return false;
        return type.getRawTypeName().equals(Map.class.getName());
    }

    List<? extends IObjPropMeta> getProps();

    IObjPropMeta getProp(String name);

    default IObjPropMeta requireProp(String name) {
        IObjPropMeta propMeta = getProp(name);
        if (propMeta == null)
            throw new NopException(XLangErrors.ERR_OBJ_SCHEMA_NO_PROP)
                    .source(this).param(ARG_PROP_NAME, name);
        return propMeta;
    }

    default IObjPropMeta getPropByTag(String tag) {
        for (IObjPropMeta prop : getProps()) {
            if (prop.containsTag(tag))
                return prop;
        }
        return null;
    }

    default List<IObjPropMeta> getAllPropsByTag(String tag) {
        List<IObjPropMeta> props = new ArrayList<>();
        for (IObjPropMeta prop : getProps()) {
            if (prop.containsTag(tag))
                props.add(prop);
        }
        return props;
    }

    default List<? extends IObjPropMeta> getLocalProps() {
        return getLocalProps(true);
    }

    default List<? extends IObjPropMeta> getLocalProps(boolean sorted) {
        List<? extends IObjPropMeta> props = getProps();
        if (props == null)
            return Collections.emptyList();
        Stream<? extends IObjPropMeta> stream = props.stream().filter(prop -> !isPropInherited(prop.getName()));
        if (sorted)
            stream = stream.sorted();
        return stream.collect(Collectors.toList());
    }

    boolean isPropInherited(String name);

    boolean hasProp(String name);

    boolean hasProps();

    Boolean getSupportExtends();

    ISchema getUnknownTagSchema();

    ISchema getUnknownAttrSchema();

}
