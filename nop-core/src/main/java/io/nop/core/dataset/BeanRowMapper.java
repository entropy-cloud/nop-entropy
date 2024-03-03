/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
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
public class BeanRowMapper implements IRowMapper<Object> {
    private final IBeanModel beanModel;
    private final boolean camelCase;

    public BeanRowMapper(IBeanModel beanModel, boolean camelCase) {
        this.beanModel = beanModel;
        this.camelCase = camelCase;
    }

    public static BeanRowMapper of(Class<?> beanClass, boolean camelCase) {
        return new BeanRowMapper(ReflectionManager.instance().getBeanModelForClass(beanClass), camelCase);
    }

    @Override
    public Object mapRow(IDataRow row, long rowNumber, IFieldMapper colMapper) {
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
        return bean;
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
