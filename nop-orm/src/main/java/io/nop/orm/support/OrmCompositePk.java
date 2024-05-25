/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.support;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.json.IJsonString;
import io.nop.api.core.util.Guard;
import io.nop.commons.util.StringHelper;
import io.nop.core.reflect.hook.IPropGetMissingHook;
import io.nop.orm.IOrmCompositePk;
import io.nop.orm.exceptions.OrmException;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

import static io.nop.orm.OrmErrors.ARG_COUNT;
import static io.nop.orm.OrmErrors.ARG_ENTITY_ID;
import static io.nop.orm.OrmErrors.ARG_ENTITY_NAME;
import static io.nop.orm.OrmErrors.ARG_PROP_NAME;
import static io.nop.orm.OrmErrors.ARG_PROP_NAMES;
import static io.nop.orm.OrmErrors.ARG_VALUE;
import static io.nop.orm.OrmErrors.ERR_ORM_ENTITY_PK_NO_PROP;
import static io.nop.orm.OrmErrors.ERR_ORM_INVALID_COMPOSITE_PK_PART;
import static io.nop.orm.OrmErrors.ERR_ORM_INVALID_COMPOSITE_PK_PART_COUNT;

/**
 * 所有的复合主键都使用这一对象。字段顺序按照propId排序
 *
 * @author canonical_entropy@163.com
 */
public final class OrmCompositePk implements IOrmCompositePk, Serializable, IJsonString, IPropGetMissingHook {
    private final List<String> propNames;
    private final Object[] propValues;

    public OrmCompositePk(List<String> propNames, Object[] propValues) {
        this.propNames = propNames;
        this.propValues = propValues;
    }

    @Override
    public List<String> propNames() {
        return propNames;
    }

    @Override
    public Object prop_get(String propName) {
        return get(propName);
    }

    @Override
    public boolean prop_has(String propName) {
        return propNames.contains(propName);
    }

    @Override
    public Object get(String propName) {
        for (int i = 0, n = propNames.size(); i < n; i++) {
            if (propNames.get(i).equals(propName))
                return propValues[i];
        }
        throw new OrmException(ERR_ORM_ENTITY_PK_NO_PROP).param(ARG_PROP_NAME, propName).param(ARG_PROP_NAMES,
                Arrays.asList(propNames));
    }

    public int size() {
        return propValues.length;
    }

    public Object get(int index) {
        return propValues[index];
    }

    public boolean containsNull() {
        for (int i = 0, n = propValues.length; i < n; i++) {
            if (propValues[i] == null)
                return true;
        }
        return false;
    }

    public Object[] toArray() {
        return propValues.clone();
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(propValues);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;

        if (!(o instanceof OrmCompositePk))
            return false;
        OrmCompositePk other = (OrmCompositePk) o;
        return Arrays.equals(propValues, other.propValues);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0, n = propValues.length; i < n; i++) {
            if (i != 0)
                sb.append(COMPOSITE_PK_SEPARATOR);
            sb.append(encode(propValues[i]));
        }
        return sb.toString();
    }

    static String encode(Object o) {
        if (o == null)
            return "null";
        String s = o.toString();
        s = StringHelper.encodeDupEscape(s, COMPOSITE_PK_SEPARATOR);
        return s;
    }

    /**
     * 存在属性为null时返回null。
     *
     * @param propNames  主键字段的属性名
     * @param propValues 主键字段的值
     * @return 如果返回结果不为null，则表示所有主键字段都不为null
     */
    public static OrmCompositePk buildNotNull(List<String> propNames, Object[] propValues) {
        for (int i = 0, n = propValues.length; i < n; i++) {
            if (propValues[i] == null)
                return null;
        }
        return new OrmCompositePk(propNames, propValues);
    }

    // public static Object[] parse(String entityName, String[] propNames, Class<?>[] propTypes, String str) {
    // if (str == null || str.length() <= 0)
    // return null;
    // List<String> parts = StringHelper.splitDupEscaped(str, COMPOSITE_PK_SEPARATOR);
    // if (parts.size() != propTypes.length)
    // throw new OrmException(ERR_ORM_INVALID_COMPOSITE_PK_PART_COUNT)
    // .param(ARG_ENTITY_NAME, entityName)
    // .param(ARG_ENTITY_ID, str).param(ARG_COUNT, propTypes.length);
    //
    // Object[] propValues = new Object[parts.size()];
    // for (int i = 0, n = parts.size(); i < n; i++) {
    // String part = parts.get(i);
    // if (part.equals("null")) {
    // propValues[i] = null;
    // } else {
    // Class<?> propType = propTypes[i];
    // String propName = propNames[i];
    // propValues[i] = ConvertHelper.convertTo(propType, part, err ->
    // new OrmException(ERR_ORM_INVALID_COMPOSITE_PK_PART).param(ARG_ENTITY_NAME, entityName)
    // .param(ARG_ENTITY_ID, str).param(ARG_PROP_NAME, propName).param(ARG_VALUE, part));
    // }
    // }
    // return propValues;
    // }

    public static OrmCompositePk parse(IEntityModel entityModel, String str) {
        if (str == null || str.length() <= 0)
            return null;
        List<String> parts = StringHelper.splitDupEscaped(str, COMPOSITE_PK_SEPARATOR);
        if (parts.size() != entityModel.getPkColumns().size())
            throw new OrmException(ERR_ORM_INVALID_COMPOSITE_PK_PART_COUNT)
                    .param(ARG_ENTITY_NAME, entityModel.getName()).param(ARG_ENTITY_ID, str)
                    .param(ARG_COUNT, entityModel.getPkColumns().size());

        Object[] propValues = new Object[parts.size()];
        for (int i = 0, n = parts.size(); i < n; i++) {
            String part = parts.get(i);
            if (part.equals("null")) {
                propValues[i] = null;
            } else {
                IColumnModel col = entityModel.getPkColumns().get(i);
                Class<?> propType = col.getStdDataType().getJavaClass();
                String propName = col.getName();
                propValues[i] = ConvertHelper.convertTo(propType, part,
                        err -> new OrmException(ERR_ORM_INVALID_COMPOSITE_PK_PART)
                                .param(ARG_ENTITY_NAME, entityModel.getName()).param(ARG_ENTITY_ID, str)
                                .param(ARG_PROP_NAME, propName).param(ARG_VALUE, part));
            }
        }
        return new OrmCompositePk(entityModel.getPkColumnNames(), propValues);
    }

    public static OrmCompositePk build(IEntityModel entityModel, Object[] propValues) {
        Guard.checkEquals(entityModel.getPkColumnNames().size(), propValues.length, "pk col count mismatch");

        List<? extends IColumnModel> cols = entityModel.getPkColumns();
        for (int i = 0, n = cols.size(); i < n; i++) {
            IColumnModel col = cols.get(i);
            Class<?> propType = col.getStdDataType().getJavaClass();
            String propName = col.getName();
            propValues[i] = ConvertHelper.convertTo(propType, propValues[i],
                    err -> new OrmException(ERR_ORM_INVALID_COMPOSITE_PK_PART)
                            .param(ARG_ENTITY_NAME, entityModel.getName())
                            .param(ARG_ENTITY_ID,
                                    StringHelper.joinArray(propValues, String.valueOf(COMPOSITE_PK_SEPARATOR)))
                            .param(ARG_PROP_NAME, propName));
        }
        return new OrmCompositePk(entityModel.getPkColumnNames(), propValues);
    }
}