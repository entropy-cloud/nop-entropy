/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.batch.gen.generator;

import io.nop.api.core.util.CloneHelper;
import io.nop.batch.gen.IBatchGenContext;
import io.nop.batch.gen.model.IBatchTemplateBasedProducer;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.type.IGenericType;

import java.util.Map;

public class BatchTemplateBasedProducer implements IBatchTemplateBasedProducer {
    @Override
    public Object produce(Map<String, Object> template, IGenericType targetType, IBatchGenContext context) {
        if (targetType == null)
            return CloneHelper.deepCloneMap(template);

        return BeanTool.buildBean(template, targetType);
    }
}
