/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.dataset;

import io.nop.commons.util.StringHelper;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.reflect.bean.IBeanModel;
import io.nop.dataset.IDataRow;
import io.nop.dataset.IFieldMapper;
import io.nop.dataset.IRowMapper;

import java.util.Map;

/**
 * @author canonical_entropy@163.com
 */
public class BeanRowMapper<T> implements IRowMapper<T> {
    private final IBeanModel beanModel;
    private final boolean camelCase;

    public BeanRowMapper(IBeanModel beanModel, boolean camelCase) {
        this.beanModel = beanModel;
        this.camelCase = camelCase;
    }

    public static <T> BeanRowMapper<T> of(Class<T> beanClass, boolean camelCase) {
        return new BeanRowMapper<T>(ReflectionManager.instance().getBeanModelForClass(beanClass), camelCase);
    }

    public static <T> BeanRowMapper<T> of(Class<T> beanClass) {
        return of(beanClass, false);
    }

    @Override
    public T mapRow(IDataRow row, long rowNumber, IFieldMapper colMapper) {
        int columnCount = row.getFieldCount();
        Object bean = beanModel.newInstance();
        for (int i = 0; i < columnCount; i++) {
            String key = row.getMeta().getFieldName(i);
            if (camelCase) {
                key = StringHelper.camelCase(key, '_', false);
            }
            Object value = colMapper.getValue(row, i);
            if (key.indexOf('.') < 0) {
                beanModel.setProperty(bean, key, value);
            } else {
                BeanTool.setComplexProperty(bean, key, value);
            }
        }
        return (T) bean;
    }

    public static Object newBean(IBeanModel beanModel, Map<String, Object> data, boolean camelCase) {
        Object bean = beanModel.newInstance();
        data.forEach((key, value) -> {
            setBeanProp(beanModel, bean, key, value, camelCase);
        });
        return bean;
    }

    public static void setBeanProp(IBeanModel beanModel, Object bean, String key, Object value, boolean camelCase) {
        if (camelCase) {
            key = StringHelper.camelCase(key, '_', false);
        }
        if (key.indexOf('.') < 0) {
            beanModel.setProperty(bean, key, value);
        } else {
            BeanTool.setComplexProperty(bean, key, value);
        }
    }
}
