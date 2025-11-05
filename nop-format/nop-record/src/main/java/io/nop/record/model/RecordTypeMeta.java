/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.record.model;

import io.nop.core.reflect.IClassModel;
import io.nop.core.reflect.ReflectionManager;
import io.nop.record.model._gen._RecordTypeMeta;

import java.util.LinkedHashMap;

public class RecordTypeMeta extends _RecordTypeMeta {
    private IClassModel beanClassModel;

    public RecordTypeMeta() {

    }

    public Object newRecordObject() {
        IClassModel classModel = getBeanClassModel();
        if (classModel != null)
            return classModel.newInstance();
        return new LinkedHashMap<>();
    }

    public IClassModel getBeanClassModel() {
        if (beanClassModel == null) {
            String beanClass = getBeanClass();
            if (beanClass != null)
                beanClassModel = ReflectionManager.instance().loadClassModel(beanClass);
        }
        return beanClassModel;
    }
}
