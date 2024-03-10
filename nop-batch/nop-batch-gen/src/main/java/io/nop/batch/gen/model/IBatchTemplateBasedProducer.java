/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.gen.model;

import io.nop.batch.gen.IBatchGenContext;
import io.nop.core.type.IGenericType;

import java.util.Map;

/**
 * 根据测试数据模板配置和当前上下文环境生成一个测试数据对象
 */
public interface IBatchTemplateBasedProducer {
    Object produce(Map<String, Object> template, IGenericType targetType, IBatchGenContext context);
}