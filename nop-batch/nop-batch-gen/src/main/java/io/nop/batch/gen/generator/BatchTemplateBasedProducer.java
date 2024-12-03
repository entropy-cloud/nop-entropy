/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.gen.generator;

import io.nop.api.core.util.CloneHelper;
import io.nop.batch.gen.IBatchGenContext;
import io.nop.batch.gen.model.IBatchTemplateBasedProducer;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.eval.EvalExprProvider;
import io.nop.core.lang.eval.IEvalExprParser;
import io.nop.core.lang.json.bind.JsonBindExprEvaluator;
import io.nop.core.lang.json.bind.ValueResolverCompilerRegistry;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.type.IGenericType;

import java.util.Map;

public class BatchTemplateBasedProducer implements IBatchTemplateBasedProducer {
    private final ValueResolverCompilerRegistry registry;
    private final IEvalExprParser exprParser;

    public BatchTemplateBasedProducer(ValueResolverCompilerRegistry registry) {
        this.registry = registry;
        this.exprParser = EvalExprProvider.getDefaultExprParser();
    }

    @Override
    public Object produce(Map<String, Object> template, IGenericType targetType, IEvalContext context) {
        Map<String,Object> resolved = (Map<String, Object>) JsonBindExprEvaluator.evalBindExpr(template,
                true, exprParser, registry, context.getEvalScope());

        if (targetType == null)
            return CloneHelper.deepCloneMap(resolved);

        return BeanTool.buildBean(resolved, targetType);
    }
}
